package io.github.sceneview.sample.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Live Camera background feed powered by Android Camera2 and OpenGL ES.
 * Renders stereoscopic dual camera in MR mode (Left Eye and Right Eye) and single camera in AR mode.
 */
@Composable
fun CameraFeedView(
    isMRMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("camera_feed_container")
    ) {
        if (hasCameraPermission) {
            CameraSurfacePreview(
                isMR = isMRMode,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Permission request overlay with futuristic spatial canvas
            SpatialCameraFallback(
                isMR = isMRMode,
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Lifecycle-safe Camera2 Preview View.
 * Supports single camera preview for AR mode and double camera preview (Left Eye & Right Eye) for MR mode.
 */
@Composable
fun CameraSurfacePreview(
    isMR: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val controller = remember(context) { CameraPreviewController(context) }
    var glViewRef by remember { mutableStateOf<StereoscopicCameraGLView?>(null) }

    DisposableEffect(controller) {
        onDispose {
            controller.stop()
            glViewRef?.release()
        }
    }

    LaunchedEffect(isMR, glViewRef) {
        glViewRef?.updateMode(isMR)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. OpenGL Hardware-Accelerated Dual Camera Surface View
        AndroidView(
            factory = { ctx ->
                StereoscopicCameraGLView(
                    context = ctx,
                    initialIsMR = isMR,
                    onSurfaceReady = { surface, surfaceTexture ->
                        controller.start(surface, surfaceTexture)
                    }
                ).also { glViewRef = it }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Overlays & Target Semantics
        if (isMR) {
            // Dual Camera Viewport containers for testing and spatial alignment
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Eye Camera Container
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("camera_preview_left")
                )

                // Center Divider
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF00E5FF).copy(alpha = 0.4f))
                )

                // Right Eye Camera Container
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("camera_preview_right")
                )
            }

            // Ocular framing overlay
            MRStereoscopicCameraOverlay(
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("camera_preview_single")
            )
        }
    }
}

/**
 * Custom GLSurfaceView for stereoscopic MR and AR camera rendering.
 * Provides a single SurfaceTexture to Camera2, eliminating hardware driver conflicts
 * and rendering the live camera feed simultaneously to both the Left and Right eye viewports.
 */
class StereoscopicCameraGLView(
    context: Context,
    initialIsMR: Boolean,
    onSurfaceReady: (Surface, SurfaceTexture) -> Unit
) : GLSurfaceView(context) {

    private val renderer = StereoscopicCameraRenderer(this, initialIsMR, onSurfaceReady)

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun updateMode(isMR: Boolean) {
        if (renderer.isMR != isMR) {
            renderer.isMR = isMR
            requestRender()
        }
    }

    fun release() {
        renderer.release()
    }
}

