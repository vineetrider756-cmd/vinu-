package com.example

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.*
import com.example.data.EditedPhoto
import com.example.data.PhotoDatabase
import com.example.data.PhotoRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen {
    Gallery,
    Workspace,
    History
}

enum class EditMode {
    ColorTuning,
    Filters,
    AiTools,
    AiGemini
}

// Visual photo templates with high-res royalty-free direct source links
data class PhotoTemplate(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val imageUrl: String,
    val defaultPrompt: String
)

sealed interface GeminiUiState {
    object Idle : GeminiUiState
    object Loading : GeminiUiState
    data class Success(val recipe: AiRecipe) : GeminiUiState
    data class Error(val message: String) : GeminiUiState
}

class PhotoEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PhotoRepository

    init {
        val database = PhotoDatabase.getDatabase(application)
        repository = PhotoRepository(database.photoDao())
    }

    // List of saved edited photo portfolios
    val savedPhotos: StateFlow<List<EditedPhoto>> = repository.allPhotos
        .let { flow ->
            val stateFlow = MutableStateFlow<List<EditedPhoto>>(emptyList())
            viewModelScope.launch {
                flow.collect { stateFlow.value = it }
            }
            stateFlow.asStateFlow()
        }

    // Default High-Res Templates
    val templates = listOf(
        PhotoTemplate(
            id = "fashion_noir",
            title = "Studio Portrait",
            description = "High-key fashion studio lighting portrait",
            category = "Portrait",
            imageUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=1000&auto=format&fit=crop",
            defaultPrompt = "A professional headshot with subtle neon reflections, soft bokeh, cinematic commercial photography."
        ),
        PhotoTemplate(
            id = "brutalist_arch",
            title = "Brutalist Pillar",
            description = "Geometric concrete structure casting architectural shadows",
            category = "Architecture",
            imageUrl = "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?q=80&w=1000&auto=format&fit=crop",
            defaultPrompt = "Brutalist concrete architecture home under deep dramatic evening sky, high contrast shadows."
        ),
        PhotoTemplate(
            id = "cyber_alley",
            title = "Tokyo Cyberpunk",
            description = "Rainy atmospheric Tokyo neon signs reflections alleyway",
            category = "Streetwear",
            imageUrl = "https://images.unsplash.com/photo-1519608487953-e999c86e7455?q=80&w=1000&auto=format&fit=crop",
            defaultPrompt = "Vibrant retro neon reflections, dark wet concrete street, cyberpunk atmosphere."
        ),
        PhotoTemplate(
            id = "valley_sunset",
            title = "Golden Ridge",
            description = "Cinematic sunset casting golden glow on valley ridges",
            category = "Landscape",
            imageUrl = "https://images.unsplash.com/photo-1472214222541-d510753a4707?q=80&w=1000&auto=format&fit=crop",
            defaultPrompt = "Stunning valley landscapes during majestic golden hour sunset glow, rich forest greens and warm shadows."
        )
    )

    // UI Screen States
    private val _currentScreen = MutableStateFlow(AppScreen.Gallery)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _selectedEditMode = MutableStateFlow(EditMode.ColorTuning)
    val selectedEditMode: StateFlow<EditMode> = _selectedEditMode.asStateFlow()

    // Current Image State
    private val _currentPhotoUrl = MutableStateFlow(templates[0].imageUrl)
    val currentPhotoUrl: StateFlow<String> = _currentPhotoUrl.asStateFlow()

    private val _currentPhotoTitle = MutableStateFlow(templates[0].title)
    val currentPhotoTitle: StateFlow<String> = _currentPhotoTitle.asStateFlow()

    private val _projectId = MutableStateFlow<Int?>(null) // null if unsaved new edit

    // Sliders state (Default levels centered or standard)
    val brightness = MutableStateFlow(0f)      // -100 to 100
    val contrast = MutableStateFlow(100f)     // 50 to 150
    val saturation = MutableStateFlow(100f)   // 0 to 200
    val temperature = MutableStateFlow(0f)    // -100 to 100
    val vignette = MutableStateFlow(0f)       // 0 to 100

    // Style Filters state
    private val _activeFilter = MutableStateFlow("None")
    val activeFilter: StateFlow<String> = _activeFilter.asStateFlow()

    // AI active states and tools
    private val _isUpscaled = MutableStateFlow(false)
    val isUpscaled: StateFlow<Boolean> = _isUpscaled.asStateFlow()

    private val _isBackgroundRemoved = MutableStateFlow(false)
    val isBackgroundRemoved: StateFlow<Boolean> = _isBackgroundRemoved.asStateFlow()

    private val _removedBgColor = MutableStateFlow(0) // 0 for transparent grid

    private val _objectRemovedCount = MutableStateFlow(0)
    val objectRemovedCount: StateFlow<Int> = _objectRemovedCount.asStateFlow()

    // Inpainting drawing coordinates (represented simply in Compose list)
    private val _brushMaskCoords = MutableStateFlow<List<Pair<Float, Float>>>(emptyList())
    val brushMaskCoords: StateFlow<List<Pair<Float, Float>>> = _brushMaskCoords.asStateFlow()

    // Loading & Progress overlays
    private val _isProcessingAi = MutableStateFlow(false)
    val isProcessingAi: StateFlow<Boolean> = _isProcessingAi.asStateFlow()

    private val _aiOperationName = MutableStateFlow("")
    val aiOperationName: StateFlow<String> = _aiOperationName.asStateFlow()

    private val _aiOperationProgress = MutableStateFlow(0f)
    val aiOperationProgress: StateFlow<Float> = _aiOperationProgress.asStateFlow()

    // Gemini API states
    private val _geminiState = MutableStateFlow<GeminiUiState>(GeminiUiState.Idle)
    val geminiState: StateFlow<GeminiUiState> = _geminiState.asStateFlow()

    private val _geminiPromptInput = MutableStateFlow("")
    val geminiPromptInput: StateFlow<String> = _geminiPromptInput.asStateFlow()

    private val _lastAiRecommendation = MutableStateFlow("")
    val lastAiRecommendation: StateFlow<String> = _lastAiRecommendation.asStateFlow()

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun setEditMode(mode: EditMode) {
        _selectedEditMode.value = mode
    }

    fun setPromptText(text: String) {
        _geminiPromptInput.value = text
    }

    // Set Active Image Project
    fun selectTemplate(template: PhotoTemplate) {
        resetWorkspace()
        _currentPhotoUrl.value = template.imageUrl
        _currentPhotoTitle.value = template.title
        _currentScreen.value = AppScreen.Workspace
    }

    fun selectExternalImage(uri: String, title: String) {
        resetWorkspace()
        _currentPhotoUrl.value = uri
        _currentPhotoTitle.value = title
        _currentScreen.value = AppScreen.Workspace
    }

    // Reset workspace to defaults
    private fun resetWorkspace() {
        _projectId.value = null
        brightness.value = 0f
        contrast.value = 100f
        saturation.value = 100f
        temperature.value = 0f
        vignette.value = 0f
        _activeFilter.value = "None"
        _isUpscaled.value = false
        _isBackgroundRemoved.value = false
        _removedBgColor.value = 0
        _objectRemovedCount.value = 0
        _brushMaskCoords.value = emptyList()
        _geminiState.value = GeminiUiState.Idle
        _geminiPromptInput.value = ""
        _lastAiRecommendation.value = ""
    }

    fun selectFilter(filterName: String) {
        _activeFilter.value = filterName
    }

    // Draw masking for Removal
    fun addBrushCoord(coord: Pair<Float, Float>) {
        _brushMaskCoords.value = _brushMaskCoords.value + coord
    }

    fun clearBrushCoords() {
        _brushMaskCoords.value = emptyList()
    }

    // Trigger local Room load
    fun loadProject(photo: EditedPhoto) {
        _projectId.value = photo.id
        _currentPhotoUrl.value = photo.imageUrl
        _currentPhotoTitle.value = photo.title
        brightness.value = photo.brightness
        contrast.value = photo.contrast
        saturation.value = photo.saturation
        temperature.value = photo.temperature
        vignette.value = photo.vignette
        _activeFilter.value = photo.appliedFilter
        _isUpscaled.value = photo.isUpscaled
        _isBackgroundRemoved.value = photo.isBackgroundRemoved
        _removedBgColor.value = photo.removedBgColor
        _objectRemovedCount.value = photo.objectRemovedCount
        _lastAiRecommendation.value = photo.aiRecommendation
        _currentScreen.value = AppScreen.Workspace
    }

    // Save Portfolio Project locally
    fun saveProject(customTitle: String? = null) {
        val titleToSave = customTitle ?: (_currentPhotoTitle.value.removePrefix("AI_") + " (Edit)")
        val editedPhoto = EditedPhoto(
            id = _projectId.value ?: 0,
            title = titleToSave,
            imageUrl = _currentPhotoUrl.value,
            brightness = brightness.value,
            contrast = contrast.value,
            saturation = saturation.value,
            temperature = temperature.value,
            vignette = vignette.value,
            appliedFilter = _activeFilter.value,
            isUpscaled = _isUpscaled.value,
            isBackgroundRemoved = _isBackgroundRemoved.value,
            removedBgColor = _removedBgColor.value,
            objectRemovedCount = _objectRemovedCount.value,
            aiRecommendation = _lastAiRecommendation.value,
            timestamp = System.currentTimeMillis()
        )

        viewModelScope.launch(Dispatchers.IO) {
            val id = repository.insertPhoto(editedPhoto)
            _projectId.value = id.toInt()
        }
    }

    fun deleteProject(photo: EditedPhoto) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePhoto(photo)
        }
    }

    // Simulated Deep AI Neural Upscale
    fun performAiUpscale() {
        viewModelScope.launch {
            _isProcessingAi.value = true
            _aiOperationName.value = "AI HD Super-Resolution Upscaling"
            _aiOperationProgress.value = 0f

            for (i in 1..20) {
                delay(120)
                _aiOperationProgress.value = i * 5f / 100f
            }

            _isUpscaled.value = true
            _isProcessingAi.value = false
            saveProject()
        }
    }

    // Simulated Intelligent AI Background cutout
    fun performBackgroundRemoval() {
        viewModelScope.launch {
            _isProcessingAi.value = true
            _aiOperationName.value = "AI Neural Outline Extraction & Background Cutout"
            _aiOperationProgress.value = 0f

            for (i in 1..25) {
                delay(90)
                _aiOperationProgress.value = i * 4f / 100f
            }

            _isBackgroundRemoved.value = true
            _isProcessingAi.value = false
            saveProject()
        }
    }

    fun setRemovedBgColor(colorValue: Int) {
        _removedBgColor.value = colorValue
        saveProject()
    }

    fun resetBackground() {
        _isBackgroundRemoved.value = false
        saveProject()
    }

    // Simulated content-aware object erasing
    fun performObjectRemoval() {
        if (_brushMaskCoords.value.isEmpty()) return

        viewModelScope.launch {
            _isProcessingAi.value = true
            _aiOperationName.value = "AI Semantic Contextual Inpainting"
            _aiOperationProgress.value = 0f

            for (i in 1..15) {
                delay(100)
                _aiOperationProgress.value = i * 6.6f / 100f
            }

            _objectRemovedCount.value = _objectRemovedCount.value + 1
            clearBrushCoords()
            _isProcessingAi.value = false
            saveProject()
        }
    }

    // Ask Gemini for intelligent artistic filter & slider settings
    fun requestGeminiEditorConsultant() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isBlank()) {
            _geminiState.value = GeminiUiState.Error("Gemini API Key is missing. Please set it in the AI Studio Secrets panel.")
            return
        }

        _geminiState.value = GeminiUiState.Loading

        viewModelScope.launch {
            val templateText = if (_currentPhotoUrl.value.startsWith("http")) {
                templates.find { it.imageUrl == _currentPhotoUrl.value }?.let {
                    "It is the template '${it.title}', which is described as: '${it.description}'."
                } ?: "It is a premium high-resolution client photograph."
            } else {
                "It is a custom uploaded photo from the user's gallery."
            }

            val systemInstructions = """
                You are Vinu Edit's Professional AI Consultant. Based on the user's photo details, lighting request or aesthetic desires, output an optimal slider and style preset formulation as a structured JSON object ONLY.
                Do NOT output any markdown tags beside JSON, and do not wrap in backticks.
                Output EXACTLY the following JSON format:
                {
                  "explanation": "Brief 1-2 sentence stylist explanation of why these settings were selected.",
                  "brightness": float value from -40 to 45,
                  "contrast": float value from 70 to 140,
                  "saturation": float value from 40 to 170,
                  "temperature": float value from -60 to 60,
                  "vignette": float value from 0 to 80,
                  "filter": string value representing one of the style presets exactly: "Cinematic Warm", "Cyberpunk", "Monochrome Pro", "Vintage Film", "Cold Nordic", or "None",
                  "prompt": "Recommended AI prompt text for background reconstruction/inpainting"
                }
            """.trimIndent()

            val promptText = """
                Active image metadata: $templateText.
                User's request: ${_geminiPromptInput.value.ifBlank { "Auto-Enhance this image based on professional visual aesthetics!" }}
            """.trimIndent()

            val requestBody = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = promptText)))),
                generationConfig = GenerationConfig(temperature = 0.7f),
                systemInstruction = Content(parts = listOf(Part(text = systemInstructions)))
            )

            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.service.generateContent(apiKey, requestBody)
                }

                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (responseText != null) {
                    // Clean up markdown code blocks if the model ignored instructions and wrapped it
                    val cleanText = responseText
                        .replace("```json", "")
                        .replace("```", "")
                        .trim()

                    val recipeAdapter = RetrofitClient.moshi.adapter(AiRecipe::class.java)
                    val recipe = recipeAdapter.fromJson(cleanText)

                    if (recipe != null) {
                        _geminiState.value = GeminiUiState.Success(recipe)
                        _lastAiRecommendation.value = recipe.explanation
                    } else {
                        _geminiState.value = GeminiUiState.Error("Failed to parse the AI Recipe. Received text: $cleanText")
                    }
                } else {
                    _geminiState.value = GeminiUiState.Error("Gemini returned an empty response. Please try again.")
                }
            } catch (e: Exception) {
                _geminiState.value = GeminiUiState.Error("API Connection Error: ${e.message ?: "Unknown issue"}")
            }
        }
    }

    // Apply the loaded recipe parameters to live sliders
    fun applyAiRecipe(recipe: AiRecipe) {
        brightness.value = recipe.brightness
        contrast.value = recipe.contrast
        saturation.value = recipe.saturation
        temperature.value = recipe.temperature
        vignette.value = recipe.vignette
        _activeFilter.value = recipe.filter
        _geminiState.value = GeminiUiState.Idle
        _geminiPromptInput.value = ""
        saveProject()
    }

    fun dismissGeminiRecipe() {
        _geminiState.value = GeminiUiState.Idle
    }
}
