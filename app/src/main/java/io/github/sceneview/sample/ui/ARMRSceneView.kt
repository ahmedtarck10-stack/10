package io.github.sceneview.sample.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.filament.Engine
import com.google.android.filament.gltfio.FilamentInstance
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import dev.romainguy.kotlin.math.Float3
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.SphereNode
import io.github.sceneview.node.TorusNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.sample.model.AppMode
import io.github.sceneview.sample.model.ModelPresetType
import io.github.sceneview.sample.model.PlacedAnchor
import io.github.sceneview.sample.model.SpatialModel
import java.io.File

/**
 * AR & Mixed Reality Viewport.
 * - AR Mode: Native ARCore session with plane detection, real 6-DOF tracking, hit testing, and AnchorNode.
 * - MR Mode: Stereoscopic Double Camera + Double 3D Model (Side-by-Side Left & Right Eye)
 *   with center dividing line for MR glasses / headsets.
 * - Cleared State: When model is cleared, no 3D model is rendered.
 */
@Composable
fun ARMRSceneView(
    mode: AppMode,
    activeModel: SpatialModel?,
    placedAnchors: List<PlacedAnchor>,
    onAddAnchor: (PlacedAnchor) -> Unit,
    onClearAnchors: () -> Unit,
    engine: Engine = rememberEngine(),
    modelLoader: ModelLoader = rememberModelLoader(engine),
    materialLoader: MaterialLoader = rememberMaterialLoader(engine),
    modifier: Modifier = Modifier
) {
    val isMR = (mode == AppMode.MR)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("armr_viewport")
    ) {
        if (isMR) {
            // MR Mode: Stereoscopic Double Camera (Left Eye & Right Eye) + Double 3D Model Layer
            CameraFeedView(
                isMRMode = true,
                modifier = Modifier.fillMaxSize()
            )
            StereoscopicMRScene(
                model = activeModel,
                placedAnchors = placedAnchors,
                onAddAnchor = onAddAnchor,
                engine = engine,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // AR Mode: Native ARCore Pipeline with Plane Detection and Physical Anchoring
            SingleARScene(
                model = activeModel,
                placedAnchors = placedAnchors,
                onAddAnchor = onAddAnchor,
                onClearAnchors = onClearAnchors,
                engine = engine,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Native Google ARCore AR Scene View.
 * Real AR pipeline:
 * 1. ARCore Session lifecycle & camera stream.
 * 2. 6-DOF device tracking synchronized with Filament scene camera.
 * 3. Horizontal and vertical surface detection with visual plane grid renderer.
 * 4. Screen tap to ARCore frame hit test against physical detected planes.
 * 5. Creation of authentic ARCore Anchor and binding 3D model to AnchorNode.
 * 6. Model remains completely pinned in physical world space during user movement.
 */
@Composable
fun SingleARScene(
    model: SpatialModel?,
    placedAnchors: List<PlacedAnchor>,
    onAddAnchor: (PlacedAnchor) -> Unit,
    onClearAnchors: () -> Unit,
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Camera permission check and request
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
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

    // ARCore Tracking & Plane State
    var isTracking by remember { mutableStateOf(false) }
    var trackingFailureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var detectedPlanesCount by remember { mutableIntStateOf(0) }
    var activeAnchor by remember { mutableStateOf<Anchor?>(null) }
    var pendingTapOffset by remember { mutableStateOf<Offset?>(null) }

    // Touch gesture tracking: ensure ACTION_DOWN returns true so ACTION_UP is received
    var downTime by remember { mutableLongStateOf(0L) }
    var downX by remember { mutableFloatStateOf(0f) }
    var downY by remember { mutableFloatStateOf(0f) }

    // Load custom model instance if a custom file is provided
    val customInstance = remember(model?.customFilePath, modelLoader) {
        val path = model?.customFilePath
        if (path != null) {
            val file = File(path)
            if (file.exists()) {
                try {
                    modelLoader.createModelInstance(file)
                } catch (e: Exception) {
                    Log.e("SingleARScene", "Failed to load custom model", e)
                    null
                }
            } else null
        } else null
    }

    // Clean up ARCore anchor when model is cleared or composable is disposed
    LaunchedEffect(model) {
        if (model == null) {
            activeAnchor?.detach()
            activeAnchor = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeAnchor?.detach()
            activeAnchor = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            planeRenderer = true,
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL,
            sessionConfiguration = { session, config ->
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                config.focusMode = Config.FocusMode.AUTO
            },
            onSessionCreated = { session ->
                Log.d("SingleARScene", "ARCore Session created successfully")
            },
            onSessionResumed = { session ->
                Log.d("SingleARScene", "ARCore Session resumed")
            },
            onSessionFailed = { exception ->
                Log.e("SingleARScene", "ARCore Session exception: ${exception.message}", exception)
            },
            onTrackingFailureChanged = { reason ->
                trackingFailureReason = reason
            },
            onSessionUpdated = { session, frame ->
                val camera = frame.camera
                isTracking = (camera.trackingState == TrackingState.TRACKING)

                // Track active planes in the environment
                val allPlanes = session.getAllTrackables(Plane::class.java)
                val trackedPlanes = allPlanes.filter { it.trackingState == TrackingState.TRACKING }
                detectedPlanesCount = trackedPlanes.size

                // Process pending tap: perform ARCore hit test against active tracked planes
                val tap = pendingTapOffset
                if (tap != null) {
                    pendingTapOffset = null
                    if (isTracking && model != null) {
                        val hitResults = frame.hitTest(tap.x, tap.y)
                        // Prioritize hits inside the polygon boundary of horizontal upward planes (floors/tables)
                        val planeHit = hitResults.firstOrNull { hit ->
                            val trackable = hit.trackable
                            trackable is Plane &&
                            trackable.trackingState == TrackingState.TRACKING &&
                            trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                            trackable.isPoseInPolygon(hit.hitPose)
                        } ?: hitResults.firstOrNull { hit ->
                            val trackable = hit.trackable
                            trackable is Plane &&
                            trackable.trackingState == TrackingState.TRACKING &&
                            trackable.isPoseInPolygon(hit.hitPose)
                        } ?: hitResults.firstOrNull { hit ->
                            val trackable = hit.trackable
                            trackable is Plane && trackable.trackingState == TrackingState.TRACKING
                        }

                        if (planeHit != null) {
                            // Create genuine ARCore Anchor from the physical hit result
                            activeAnchor?.detach()
                            val newAnchor = planeHit.createAnchor()
                            activeAnchor = newAnchor
                            Log.d("SingleARScene", "ARCore Anchor established at pose: ${planeHit.hitPose}")
                        }
                    }
                }
            },
            onTouchEvent = { motionEvent, _ ->
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downTime = System.currentTimeMillis()
                        downX = motionEvent.x
                        downY = motionEvent.y
                        true // Consume ACTION_DOWN to ensure ACTION_UP is dispatched
                    }
                    MotionEvent.ACTION_UP -> {
                        val elapsed = System.currentTimeMillis() - downTime
                        val dx = motionEvent.x - downX
                        val dy = motionEvent.y - downY
                        val distanceSq = dx * dx + dy * dy
                        // Verify this was a tap (under 500ms and minimal movement)
                        if (elapsed < 500 && distanceSq < 10000f) {
                            pendingTapOffset = Offset(motionEvent.x, motionEvent.y)
                        }
                        true
                    }
                    else -> false
                }
            }
        ) {
            // Anchor 3D model to the physical world pose
            val currentAnchor = activeAnchor
            if (currentAnchor != null && model != null) {
                AnchorNode(anchor = currentAnchor) {
                    if (customInstance != null) {
                        ModelNode(
                            modelInstance = customInstance,
                            scaleToUnits = 0.8f,
                            isEditable = true
                        )
                    } else {
                        RenderModelItem(
                            model = model,
                            customInstance = null,
                            materialLoader = materialLoader,
                            offsetPosition = Float3(0f, 0f, 0f)
                        )
                    }
                }
            }
        }

        // Live AR Pipeline Status HUD
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF16171B).copy(alpha = 0.88f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (activeAnchor != null) Color(0xFF00E676).copy(alpha = 0.9f)
                else if (detectedPlanesCount > 0) Color(0xFF00E5FF).copy(alpha = 0.8f)
                else Color(0xFFFFD600).copy(alpha = 0.6f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (activeAnchor != null) Color(0xFF00E676)
                            else if (detectedPlanesCount > 0) Color(0xFF00E5FF)
                            else Color(0xFFFFD600)
                        )
                )
                Text(
                    text = when {
                        !isTracking && trackingFailureReason == TrackingFailureReason.INSUFFICIENT_LIGHT ->
                            "Too dark • Move to a brighter area"
                        !isTracking && trackingFailureReason == TrackingFailureReason.EXCESSIVE_MOTION ->
                            "Moving too fast • Slow down"
                        !isTracking && trackingFailureReason == TrackingFailureReason.INSUFFICIENT_FEATURES ->
                            "Aim camera at a textured surface"
                        !isTracking ->
                            "Initializing AR tracking..."
                        activeAnchor != null ->
                            "✓ Model Anchored • Tap surface to relocate"
                        detectedPlanesCount > 0 ->
                            "✓ Surface Detected ($detectedPlanesCount) • Tap floor to place"
                        else ->
                            "Scanning surfaces... Move phone slowly"
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Spatial Center Reticle
        SpatialReticle(
            isMR = false,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/**
 * Stereoscopic Mixed Reality Scene (Double Camera, Double Model - SBS).
 * Renders both Left Eye and Right Eye stereoscopic models inside a unified single SceneView
 * to prevent duplicate Filament engine conflicts and EGL attribute errors.
 *
 * CRITICAL FIX: Both Left Eye and Right Eye receive genuine FilamentInstance of the loaded model.
 * The right eye never falls back to a primitive Cube.
 */
@Composable
fun StereoscopicMRScene(
    model: SpatialModel?,
    placedAnchors: List<PlacedAnchor>,
    onAddAnchor: (PlacedAnchor) -> Unit,
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    modifier: Modifier = Modifier
) {
    val cameraManipulator = rememberCameraManipulator()

    val modelInstances = remember(model?.customFilePath, modelLoader) {
        val path = model?.customFilePath
        if (path != null) {
            val file = File(path)
            if (file.exists()) {
                try {
                    val instanced = modelLoader.createInstancedModel(file, 2)
                    if (instanced.size >= 2) {
                        instanced
                    } else {
                        val inst1 = modelLoader.createModelInstance(file)
                        val inst2 = try { modelLoader.createModelInstance(file) } catch (e: Exception) { null }
                        listOfNotNull(inst1, inst2)
                    }
                } catch (e: Exception) {
                    try {
                        val inst1 = modelLoader.createModelInstance(file)
                        val inst2 = try { modelLoader.createModelInstance(file) } catch (e2: Exception) { null }
                        listOfNotNull(inst1, inst2)
                    } catch (e2: Exception) {
                        emptyList()
                    }
                }
            } else emptyList()
        } else emptyList()
    }

    val leftInstance = modelInstances.getOrNull(0)
    val rightInstance = modelInstances.getOrNull(1) ?: leftInstance

    Box(modifier = modifier.fillMaxSize()) {
        // Single unified SceneView rendering both Left Eye and Right Eye stereoscopic models
        SceneView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(model) {
                    detectTapGestures { offset ->
                        if (model != null) {
                            val isLeftHalf = offset.x < size.width / 2f
                            val normalizedX = if (isLeftHalf) {
                                ((offset.x / (size.width / 2f)) - 0.5f) * 0.4f
                            } else {
                                (((offset.x - size.width / 2f) / (size.width / 2f)) - 0.5f) * 0.4f
                            }
                            val normalizedY = -(offset.y / size.height - 0.5f) * 0.5f
                            val newAnchor = PlacedAnchor(
                                position = Float3(normalizedX, normalizedY, -0.6f),
                                model = model
                            )
                            onAddAnchor(newAnchor)
                        }
                    }
                },
            surfaceType = SurfaceType.Surface,
            isOpaque = false,
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            cameraManipulator = cameraManipulator
        ) {
            if (model != null) {
                val leftOffset = -0.32f
                val rightOffset = 0.32f

                if (placedAnchors.isEmpty()) {
                    // Left Eye Model
                    RenderModelItem(
                        model = model,
                        customInstance = leftInstance,
                        materialLoader = materialLoader,
                        offsetPosition = Float3(leftOffset, 0f, 0f)
                    )
                    // Right Eye Model (True duplicate model with stereoscopic parallax, NOT a primitive cube!)
                    RenderModelItem(
                        model = model,
                        customInstance = rightInstance,
                        materialLoader = materialLoader,
                        offsetPosition = Float3(rightOffset, 0f, 0f)
                    )
                } else {
                    placedAnchors.forEach { anchor ->
                        // Left Eye anchor
                        RenderModelItem(
                            model = anchor.model,
                            customInstance = leftInstance,
                            materialLoader = materialLoader,
                            offsetPosition = Float3(anchor.position.x + leftOffset, anchor.position.y, anchor.position.z)
                        )
                        // Right Eye anchor
                        RenderModelItem(
                            model = anchor.model,
                            customInstance = rightInstance,
                            materialLoader = materialLoader,
                            offsetPosition = Float3(anchor.position.x + rightOffset, anchor.position.y, anchor.position.z)
                        )
                    }
                }
            }
        }

        // Stereoscopic HUD Overlays: Left Eye & Right Eye Reticles and labels
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Eye Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("mr_left_eye")
            ) {
                SpatialReticle(
                    isMR = true,
                    modifier = Modifier.align(Alignment.Center)
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 90.dp),
                    shape = CircleShape,
                    color = Color(0xFF16171B).copy(alpha = 0.75f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "EYE L",
                        color = Color(0xFF00E5FF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Center Stereoscopic Divider Line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF00E5FF).copy(alpha = 0.4f))
            )

            // Right Eye Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("mr_right_eye")
            ) {
                SpatialReticle(
                    isMR = true,
                    modifier = Modifier.align(Alignment.Center)
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 90.dp),
                    shape = CircleShape,
                    color = Color(0xFF16171B).copy(alpha = 0.75f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "EYE R",
                        color = Color(0xFF00E5FF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * Renders an active 3D model item inside a SceneScope.
 */
@Composable
private fun io.github.sceneview.SceneScope.RenderModelItem(
    model: SpatialModel,
    customInstance: FilamentInstance?,
    materialLoader: MaterialLoader,
    offsetPosition: Float3
) {
    if (customInstance != null) {
        ModelNode(
            modelInstance = customInstance,
            scaleToUnits = 0.6f,
            centerOrigin = offsetPosition
        )
    } else {
        val modelMaterial = remember(materialLoader, model.primaryColor, model.metallic, model.roughness) {
            materialLoader.createColorInstance(
                color = model.primaryColor,
                metallic = model.metallic,
                roughness = model.roughness,
                reflectance = 0.5f
            )
        }

        val accentMaterial = remember(materialLoader) {
            materialLoader.createColorInstance(
                color = Color(0xFF1E242B),
                metallic = 0.9f,
                roughness = 0.1f,
                reflectance = 0.8f
            )
        }

        when (model.type) {
            ModelPresetType.MR_HEADSET -> {
                CubeNode(
                    size = Float3(0.55f, 0.22f, 0.15f),
                    center = offsetPosition,
                    materialInstance = modelMaterial
                )
                CylinderNode(
                    radius = 0.035f,
                    height = 0.14f,
                    center = Float3(offsetPosition.x, offsetPosition.y + 0.1f, offsetPosition.z + 0.06f),
                    materialInstance = accentMaterial
                )
            }
            ModelPresetType.CYBER_DRONE -> {
                SphereNode(
                    radius = 0.22f,
                    center = offsetPosition,
                    materialInstance = modelMaterial
                )
                TorusNode(
                    majorRadius = 0.36f,
                    minorRadius = 0.02f,
                    center = offsetPosition,
                    materialInstance = accentMaterial
                )
            }
            ModelPresetType.QUANTUM_GYRO -> {
                TorusNode(
                    majorRadius = 0.35f,
                    minorRadius = 0.03f,
                    center = offsetPosition,
                    materialInstance = modelMaterial
                )
            }
            ModelPresetType.PRISM_CRYSTAL -> {
                CylinderNode(
                    radius = 0.18f,
                    height = 0.6f,
                    center = offsetPosition,
                    materialInstance = modelMaterial
                )
            }
            else -> {
                CubeNode(
                    size = Float3(0.4f, 0.4f, 0.4f),
                    center = offsetPosition,
                    materialInstance = modelMaterial
                )
            }
        }
    }
}

/**
 * Center targeting reticle for AR & MR.
 */
@Composable
fun SpatialReticle(
    isMR: Boolean,
    modifier: Modifier = Modifier
) {
    val reticleColor = if (isMR) Color(0xFF00E5FF) else Color.White
    Box(
        modifier = modifier
            .size(44.dp)
            .border(
                width = 1.5.dp,
                color = reticleColor.copy(alpha = 0.7f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(reticleColor)
        )
    }
}

/**
 * MR Telemetry HUD.
 */
@Composable
fun MRTelemetryHUD(
    anchorCount: Int,
    isModelLoaded: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag("mr_telemetry_hud"),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF101216).copy(alpha = 0.88f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E5FF))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "MR STEREOSCOPIC DUAL",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = if (isModelLoaded) "Dual Camera • Dual 3D Active" else "Scene Cleared • Tap Open to load",
                color = if (isModelLoaded) Color(0xFF00E5FF) else Color(0xFF8F939D),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * AR Surface Detection Prompt.
 */
@Composable
fun ARSurfacePrompt(
    anchorCount: Int,
    isModelLoaded: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag("ar_surface_prompt"),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF101216).copy(alpha = 0.88f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF434752))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isModelLoaded) Color(0xFF00E5FF) else Color(0xFF8F939D))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = "AR LIVE CAMERA",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (isModelLoaded) "Tap anywhere to place in camera view" else "Scene Cleared • Tap Open to load model",
                    color = Color(0xFF8F939D),
                    fontSize = 10.sp
                )
            }
        }
    }
}
