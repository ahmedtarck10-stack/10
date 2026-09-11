package io.github.sceneview.sample.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.filament.Engine
import com.google.android.filament.gltfio.FilamentInstance
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.SphereNode
import io.github.sceneview.node.TorusNode
import io.github.sceneview.rememberCameraManipulator
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
 * - AR Mode: Single live camera stream + 3D model + surface reticle + tap-to-place.
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
        // 1. Live Camera Feed (Single for AR, Dual for MR)
        CameraFeedView(
            isMRMode = isMR,
            modifier = Modifier.fillMaxSize()
        )

        // 2. 3D Model Layer over Camera
        if (isMR) {
            // MR Mode: Double Camera & Double Model (Stereoscopic SBS View)
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
            // AR Mode: Single Full-screen AR View
            SingleARScene(
                model = activeModel,
                placedAnchors = placedAnchors,
                onAddAnchor = onAddAnchor,
                engine = engine,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Single Full-screen AR Scene View.
 */
@Composable
fun SingleARScene(
    model: SpatialModel?,
    placedAnchors: List<PlacedAnchor>,
    onAddAnchor: (PlacedAnchor) -> Unit,
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    modifier: Modifier = Modifier
) {
    val cameraManipulator = rememberCameraManipulator()

    val customInstance = remember(model?.customFilePath, modelLoader) {
        val path = model?.customFilePath
        if (path != null) {
            val file = File(path)
            if (file.exists()) {
                try {
                    modelLoader.createModelInstance(file)
                } catch (e: Exception) {
                    null
                }
            } else null
        } else null
    }

    Box(modifier = modifier.fillMaxSize()) {
        SceneView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(model) {
                    detectTapGestures { offset ->
                        if (model != null) {
                            val newAnchor = PlacedAnchor(
                                position = Float3(
                                    (offset.x / size.width - 0.5f) * 0.8f,
                                    -(offset.y / size.height - 0.5f) * 0.8f,
                                    -0.6f
                                ),
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
                if (placedAnchors.isEmpty()) {
                    RenderModelItem(
                        model = model,
                        customInstance = customInstance,
                        materialLoader = materialLoader,
                        offsetPosition = Float3(0f, 0f, 0f)
                    )
                } else {
                    placedAnchors.forEach { anchor ->
                        RenderModelItem(
                            model = anchor.model,
                            customInstance = null,
                            materialLoader = materialLoader,
                            offsetPosition = anchor.position
                        )
                    }
                }
            }
        }

        // Center Reticle
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

    val customInstance = remember(model?.customFilePath, modelLoader) {
        val path = model?.customFilePath
        if (path != null) {
            val file = File(path)
            if (file.exists()) {
                try {
                    modelLoader.createModelInstance(file)
                } catch (e: Exception) {
                    null
                }
            } else null
        } else null
    }

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
                        customInstance = customInstance,
                        materialLoader = materialLoader,
                        offsetPosition = Float3(leftOffset, 0f, 0f)
                    )
                    // Right Eye Model (with stereoscopic parallax)
                    RenderModelItem(
                        model = model,
                        customInstance = null,
                        materialLoader = materialLoader,
                        offsetPosition = Float3(rightOffset, 0f, 0f)
                    )
                } else {
                    placedAnchors.forEach { anchor ->
                        // Left Eye anchor
                        RenderModelItem(
                            model = anchor.model,
                            customInstance = null,
                            materialLoader = materialLoader,
                            offsetPosition = Float3(anchor.position.x + leftOffset, anchor.position.y, anchor.position.z)
                        )
                        // Right Eye anchor
                        RenderModelItem(
                            model = anchor.model,
                            customInstance = null,
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