class StereoscopicCameraRenderer(
    private val glSurfaceView: GLSurfaceView,
    @Volatile var isMR: Boolean,
    private val onSurfaceReady: (Surface, SurfaceTexture) -> Unit
) : GLSurfaceView.Renderer {

    private var textureId: Int = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var cameraSurface: Surface? = null
    private var program: Int = 0

    private var uTexMatrixHandle: Int = -1
    private var aPositionHandle: Int = -1
    private var aTexCoordHandle: Int = -1
    private var sTextureHandle: Int = -1

    private val texMatrix = FloatArray(16)
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(QUAD_VERTICES.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(QUAD_VERTICES)
            position(0)
        }

    private val texCoordBuffer: FloatBuffer = ByteBuffer.allocateDirect(QUAD_TEX_COORDS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(QUAD_TEX_COORDS)
            position(0)
        }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // 1. Generate OES external texture for Camera2
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // 2. Create SurfaceTexture with explicit high-resolution buffer size
        val st = SurfaceTexture(textureId)
        st.setDefaultBufferSize(1920, 1080)
        st.setOnFrameAvailableListener {
            glSurfaceView.requestRender()
        }
        surfaceTexture = st
        val surf = Surface(st)
        cameraSurface = surf
        onSurfaceReady(surf, st)

        // 3. Compile and build GL program
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (program != 0) {
            uTexMatrixHandle = GLES20.glGetUniformLocation(program, "uTexMatrix")
            sTextureHandle = GLES20.glGetUniformLocation(program, "sTexture")
            aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            aTexCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
    }

    override fun onDrawFrame(gl: GL10?) {
        val st = surfaceTexture ?: return
        try {
            st.updateTexImage()
        } catch (e: Exception) {
            return
        }
        st.getTransformMatrix(texMatrix)

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (program == 0 || viewportWidth <= 0 || viewportHeight <= 0) return

        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(sTextureHandle, 0)
        GLES20.glUniformMatrix4fv(uTexMatrixHandle, 1, false, texMatrix, 0)

        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glVertexAttribPointer(aPositionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(aTexCoordHandle)
        GLES20.glVertexAttribPointer(aTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        if (isMR) {
            // Stereoscopic Dual Camera Pass-Through for MR Mode:
            // 1. Left Eye Camera (left half viewport: 0 to width/2)
            GLES20.glViewport(0, 0, viewportWidth / 2, viewportHeight)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            // 2. Right Eye Camera (right half viewport: width/2 to width)
            GLES20.glViewport(viewportWidth / 2, 0, viewportWidth / 2, viewportHeight)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } else {
            // Single Full-Screen Camera for AR Mode
            GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aTexCoordHandle)
    }

    fun release() {
        cameraSurface?.release()
        cameraSurface = null
        surfaceTexture?.release()
        surfaceTexture = null
        if (textureId != 0) {
            val textures = intArrayOf(textureId)
            GLES20.glDeleteTextures(1, textures, 0)
            textureId = 0
        }
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
    }

    companion object {
        private val QUAD_VERTICES = floatArrayOf(
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f
        )

        private val QUAD_TEX_COORDS = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """

        private fun loadShader(type: Int, shaderCode: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                Log.e("DualCameraGL", "Shader compile error: $log")
                return 0
            }
            return shader
        }

        private fun buildProgram(vertexCode: String, fragmentCode: String): Int {
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexCode)
            if (vertexShader == 0) return 0
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode)
            if (fragmentShader == 0) return 0

            val prog = GLES20.glCreateProgram()
            GLES20.glAttachShader(prog, vertexShader)
            GLES20.glAttachShader(prog, fragmentShader)
            GLES20.glLinkProgram(prog)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val log = GLES20.glGetProgramInfoLog(prog)
                GLES20.glDeleteProgram(prog)
                Log.e("DualCameraGL", "Program link error: $log")
                return 0
            }
            return prog
        }
    }
}

/**
 * Controller that safely coordinates Camera2 opening, single-surface capture session, and synchronous teardown.
 */
class CameraPreviewController(private val context: Context) {
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var currentSurface: Surface? = null
    private var currentSurfaceTexture: SurfaceTexture? = null
    @Volatile
    private var isStopped = false

    @SuppressLint("MissingPermission")
    fun start(surface: Surface, surfaceTexture: SurfaceTexture? = null) {
        if (!surface.isValid) return
        currentSurface = surface
        currentSurfaceTexture = surfaceTexture
        initCamera()
    }

    @SuppressLint("MissingPermission")
    private fun initCamera() {
        cleanUpCamera()
        isStopped = false

        val thread = HandlerThread("CamPreviewThread_${System.currentTimeMillis()}").apply { start() }
        handlerThread = thread
        val bgHandler = Handler(thread.looper)
        handler = bgHandler

        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            val cameraIdList = cameraManager.cameraIdList
            if (cameraIdList.isEmpty()) return

            val cameraId = cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraIdList.first()

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val outputSizes = map?.getOutputSizes(SurfaceTexture::class.java)
            val optimalSize = outputSizes?.filter { it.width <= 1920 && it.height <= 1080 }
                ?.maxByOrNull { it.width.toLong() * it.height.toLong() }
                ?: outputSizes?.maxByOrNull { it.width.toLong() * it.height.toLong() }

            optimalSize?.let { opt ->
                currentSurfaceTexture?.setDefaultBufferSize(opt.width, opt.height)
            }

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (isStopped) {
                        camera.close()
                        return
                    }
                    cameraDevice = camera
                    startCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (cameraDevice == camera) cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    if (cameraDevice == camera) cameraDevice = null
                }
            }, bgHandler)
        } catch (e: Exception) {
            Log.e("CameraPreview", "Camera setup error", e)
        }
    }

    private fun startCaptureSession() {
        val camera = cameraDevice ?: return
        val bgHandler = handler ?: return
        val surface = currentSurface ?: return
        if (isStopped || !surface.isValid) return

        try {
            captureSession?.stopRepeating()
            captureSession?.close()
            captureSession = null

            val previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(surface)
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_MODE,
                CaptureRequest.CONTROL_MODE_AUTO
            )
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON
            )
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AWB_MODE,
                CaptureRequest.CONTROL_AWB_MODE_AUTO
            )
            previewRequestBuilder.set(
                CaptureRequest.NOISE_REDUCTION_MODE,
                CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
            )
            previewRequestBuilder.set(
                CaptureRequest.EDGE_MODE,
                CaptureRequest.EDGE_MODE_HIGH_QUALITY
            )

            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (isStopped) {
                            session.close()
                            return
                        }
                        captureSession = session
                        try {
                            session.setRepeatingRequest(previewRequestBuilder.build(), null, bgHandler)
                        } catch (e: Exception) {
                            Log.w("CameraPreview", "Capture request stopped or error", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        session.close()
                        if (captureSession == session) captureSession = null
                    }

                    override fun onClosed(session: CameraCaptureSession) {
                        if (captureSession == session) captureSession = null
                    }
                },
                bgHandler
            )
        } catch (e: Exception) {
            Log.e("CameraPreview", "Failed to start capture session", e)
        }
    }

    private fun cleanUpCamera() {
        try {
            captureSession?.stopRepeating()
            captureSession?.abortCaptures()
            captureSession?.close()
        } catch (e: Exception) {}
        captureSession = null

        try {
            cameraDevice?.close()
        } catch (e: Exception) {}
        cameraDevice = null

        try {
            handlerThread?.quitSafely()
        } catch (e: Exception) {}
        handlerThread = null
        handler = null
    }

    fun stop() {
        isStopped = true
        cleanUpCamera()
        currentSurface = null
    }
}

/**
 * Stereoscopic overlay framing for Mixed Reality mode.
 * Shows center divider line and ocular lens alignments for MR headset passthrough.
 */
@Composable
fun MRStereoscopicCameraOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        // Vertical stereoscopic divider line for left and right eyes
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .align(Alignment.Center)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF00E5FF).copy(alpha = 0.6f),
                            Color(0xFF00E5FF),
                            Color(0xFF00E5FF).copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Alignment tick marks on center divider
        Canvas(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
                .align(Alignment.Center)
        ) {
            val midY = size.height / 2f
            for (i in -4..4) {
                val y = midY + i * 24.dp.toPx()
                val tickWidth = if (i == 0) 18.dp.toPx() else 10.dp.toPx()
                drawLine(
                    color = Color(0xFF00E5FF).copy(alpha = if (i == 0) 0.8f else 0.4f),
                    start = Offset((size.width - tickWidth) / 2f, y),
                    end = Offset((size.width + tickWidth) / 2f, y),
                    strokeWidth = 1.5f
                )
            }
        }
    }
}

/**
 * Visual spatial fallback if camera permission is not yet granted.
 */
@Composable
fun SpatialCameraFallback(
    isMR: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0F14),
                        Color(0xFF141720),
                        Color(0xFF090A0E)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Grid pattern
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 48.dp.toPx()
            for (x in 0..(size.width / step).toInt()) {
                drawLine(
                    color = Color(0xFF00E5FF).copy(alpha = 0.08f),
                    start = Offset(x * step, 0f),
                    end = Offset(x * step, size.height),
                    strokeWidth = 1f
                )
            }
            for (y in 0..(size.height / step).toInt()) {
                drawLine(
                    color = Color(0xFF00E5FF).copy(alpha = 0.08f),
                    start = Offset(0f, y * step),
                    end = Offset(size.width, y * step),
                    strokeWidth = 1f
                )
            }
        }

        // Center line if MR mode
        if (isMR) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .align(Alignment.Center)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.3f))
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.15f))
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Camera",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isMR) "MR Stereoscopic Camera Feed" else "AR Live Camera Feed",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isMR)
                    "Camera access is required for dual-eye stereoscopic Mixed Reality view."
                else
                    "Camera access is required to view 3D models in your physical environment.",
                color = Color(0xFF8F939D),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = "Enable Camera",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
