package io.github.sceneview.sample.model

import androidx.compose.ui.graphics.Color
import dev.romainguy.kotlin.math.Float3

enum class AppMode(val displayName: String) {
    MR("MR"),
    AR("AR"),
    OBJECT("Object")
}

enum class ModelPresetType(val title: String, val subtitle: String) {
    MR_HEADSET("MR Headset", "Spatial holographic visor with optics"),
    CYBER_DRONE("Cyber Drone", "Spherical spatial probe with thrusters"),
    QUANTUM_GYRO("Quantum Gyro", "Perpendicular dual orbital rings"),
    PRISM_CRYSTAL("Prism Crystal", "Faceted spatial refractive monolith"),
    GEOMETRIC_CUBE("Quantum Cube", "High-metallic beveled core unit"),
    CUSTOM_FILE("Imported Model", "Custom .glb / .gltf file")
}

data class SpatialModel(
    val id: String,
    val type: ModelPresetType,
    val name: String,
    val primaryColor: Color,
    val metallic: Float = 0.7f,
    val roughness: Float = 0.25f,
    val customFilePath: String? = null
)

val DefaultPresets = listOf(
    SpatialModel(
        id = "mr_headset",
        type = ModelPresetType.MR_HEADSET,
        name = "Spatial MR Visor",
        primaryColor = Color(0xFF00B0FF),
        metallic = 0.85f,
        roughness = 0.15f
    ),
    SpatialModel(
        id = "cyber_drone",
        type = ModelPresetType.CYBER_DRONE,
        name = "Orbital Drone",
        primaryColor = Color(0xFF00E5FF),
        metallic = 0.9f,
        roughness = 0.2f
    ),
    SpatialModel(
        id = "quantum_gyro",
        type = ModelPresetType.QUANTUM_GYRO,
        name = "Quantum Gyroscope",
        primaryColor = Color(0xFF7C4DFF),
        metallic = 0.95f,
        roughness = 0.1f
    ),
    SpatialModel(
        id = "prism_crystal",
        type = ModelPresetType.PRISM_CRYSTAL,
        name = "Emerald Monolith",
        primaryColor = Color(0xFF00E676),
        metallic = 0.3f,
        roughness = 0.05f
    ),
    SpatialModel(
        id = "quantum_cube",
        type = ModelPresetType.GEOMETRIC_CUBE,
        name = "Tesseract Core",
        primaryColor = Color(0xFFFF9100),
        metallic = 0.8f,
        roughness = 0.3f
    )
)

data class PlacedAnchor(
    val id: String = java.util.UUID.randomUUID().toString(),
    val position: Float3 = Float3(0f, 0f, -0.6f),
    val rotation: Float3 = Float3(0f, 0f, 0f),
    val scale: Float3 = Float3(1f, 1f, 1f),
    val model: SpatialModel
)
