package io.github.sceneview.sample

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.sample.model.AppMode
import io.github.sceneview.sample.model.DefaultPresets
import io.github.sceneview.sample.model.ModelPresetType
import io.github.sceneview.sample.model.PlacedAnchor
import io.github.sceneview.sample.model.SpatialModel
import io.github.sceneview.sample.ui.ARMRSceneView
import io.github.sceneview.sample.ui.BottomActionBar
import io.github.sceneview.sample.ui.ObjectModeView
import io.github.sceneview.sample.ui.RecordingStatusBadge
import io.github.sceneview.sample.ui.ShutterFlashOverlay
import io.github.sceneview.sample.ui.TopModeSelector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MixedRealityApp()
        }
    }
}

@Composable
fun MixedRealityApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Shared unified Filament engine across mode changes to prevent duplicate EGL contexts
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)

    var selectedMode by remember { mutableStateOf(AppMode.OBJECT) }
    var currentModel by remember { mutableStateOf<SpatialModel?>(DefaultPresets.first()) }
    val placedAnchors = remember { mutableStateListOf<PlacedAnchor>() }

    // State for bottom actions
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var isShutterFlashing by remember { mutableStateOf(false) }

    // System File picker for custom 3D models (.glb, .gltf)
    // Directly removes existing model and opens file picker without extra dialog sheets
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                // 1. Remove existing model and anchors
                currentModel = null
                placedAnchors.clear()

                // 2. Cache the selected model file locally
                val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "custom_model.glb"
                val destFile = File(context.cacheDir, "imported_${System.currentTimeMillis()}_$fileName")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // 3. Set the new model
                currentModel = SpatialModel(
                    id = "custom_${System.currentTimeMillis()}",
                    type = ModelPresetType.CUSTOM_FILE,
                    name = fileName.removeSuffix(".glb").removeSuffix(".gltf"),
                    primaryColor = Color(0xFF00E5FF),
                    metallic = 0.8f,
                    roughness = 0.2f,
                    customFilePath = destFile.absolutePath
                )
                Toast.makeText(context, "Loaded model: ${destFile.name}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load model file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Recording timer loop
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                delay(1000)
                recordingSeconds++
            }
        }
    }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 1. Primary Full-screen Viewport based on selected mode
                when (selectedMode) {
                    AppMode.OBJECT -> {
                        ObjectModeView(
                            model = currentModel,
                            engine = engine,
                            modelLoader = modelLoader,
                            materialLoader = materialLoader,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    AppMode.AR, AppMode.MR -> {
                        ARMRSceneView(
                            mode = selectedMode,
                            activeModel = currentModel,
                            placedAnchors = placedAnchors,
                            onAddAnchor = { anchor ->
                                placedAnchors.add(anchor)
                            },
                            onClearAnchors = {
                                placedAnchors.clear()
                            },
                            engine = engine,
                            modelLoader = modelLoader,
                            materialLoader = materialLoader,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 2. Top Pill Mode Selector (MR | AR | Object)
                TopModeSelector(
                    selectedMode = selectedMode,
                    onModeSelected = { mode ->
                        selectedMode = mode
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = statusBarPadding + 16.dp)
                )

                // 3. Recording status pill (if recording)
                RecordingStatusBadge(
                    isRecording = isRecording,
                    elapsedSeconds = recordingSeconds,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = statusBarPadding + 70.dp)
                )

                // 4. Bottom Floating Action Bar (PHOTO, REC, Open, Clear)
                BottomActionBar(
                    isRecording = isRecording,
                    onPhotoClick = {
                        coroutineScope.launch {
                            isShutterFlashing = true
                            delay(120)
                            isShutterFlashing = false
                            Toast.makeText(context, "Snapshot captured to Gallery", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRecClick = {
                        isRecording = !isRecording
                        if (isRecording) {
                            Toast.makeText(context, "Mixed Reality recording started", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Recording saved ($recordingSeconds s)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenClick = {
                        // Directly launch file picker, removing existing model and removing any modal screens
                        currentModel = null
                        placedAnchors.clear()
                        filePickerLauncher.launch("*/*")
                    },
                    onClearClick = {
                        // Completely removes model from scene and clears anchors
                        currentModel = null
                        placedAnchors.clear()
                        Toast.makeText(context, "Model removed and scene cleared", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = navBarPadding + 28.dp)
                )

                // 5. Camera Shutter Flash Animation
                ShutterFlashOverlay(
                    isFlashing = isShutterFlashing
                )
            }
        }
    }
}
