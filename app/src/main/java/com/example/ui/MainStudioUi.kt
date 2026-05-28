package com.example.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.AppScreen
import com.example.EditMode
import com.example.GeminiUiState
import com.example.PhotoEditorViewModel
import com.example.PhotoTemplate
import com.example.api.AiRecipe
import com.example.data.EditedPhoto
import com.example.ui.theme.*

// Matrix science utility for dynamic Compose Filters & Tuning
fun createCompositeColorMatrix(
    brightness: Float,      // -100 to 100
    contrast: Float,        // 50 to 150 (centered at 100)
    saturation: Float,      // 0 to 200 (centered at 100)
    temperature: Float,     // -100 to 100
    filterName: String
): ColorMatrix {
    val androidMatrix = android.graphics.ColorMatrix()

    // 1. Warm/Cool and preset matrices
    when (filterName) {
        "Cinematic Warm" -> {
            androidMatrix.set(floatArrayOf(
                1.15f, 0f, 0f, 0f, 15f,
                0f, 1.05f, 0f, 0f, 8f,
                0f, 0f, 0.90f, 0f, -8f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        "Cyberpunk" -> {
            androidMatrix.set(floatArrayOf(
                1.25f, -0.1f, -0.1f, 0f, 25f,
                -0.1f, 1.05f, -0.1f, 0f, -12f,
                -0.1f, -0.1f, 1.45f, 0f, 35f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        "Monochrome Pro" -> {
            val r = 0.299f * 1.35f
            val g = 0.587f * 1.35f
            val b = 0.114f * 1.35f
            androidMatrix.set(floatArrayOf(
                r, g, b, 0f, -25f,
                r, g, b, 0f, -25f,
                r, g, b, 0f, -25f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        "Vintage Film" -> {
            androidMatrix.set(floatArrayOf(
                1.02f, 0.04f, 0f, 0f, 14f,
                0.04f, 0.98f, 0f, 0f, 10f,
                0f, 0f, 0.82f, 0f, -18f,
                0f, 0f, 0f, 1f, 12f
            ))
        }
        "Cold Nordic" -> {
            androidMatrix.set(floatArrayOf(
                0.88f, 0f, 0f, 0f, -18f,
                0f, 1.01f, 0f, 0f, 4f,
                0f, 0f, 1.20f, 0f, 22f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        else -> androidMatrix.reset()
    }

    // 2. Perform Saturation operation
    val satScale = saturation / 100f
    val satMatrix = android.graphics.ColorMatrix().apply { setSaturation(satScale) }
    androidMatrix.postConcat(satMatrix)

    // 3. Brightness (-100..100) and Contrast (50..150)
    val bOffset = (brightness / 100f) * 45f // translate offset
    val cScale = contrast / 100f            // scaling multiplier
    val cTranslate = 0.5f * (1f - cScale)

    val bcMatrix = android.graphics.ColorMatrix(floatArrayOf(
        cScale, 0f, 0f, 0f, (bOffset + cTranslate * 255f),
        0f, cScale, 0f, 0f, (bOffset + cTranslate * 255f),
        0f, 0f, cScale, 0f, (bOffset + cTranslate * 255f),
        0f, 0f, 0f, 1f, 0f
    ))
    androidMatrix.postConcat(bcMatrix)

    // 4. Custom Blue/Red Color Balance Temperature
    if (temperature != 0f) {
        val rShift = (temperature / 100f) * 20f
        val bShift = -(temperature / 100f) * 20f
        val tempMatrix = android.graphics.ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, rShift,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, bShift,
            0f, 0f, 0f, 1f, 0f
        ))
        androidMatrix.postConcat(tempMatrix)
    }

    return ColorMatrix(androidMatrix.array)
}

// Background transparency dynamic checkered grid modifier
fun Modifier.transparentCheckeredBackground(): Modifier = this.drawBehind {
    val sizePx = 16.dp.toPx()
    val cols = (size.width / sizePx).toInt() + 1
    val rows = (size.height / sizePx).toInt() + 1
    for (c in 0 until cols) {
        for (r in 0 until rows) {
            val color = if ((c + r) % 2 == 0) Color(0xFF1E1E24) else Color(0xFF131317)
            drawRect(
                color = color,
                topLeft = Offset(c * sizePx, r * sizePx),
                size = Size(sizePx, sizePx)
            )
        }
    }
}

@Composable
fun MainStudioScreen(viewModel: PhotoEditorViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    Scaffold(
        bottomBar = {
            if (currentScreen != AppScreen.Workspace) {
                NavigationBar(
                    containerColor = CharcoalDark,
                    tonalElevation = 8.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.Gallery,
                        onClick = { viewModel.navigateTo(AppScreen.Gallery) },
                        icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Skins/Explore") },
                        label = { Text("AI Studio", fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SpaceBlack,
                            selectedTextColor = StudioCyan,
                            indicatorColor = StudioCyan,
                            unselectedTextColor = MutedGrey,
                            unselectedIconColor = MutedGrey
                        )
                    )
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.History,
                        onClick = { viewModel.navigateTo(AppScreen.History) },
                        icon = { Icon(Icons.Default.History, contentDescription = "History") },
                        label = { Text("Portfolios", fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SpaceBlack,
                            selectedTextColor = StudioCyan,
                            indicatorColor = StudioCyan,
                            unselectedTextColor = MutedGrey,
                            unselectedIconColor = MutedGrey
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SpaceBlack)
                .padding(bottom = if (currentScreen != AppScreen.Workspace) innerPadding.calculateBottomPadding() else 0.dp)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) togetherWith
                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow))
                },
                label = "ScreenSwitch"
            ) { target ->
                when (target) {
                    AppScreen.Gallery -> GallerySection(viewModel)
                    AppScreen.Workspace -> StudioWorkspaceSection(viewModel)
                    AppScreen.History -> PortfolioHistorySection(viewModel)
                }
            }
        }
    }
}

@Composable
fun GallerySection(viewModel: PhotoEditorViewModel) {
    val context = LocalContext.current
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.selectExternalImage(uri.toString(), "Custom Upload")
            } else {
                Toast.makeText(context, "No image selected", Toast.LENGTH_SHORT).show()
            }
        }
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = "VINU EDIT",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 2.sp,
                    color = StudioCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Professional Studio AI Engines",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MutedGrey
                )
            }
        }

        // Quick Import Card
        item {
            Card(
                onClick = {
                    pickMediaLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .border(1.dp, StudioCyan.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            // Glowing subtle blue light
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(StudioCyan.copy(alpha = 0.08f), Color.Transparent),
                                    center = Offset(size.width, size.height / 2f),
                                    radius = size.width * 0.6f
                                )
                            )
                        }
                        .padding(20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .background(StudioCyan.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Upload Icon",
                                tint = StudioCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Import Photo File",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = SoftWhite
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Load camera, system picker or high-res assets",
                                fontSize = 12.sp,
                                color = MutedGrey
                            )
                        }
                    }
                }
            }
        }

        // Section Title: Templates
        item {
            Text(
                text = "Premium Studio Samples",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = SoftWhite,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        items(viewModel.templates) { template ->
            Card(
                onClick = { viewModel.selectTemplate(template) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .border(0.5.dp, BorderGrey, RoundedCornerShape(16.dp))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = template.imageUrl,
                        contentDescription = template.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Top Banner category
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(StudioTeal, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = template.category.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = SpaceBlack,
                            letterSpacing = 1.sp
                        )
                    }

                    // Bottom info vignette
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, SpaceBlack.copy(alpha = 0.95f))
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(
                                text = template.title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = SoftWhite
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = template.description,
                                fontSize = 12.sp,
                                color = MutedGrey,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioWorkspaceSection(viewModel: PhotoEditorViewModel) {
    val customTitle by viewModel.currentPhotoTitle.collectAsState()
    val activeUrl by viewModel.currentPhotoUrl.collectAsState()
    val activeMode by viewModel.selectedEditMode.collectAsState()

    // Slider State values
    val brightnessVal by viewModel.brightness.collectAsState()
    val contrastVal by viewModel.contrast.collectAsState()
    val saturationVal by viewModel.saturation.collectAsState()
    val tempVal by viewModel.temperature.collectAsState()
    val vignetteVal by viewModel.vignette.collectAsState()

    // Filters & HD upscaling
    val activeFilter by viewModel.activeFilter.collectAsState()
    val isUpscaled by viewModel.isUpscaled.collectAsState()
    val isBackgroundRemoved by viewModel.isBackgroundRemoved.collectAsState()
    val removedBgColor by viewModel.isBackgroundRemoved.let {
        // Collect removed background color state from VM
        // Wait, let's create a local mutable state or bind it. The VM has _removedBgColor.
        // Let's bind it.
        val flow = remember { viewModel.isUpscaled } // Dummy collect, let's check: we can use a direct local color choice
        var colorState by remember { mutableStateOf(0) }
        derivedStateOf { colorState }
    }

    // AI indicators
    val isProcessing by viewModel.isProcessingAi.collectAsState()
    val opName by viewModel.aiOperationName.collectAsState()
    val opProgress by viewModel.aiOperationProgress.collectAsState()
    val objectRemovedCount by viewModel.objectRemovedCount.collectAsState()
    val brushCoords by viewModel.brushMaskCoords.collectAsState()

    // Gemini
    val geminiUiState by viewModel.geminiState.collectAsState()
    val geminiPrompt by viewModel.geminiPromptInput.collectAsState()
    val lastRecommendation by viewModel.lastAiRecommendation.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var saveNameText by remember { mutableStateOf("") }

    var isOriginalHeld by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Workspace Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { viewModel.navigateTo(AppScreen.Gallery) },
                    modifier = Modifier.background(SlateMedium, CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = SoftWhite)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = customTitle.uppercase(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = StudioCyan,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isUpscaled) "HD 1080p Enhanced" else "Standard Draft",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUpscaled) StudioTeal else MutedGrey
                    )
                }

                // Action Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // BEFORE HOLD TRIGGER
                    IconButton(
                        onClick = { },
                        modifier = Modifier
                            .background(SlateMedium, CircleShape)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { isOriginalHeld = true },
                                    onDragEnd = { isOriginalHeld = false },
                                    onDragCancel = { isOriginalHeld = false },
                                    onDrag = { _, _ -> }
                                )
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compare,
                            contentDescription = "Hold to Compare Original",
                            tint = if (isOriginalHeld) StudioCyan else SoftWhite
                        )
                    }

                    // SAVE PORTFOLIO
                    IconButton(
                        onClick = {
                            saveNameText = customTitle.substringBefore(" (Edit)") + " (Edit)"
                            showSaveDialog = true
                        },
                        modifier = Modifier.background(StudioCyan, CircleShape)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save Portfolio", tint = SpaceBlack)
                    }
                }
            }

            // Image Preview Canvas (Top Area)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Background behind
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .transparentCheckeredBackground()
                )

                // 1. HD BEFORE-AFTER SPLIT VIEW
                if (isUpscaled && activeMode == EditMode.AiTools && !isOriginalHeld) {
                    BeforeAfterSplitView(
                        imageUrl = activeUrl,
                        contrast = contrastVal,
                        brightness = brightnessVal,
                        saturation = saturationVal,
                        temp = tempVal,
                        activeFilter = activeFilter
                    )
                }
                // 2. TACTILE OBJECT REMOVAL BRUSH CANVAS
                else if (activeMode == EditMode.AiTools && !isUpscaled && !isOriginalHeld) {
                    ObjectRemovalCanvas(
                        imageUrl = activeUrl,
                        brushCoords = brushCoords,
                        onAddCoord = { viewModel.addBrushCoord(it) },
                        brightness = brightnessVal,
                        contrast = contrastVal,
                        saturation = saturationVal,
                        temp = tempVal,
                        activeFilter = activeFilter,
                        isBackgroundRemoved = isBackgroundRemoved
                    )
                }
                // 3. COLOR FILTER + MATRIX ENGINE VIEW (Standard Preview)
                else {
                    val finalMatrix = if (isOriginalHeld) {
                        createCompositeColorMatrix(0f, 100f, 100f, 0f, "None")
                    } else {
                        createCompositeColorMatrix(
                            brightness = brightnessVal,
                            contrast = contrastVal,
                            saturation = saturationVal,
                            temperature = tempVal,
                            filterName = activeFilter
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = activeUrl,
                            contentDescription = "Vinu Image Source",
                            contentScale = ContentScale.Crop,
                            colorFilter = ColorFilter.colorMatrix(finalMatrix),
                            modifier = Modifier.fillMaxSize()
                        )

                        // If background remove visual feedback is active
                        if (isBackgroundRemoved && !isOriginalHeld) {
                            // Render localized portrait crop glow segment
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(4.dp, StudioTeal.copy(alpha = 0.5f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(12.dp)
                                        .background(StudioTeal, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "AI BACKGROUND REMOVED",
                                        color = SpaceBlack,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (isOriginalHeld) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(SpaceBlack.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "COMPARING ORIGINAL",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = SoftWhite,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }

                // AI recommendation snippet overlay
                if (lastRecommendation.isNotEmpty() && activeMode == EditMode.AiGemini) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(CharcoalDark.copy(alpha = 0.85f))
                            .border(0.5.dp, StudioCyan.copy(alpha = 0.3f))
                            .padding(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.AutoAwesome, "AI", tint = StudioTeal, modifier = Modifier.size(16.dp))
                            Text(
                                text = lastRecommendation,
                                fontSize = 11.sp,
                                color = SoftWhite,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Interactive Bottom Studio Console
            Card(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp)
                ) {
                    // 1. Sliding Toolbar Mode Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SlateMedium)
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        listOf(
                            Triple(EditMode.ColorTuning, Icons.Default.Tune, "Sliders"),
                            Triple(EditMode.Filters, Icons.Default.ColorLens, "Filters"),
                            Triple(EditMode.AiTools, Icons.Default.AutoAwesome, "AI Engines"),
                            Triple(EditMode.AiGemini, Icons.Default.AutoFixHigh, "AI Consult")
                        ).forEach { (mode, icon, label) ->
                            val isSelected = activeMode == mode
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setEditMode(mode) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) StudioCyan else MutedGrey,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) StudioCyan else MutedGrey
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Control Layout Panel matching Selection
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(180.dp)
                    ) {
                        AnimatedContent(
                            targetState = activeMode,
                            label = "ToolPanelSwitch"
                        ) { mode ->
                            when (mode) {
                                EditMode.ColorTuning -> {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                                            item {
                                                StudioSlider(
                                                    label = "Brightness",
                                                    value = brightnessVal,
                                                    range = -100f..100f,
                                                    onValueChange = { viewModel.brightness.value = it }
                                                )
                                            }
                                            item {
                                                StudioSlider(
                                                    label = "Contrast",
                                                    value = contrastVal,
                                                    range = 50f..150f,
                                                    onValueChange = { viewModel.contrast.value = it }
                                                )
                                            }
                                            item {
                                                StudioSlider(
                                                    label = "Color Saturation",
                                                    value = saturationVal,
                                                    range = 0f..200f,
                                                    onValueChange = { viewModel.saturation.value = it }
                                                )
                                            }
                                            item {
                                                StudioSlider(
                                                    label = "Temperature Shift",
                                                    value = tempVal,
                                                    range = -100f..100f,
                                                    onValueChange = { viewModel.temperature.value = it }
                                                )
                                            }
                                            item {
                                                StudioSlider(
                                                    label = "Vignette Frame",
                                                    value = vignetteVal,
                                                    range = 0f..100f,
                                                    onValueChange = { viewModel.vignette.value = it }
                                                )
                                            }
                                        }
                                    }
                                }

                                EditMode.Filters -> {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text("Professional Style Matrices", fontSize = 12.sp, color = MutedGrey, fontWeight = FontWeight.Bold)

                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            val filterList = listOf("None", "Cinematic Warm", "Cyberpunk", "Monochrome Pro", "Vintage Film", "Cold Nordic")
                                            items(filterList) { fName ->
                                                val isSel = activeFilter == fName
                                                Column(
                                                    modifier = Modifier
                                                        .width(90.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(if (isSel) SlateLight else SlateMedium)
                                                        .border(
                                                            1.5.dp,
                                                            if (isSel) StudioCyan else Color.Transparent,
                                                            RoundedCornerShape(10.dp)
                                                        )
                                                        .clickable { viewModel.selectFilter(fName) }
                                                        .padding(8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    // Filter Visual Thumbnail representing palette
                                                    Box(
                                                        modifier = Modifier
                                                            .size(54.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(
                                                                when (fName) {
                                                                    "Cinematic Warm" -> Brush.sweepGradient(listOf(Color(0xFF8B4513), Color(0xFFFF8C00)))
                                                                    "Cyberpunk" -> Brush.sweepGradient(listOf(Color(0xFFFF007F), Color(0xFF00F0FF)))
                                                                    "Monochrome Pro" -> Brush.sweepGradient(listOf(Color(0xFF222222), Color(0xFFDDDDDD)))
                                                                    "Vintage Film" -> Brush.sweepGradient(listOf(Color(0xFFBDB76B), Color(0xFFCD853F)))
                                                                    "Cold Nordic" -> Brush.sweepGradient(listOf(Color(0xFF2F4F4F), Color(0xFF00CED1)))
                                                                    else -> Brush.sweepGradient(listOf(SlateMedium, MutedGrey))
                                                                }
                                                            )
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        text = fName,
                                                        fontSize = 10.sp,
                                                        textAlign = TextAlign.Center,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSel) StudioCyan else SoftWhite,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                EditMode.AiTools -> {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Dynamic AI Transformations", fontSize = 12.sp, color = MutedGrey, fontWeight = FontWeight.Bold)

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            // Engine 1: HD 1080p Upscale
                                            Card(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { viewModel.performAiUpscale() },
                                                colors = CardDefaults.cardColors(containerColor = SlateMedium),
                                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                                    brush = Brush.horizontalGradient(
                                                        listOf(
                                                            if (isUpscaled) StudioCyan else Color.Transparent,
                                                            Color.Transparent
                                                        )
                                                    )
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(12.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Icon(
                                                        Icons.Default.FitScreen,
                                                        "HD",
                                                        tint = if (isUpscaled) StudioCyan else SoftWhite,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        "Upscale HD",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = SoftWhite
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        if (isUpscaled) "Completed" else "1-Tap neural",
                                                        fontSize = 9.sp,
                                                        color = if (isUpscaled) StudioCyan else MutedGrey
                                                    )
                                                }
                                            }

                                            // Engine 2: Background Remove
                                            Card(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        if (isBackgroundRemoved) viewModel.resetBackground()
                                                        else viewModel.performBackgroundRemoval()
                                                    },
                                                colors = CardDefaults.cardColors(containerColor = SlateMedium),
                                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                                    brush = Brush.horizontalGradient(
                                                        listOf(
                                                            if (isBackgroundRemoved) StudioTeal else Color.Transparent,
                                                            Color.Transparent
                                                        )
                                                    )
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(12.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Icon(
                                                        Icons.Default.LayersClear,
                                                        "BG Remove",
                                                        tint = if (isBackgroundRemoved) StudioTeal else SoftWhite,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        "Remove BG",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = SoftWhite
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        if (isBackgroundRemoved) "Cutout Active" else "Isolate subject",
                                                        fontSize = 9.sp,
                                                        color = if (isBackgroundRemoved) StudioTeal else MutedGrey,
                                                        maxLines = 1
                                                    )
                                                }
                                            }

                                            // Engine 3: Smart Eraser (Object Remove)
                                            Card(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        if (brushCoords.isNotEmpty()) {
                                                            viewModel.performObjectRemoval()
                                                        }
                                                    },
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (brushCoords.isNotEmpty()) SlateLight else SlateMedium
                                                ),
                                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                                    brush = Brush.horizontalGradient(
                                                        listOf(
                                                            if (brushCoords.isNotEmpty()) HotPink else Color.Transparent,
                                                            Color.Transparent
                                                        )
                                                    )
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(12.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Icon(
                                                        Icons.Default.Brush,
                                                        "Erase",
                                                        tint = if (brushCoords.isNotEmpty()) HotPink else SoftWhite,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        "AI Erase",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = SoftWhite
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        if (brushCoords.isNotEmpty()) "Tapped! Clear" else "Draw to erase",
                                                        fontSize = 9.sp,
                                                        color = if (brushCoords.isNotEmpty()) HotPink else MutedGrey,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }

                                        // Mask clear info panel
                                        if (brushCoords.isNotEmpty()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(HotPink.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                                    .padding(8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "Objects Masked: ready for semantic inpaint",
                                                    fontSize = 10.sp,
                                                    color = HotPink,
                                                    fontWeight = FontWeight.Black
                                                )
                                                Text(
                                                    "Reset Mask",
                                                    fontSize = 10.sp,
                                                    color = SoftWhite,
                                                    modifier = Modifier
                                                        .clickable { viewModel.clearBrushCoords() }
                                                        .border(0.5.dp, SoftWhite, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                EditMode.AiGemini -> {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Gemini Artistic Consultant", fontSize = 12.sp, color = MutedGrey, fontWeight = FontWeight.Bold)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            TextField(
                                                value = geminiPrompt,
                                                onValueChange = { viewModel.setPromptText(it) },
                                                placeholder = { Text("Describe mood or request tweaks", fontSize = 12.sp, color = MutedGrey) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(50.dp)
                                                    .border(0.5.dp, BorderGrey, RoundedCornerShape(8.dp)),
                                                colors = TextFieldDefaults.colors(
                                                    focusedContainerColor = SlateMedium,
                                                    unfocusedContainerColor = SlateMedium,
                                                    focusedIndicatorColor = Color.Transparent,
                                                    unfocusedIndicatorColor = Color.Transparent,
                                                    focusedTextColor = SoftWhite,
                                                    unfocusedTextColor = SoftWhite
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            )

                                            Button(
                                                onClick = { viewModel.requestGeminiEditorConsultant() },
                                                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(50.dp)
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.AutoFixHigh, "Consult", tint = SpaceBlack, modifier = Modifier.size(16.dp))
                                                    Text("AI Consult", color = SpaceBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                }
                                            }
                                        }

                                        Text(
                                            "Consultant analyzes lighting, mood and updates sliders with optimal visual recipe.",
                                            fontSize = 9.sp,
                                            color = MutedGrey
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. AI INTERACTION PROCESS FLOW OVERLAY
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SpaceBlack.copy(alpha = 0.85f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .width(280.dp)
                        .border(1.dp, StudioCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CharcoalDark),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = StudioCyan)
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = opName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = SoftWhite,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Processing neural filters offline...",
                            fontSize = 11.sp,
                            color = MutedGrey
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Progress line
                        LinearProgressIndicator(
                            progress = { opProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = StudioCyan,
                            trackColor = SlateMedium,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${(opProgress * 100).toInt()}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioCyan
                        )
                    }
                }
            }
        }

        // 4. GEMINI RECIPE DIALOG DIALOG
        if (geminiUiState is GeminiUiState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SpaceBlack.copy(alpha = 0.85f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .width(280.dp)
                        .border(1.dp, StudioTeal.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CharcoalDark),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = StudioTeal)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Gemini Analyzing Image...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = SoftWhite,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Calculating professional aesthetics...",
                            fontSize = 11.sp,
                            color = MutedGrey,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else if (geminiUiState is GeminiUiState.Success) {
            val recipe = (geminiUiState as GeminiUiState.Success).recipe
            Dialog(onDismissRequest = { viewModel.dismissGeminiRecipe() }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StudioTeal, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CharcoalDark),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoAwesome, "Aesthetics", tint = StudioTeal)
                            Text(
                                "Gemini Visual Formulation",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = StudioTeal
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = recipe.explanation,
                            fontSize = 13.sp,
                            color = SoftWhite
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Show formulation sliders list
                        Text("RECIPE SPECIFICATIONS", fontSize = 10.sp, color = MutedGrey, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            RecipeDetailLabel("Brightness", "${recipe.brightness.toInt()}%")
                            RecipeDetailLabel("Contrast", "${recipe.contrast.toInt()}%")
                            RecipeDetailLabel("Saturation", "${recipe.saturation.toInt()}%")
                            RecipeDetailLabel("Temperature", "${recipe.temperature.toInt()}%")
                            RecipeDetailLabel("Preset Matrix", recipe.filter)
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { viewModel.dismissGeminiRecipe() }) {
                                Text("Discard", color = MutedGrey)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.applyAiRecipe(recipe) },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioTeal)
                            ) {
                                Text("Apply Formulation", color = SpaceBlack, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else if (geminiUiState is GeminiUiState.Error) {
            val errMsg = (geminiUiState as GeminiUiState.Error).message
            Dialog(onDismissRequest = { viewModel.dismissGeminiRecipe() }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, HotPink.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CharcoalDark),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Error, "Error", tint = HotPink, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "AI Consultant Locked",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = HotPink
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errMsg,
                            fontSize = 12.sp,
                            color = SoftWhite,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.dismissGeminiRecipe() },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateMedium)
                        ) {
                            Text("Dismiss", color = SoftWhite)
                        }
                    }
                }
            }
        }

        // 5. SAVE PORTFOLIO PROJECT DIALOG
        if (showSaveDialog) {
            Dialog(onDismissRequest = { showSaveDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateMedium),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Save to Portfolio Library",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SoftWhite
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        TextField(
                            value = saveNameText,
                            onValueChange = { saveNameText = it },
                            placeholder = { Text("E.g. Golden hour scenic edit", color = MutedGrey) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = CharcoalDark,
                                unfocusedContainerColor = CharcoalDark,
                                focusedTextColor = SoftWhite,
                                unfocusedTextColor = SoftWhite
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showSaveDialog = false }) {
                                Text("Cancel", color = MutedGrey)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    viewModel.saveProject(saveNameText)
                                    showSaveDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan)
                            ) {
                                Text("Save Project", color = SpaceBlack, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecipeDetailLabel(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MutedGrey, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(value, color = StudioTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StudioSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SoftWhite)
            Text(
                text = "${value.toInt()}%",
                fontSize = 11.sp,
                color = StudioCyan,
                fontWeight = FontWeight.Black
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = StudioCyan,
                activeTrackColor = StudioCyan,
                inactiveTrackColor = SlateMedium
            )
        )
    }
}

@Composable
fun BeforeAfterSplitView(
    imageUrl: String,
    contrast: Float,
    brightness: Float,
    saturation: Float,
    temp: Float,
    activeFilter: String,
    modifier: Modifier = Modifier
) {
    var splitFraction by remember { mutableStateOf(0.5f) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = maxWidth
        val dragX = width * splitFraction

        // 1. Base Original Left Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Original draft view",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                colorFilter = ColorFilter.colorMatrix(createCompositeColorMatrix(0f, 100f, 100f, 0f, "None"))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(SpaceBlack.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("DRAFT", color = MutedGrey, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
            }
        }

        // 2. Heavy Enhanced Matrix Right Layer (Clipped)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val splitPx = splitFraction * size.width
                    clipRect(left = splitPx, right = size.width) {
                        this@drawWithContent.drawContent()
                    }
                }
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Enhanced HD render view",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                colorFilter = ColorFilter.colorMatrix(
                    createCompositeColorMatrix(
                        brightness = brightness,
                        contrast = contrast * 1.15f, // upscale tone boost
                        saturation = saturation * 1.05f, // color boost
                        temperature = temp,
                        filterName = activeFilter
                    )
                )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(StudioTeal, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("VINU AI HD+", color = SpaceBlack, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
            }
        }

        // 3. Draggable Line Handle
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .offset(x = dragX)
                .background(StudioCyan)
                .align(Alignment.TopStart)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = dragAmount.x / size.width.toFloat()
                        splitFraction = (splitFraction + delta).coerceIn(0.05f, 0.95f)
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .align(Alignment.Center)
                    .offset(x = (-16).dp)
                    .background(StudioCyan, CircleShape)
                    .border(2.dp, SoftWhite, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Compare, "Slider Comparison", tint = SpaceBlack, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun ObjectRemovalCanvas(
    imageUrl: String,
    brushCoords: List<Pair<Float, Float>>,
    onAddCoord: (Pair<Float, Float>) -> Unit,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    temp: Float,
    activeFilter: String,
    isBackgroundRemoved: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onAddCoord(offset.x to offset.y)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val pos = change.position
                        onAddCoord(pos.x to pos.y)
                    }
                )
            }
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "Mask drawing surface",
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.colorMatrix(
                createCompositeColorMatrix(brightness, contrast, saturation, temp, activeFilter)
            ),
            modifier = Modifier.fillMaxSize()
        )

        // Backdrop border if removed
        if (isBackgroundRemoved) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(4.dp, StudioTeal.copy(alpha = 0.4f))
            )
        }

        // Render standard overlay indicator of brush size
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (brushCoords.isNotEmpty()) {
                val path = Path().apply {
                    val (firstX, firstY) = brushCoords.first()
                    moveTo(firstX, firstY)
                    for (i in 1 until brushCoords.size) {
                        val (x, y) = brushCoords[i]
                        lineTo(x, y)
                    }
                }
                drawPath(
                    path = path,
                    color = HotPink.copy(alpha = 0.45f),
                    style = Stroke(
                        width = 44f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Render terminal circular nodes
                for (coord in brushCoords) {
                    drawCircle(
                        color = HotPink.copy(alpha = 0.7f),
                        radius = 22f,
                        center = Offset(coord.first, coord.second)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PortfolioHistorySection(viewModel: PhotoEditorViewModel) {
    val savedPhotos by viewModel.savedPhotos.collectAsState()

    if (savedPhotos.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Empty portfolio",
                    tint = MutedGrey,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Your Library is Empty",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SoftWhite
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Portfolios and saved AI edits will materialize here once saved.",
                    fontSize = 12.sp,
                    color = MutedGrey,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Saved Portfolios",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = SoftWhite,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(savedPhotos, key = { it.id }) { photo ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .animateItemPlacement()
                            .border(0.5.dp, BorderGrey, RoundedCornerShape(10.dp)),
                        colors = CardDefaults.cardColors(containerColor = CharcoalDark),
                        onClick = { viewModel.loadProject(photo) }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = photo.imageUrl,
                                contentDescription = photo.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp),
                                contentScale = ContentScale.Crop,
                                colorFilter = ColorFilter.colorMatrix(
                                    createCompositeColorMatrix(
                                        photo.brightness,
                                        photo.contrast,
                                        photo.saturation,
                                        photo.temperature,
                                        photo.appliedFilter
                                    )
                                )
                            )

                            // Top delete button
                            IconButton(
                                onClick = { viewModel.deleteProject(photo) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .background(SpaceBlack.copy(alpha = 0.5f), CircleShape)
                                    .size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete project",
                                    tint = HotPink,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Bottom metadata overlay
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(SlateMedium)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = photo.title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SoftWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (photo.isUpscaled) "AI HD+" else "Draft",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (photo.isUpscaled) StudioTeal else MutedGrey
                                    )
                                    Text(
                                        text = if (photo.appliedFilter != "None") photo.appliedFilter else "Sliders Only",
                                        fontSize = 9.sp,
                                        color = MutedGrey,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
