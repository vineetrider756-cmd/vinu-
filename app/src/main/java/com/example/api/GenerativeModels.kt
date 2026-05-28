package com.example.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String // Base64 encoded image
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseFormat: ResponseFormat? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null
)

@JsonClass(generateAdapter = true)
data class ResponseFormat(
    val responseMimeType: String? = null, // "application/json" or "text/plain"
    val responseSchema: String? = null // Moshi doesn't strictly need a heavy Schema defined if we specify the format in the prompt, but having structured JSON is extremely helpful. We can set responseMimeType to "application/json"
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null
)

// Custom parsed AI Recipe returned by Gemini for photo enhancement!
@JsonClass(generateAdapter = true)
data class AiRecipe(
    val explanation: String,
    val brightness: Float, // -100f to 100f -> we will map to sliders
    val contrast: Float, // 10f to 200f (corresponds to scale 0.1f to 2.0f)
    val saturation: Float, // 0f to 200f (corresponds to scale 0.0f to 2.0f)
    val temperature: Float, // -100f to 100f
    val vignette: Float, // 0f to 100f
    val filter: String, // "None", "Cinematic Warm", "Cyberpunk", "Monochrome Pro", "Vintage Film", "Cold Nordic"
    val prompt: String // optimized visual inpainting/regeneration prompt
)
