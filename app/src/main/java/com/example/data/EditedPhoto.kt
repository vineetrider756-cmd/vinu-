package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "edited_photos")
data class EditedPhoto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val imageUrl: String, // Can be a local URI path or a template ID (e.g. "template_style1")
    val brightness: Float = 0f, // -1f to 1f or custom sliders
    val contrast: Float = 1f, // 0.1f to 2f
    val saturation: Float = 1f, // 0f to 2f
    val temperature: Float = 0f, // -1f to 1f
    val vignette: Float = 0f, // 0f to 1f
    val appliedFilter: String = "None", // e.g. "None", "Cinematic Warm", etc.
    val isUpscaled: Boolean = false,
    val isBackgroundRemoved: Boolean = false,
    val removedBgColor: Int = 0, // Solid color for transparent replacements (0 means transparent)
    val objectRemovedCount: Int = 0,
    val aiRecommendation: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
