package io.github.sceneview.sample.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.sample.model.ModelPresetType
import io.github.sceneview.sample.model.SpatialModel
import kotlinx.coroutines.delay
import java.io.File

/**
 * Fullscreen dark Object Mode 3D viewer.
 * Supports rendering 3D models with PBR lighting or completely cleared empty state.
 */
@Composable
fun ObjectModeView(
    model: SpatialModel?,
    engine: Engine = rememberEngine(),
    modelLoader: ModelLoader = rememberModelLoader(engine),
    materialLoader: MaterialLoader = rememberMaterialLoader(engine),
    modifier: Modifier = Modifier
) {
    val cameraManipulator = rememberCameraManipulator()

    var autoRotate by remember { mutableStateOf(false) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(autoRotate) {
        while (autoRotate) {
            rotationAngle = (rotationAngle + 1.2f) % 360f
            delay(16)
        }
    }

    // PBR Material for the active model
    val mainMaterial = remember(materialLoader, model?.primaryColor, model?.metallic, model?.roughness) {
        materialLoader.createColorInstance(
            color = model?.primaryColor ?: Color(0xFF00E5FF),
            metallic = model?.metallic ?: 0.7f,
            roughness = model?.roughness ?: 0.3f,
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

    val glowMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            color = Color(0xFF00E5FF),
            metallic = 0.1f,
            roughness = 0.05f,
            reflectance = 0.9f
        )
    }

    // Custom model instance if loaded from file
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("object_mode_viewport")
    ) {
        // Native SceneView 3D viewport
        SceneView(
            modifier = Modifier.fillMaxSize(),
            surfaceType = SurfaceType.Surface,
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            cameraManipulator = cameraManipulator
        ) {
            if (model != null) {
                if (customInstance != null) {
                    ModelNode(
                        modelInstance = customInstance,
                        scaleToUnits = 0.8f,
                        centerOrigin = Float3(0f, 0f, 0f),
                        rotation = Float3(0f, rotationAngle, 0f)
                    )
                } else {
                    when (model.type) {
                        ModelPresetType.MR_HEADSET -> {
                            CubeNode(
                                size = Float3(0.68f, 0.28f, 0.18f),
                                center = Float3(0f, 0f, 0f),
                                materialInstance = mainMaterial,
                                rotation = Float3(0f, rotationAngle, 0f)
                            )
                            CylinderNode(
                                radius = 0.04f,
                                height = 0.16f,
                                center = Float3(0f, 0.12f, 0.08f),
                                materialInstance = accentMaterial,
                                rotation = Float3(0f, rotationAngle, 90f)
                            )
                            TorusNode(
                                majorRadius = 0.32f,
                                minorRadius = 0.035f,
                                center = Float3(0f, -0.02f, -0.16f),
                                materialInstance = accentMaterial,
                                rotation = Float3(15f, rotationAngle, 0f)
                            )
                        }

                        ModelPresetType.CYBER_DRONE -> {
                            SphereNode(
                                radius = 0.28f,
                                center = Float3(0f, 0f, 0f),
                                materialInstance = mainMaterial,
                                rotation = Float3(0f, rotationAngle, 0f)
                            )
                            CylinderNode(
                                radius = 0.08f,
                                height = 0.34f,
                                center = Float3(-0.36f, 0f, 0f),
                                materialInstance = accentMaterial,
                                rotation = Float3(0f, rotationAngle, 0f)
                            )
                            CylinderNode(
                                radius = 0.08f,
                                height = 0.34f,
                                center = Float3(0.36f, 0f, 0f),
                                materialInstance = accentMaterial,
                                rotation = Float3(0f, rotationAngle, 0f)
                            )
                            TorusNode(
                                majorRadius = 0.44f,
                                minorRadius = 0.025f,
                                center = Float3(0f, -0.04f, 0f),
                                materialInstance = glowMaterial,
                                rotation = Float3(90f, rotationAngle * 1.5f, 0f)
                            )
                        }

                        ModelPresetType.QUANTUM_GYRO -> {
                            TorusNode(
                                majorRadius = 0.42f,
                                minorRadius = 0.035f,
                                center = Float3(0f, 0f, 0f),
                                materialInstance = mainMaterial,
                                rotation = Float3(rotationAngle, 0f, 0f)
                            )
                            TorusNode(
                                majorRadius = 0.28f,
                                minorRadius = 0.03f,
                                center = Float3(0f, 0f, 0f),
                                materialInstance = accentMaterial,
                                rotation = Float3(0f, rotationAngle * 1.4f, 90f)
                            )
                            SphereNode(
                                radius = 0.12f,
                                center = Float3(0f, 0f, 0f),
                                materialInstance = glowMaterial
                            )
                        }

                        ModelPresetType.PRISM_CRYSTAL -> {
                            CylinderNode(
                                radius = 0.22f,
                                height = 0.72f,
                                center = Float3(0f, 0f, 0f),
                                materialInstance = mainMaterial,
                                rotation = Float3(10f, rotationAngle, 0f)
                            )
                            CylinderNode(
                                radius = 0.36f,
                                height = 0.06f,
                                center = Float3(0f, -0.38f, 0f),
                                materialInstance = accentMaterial
                            )
                        }

                        ModelPresetType.GEOMETRIC_CUBE -> {
                            CubeNode(
                                size = Float3(0.52f, 0.52f, 0.52f),
                                center = Float3(0f, 0f, 0f),
                                materialInstance = mainMaterial,
                                rotation = Float3(35f, rotationAngle, 45f)
                            )
                            TorusNode(
                                majorRadius = 0.46f,
                                minorRadius = 0.02f,
                                center = Float3(0f, 0f, 0f),
                                materialInstance = accentMaterial,
                                rotation = Float3(0f, rotationAngle * 0.8f, 0f)
                            )
                        }

                        ModelPresetType.CUSTOM_FILE -> {
                            CubeNode(
                                size = Float3(0.5f, 0.5f, 0.5f),
                                center = Float3(0f, 0f, 0f),
                                materialInstance = mainMaterial,
                                rotation = Float3(0f, rotationAngle, 0f)
                            )
                        }
                    }
                }
            }
        }

        // Empty state message when model is removed / cleared
        if (model == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("empty_scene_state"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22232A))
                            .border(1.dp, Color(0xFF3B3D47), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewInAr,
                            contentDescription = "Empty Scene",
                            tint = Color(0xFF7E8086),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Scene Cleared",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap Open to choose a 3D model",
                        color = Color(0xFF7E8086),
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Model inspection HUD (when model is active)
        if (model != null) {
            // Quick auto-rotate toggle button
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = 80.dp),
                shape = CircleShape,
                color = if (autoRotate) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF16171B).copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (autoRotate) Color(0xFF00E5FF) else Color(0xFF2A2C35)
                )
            ) {
                IconButton(
                    onClick = { autoRotate = !autoRotate },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RotateRight,
                        contentDescription = "Turntable auto-rotate",
                        tint = if (autoRotate) Color(0xFF00E5FF) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
