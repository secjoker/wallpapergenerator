package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.db.WallpaperItem
import com.example.viewmodel.GenerationState
import com.example.viewmodel.WallpaperViewModel
import java.io.File

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun WallpaperApp(viewModel: WallpaperViewModel) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val allWallpapers by viewModel.allWallpapers.collectAsStateWithLifecycle()
    val currentBatch by viewModel.currentBatch.collectAsStateWithLifecycle()
    val referenceWallpaper by viewModel.referenceWallpaper.collectAsStateWithLifecycle()
    val currentVibeQuery by viewModel.currentVibeQuery.collectAsStateWithLifecycle()

    // Full screen viewer states
    var selectedWallpaperForDetail by remember { mutableStateOf<WallpaperItem?>(null) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        bottomBar = {
            CustomBottomNavigationBar(
                activeTab = currentTab,
                onTabSelected = { viewModel.currentTab.value = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Content depending on active tab
            Crossfade(
                targetState = currentTab,
                animationSpec = tween(250),
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                when (tab) {
                    "create" -> CreateTabScreen(
                        viewModel = viewModel,
                        currentVibeQuery = currentVibeQuery,
                        generationState = generationState,
                        currentBatch = currentBatch,
                        referenceWallpaper = referenceWallpaper,
                        onWallpaperClick = { selectedWallpaperForDetail = it }
                    )
                    "gallery" -> GalleryTabScreen(
                        allWallpapers = allWallpapers,
                        onWallpaperClick = { selectedWallpaperForDetail = it },
                        onFavoriteClick = { viewModel.toggleFavorite(it) },
                        onDeleteClick = { viewModel.deleteWallpaper(it) }
                    )
                    "settings" -> SettingsTabScreen(viewModel = viewModel)
                }
            }

            // Fullscreen Overlay details
            selectedWallpaperForDetail?.let { wallpaper ->
                FullScreenWallpaperViewer(
                    wallpaper = wallpaper,
                    currentBatch = currentBatch,
                    onDismiss = { selectedWallpaperForDetail = null },
                    onDownload = {
                        viewModel.downloadWallpaperToPublicGallery(
                            item = wallpaper,
                            onSuccess = {
                                Toast.makeText(context, "Wallpaper downloaded to your Pictures folder!", Toast.LENGTH_LONG).show()
                                // Update selection to capture updated saved badge status
                                selectedWallpaperForDetail = wallpaper.copy(isSavedToGallery = true)
                            },
                            onError = { errorMsg ->
                                Toast.makeText(context, "Download failed: $errorMsg", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    onRemix = {
                        viewModel.referenceWallpaper.value = wallpaper
                        viewModel.currentTab.value = "create" // Switch back to create panel
                        selectedWallpaperForDetail = null // Close detail modal
                        Toast.makeText(context, "Wallpaper attached as a reference style! Prompt a new vibe to Remix.", Toast.LENGTH_LONG).show()
                    },
                    onDelete = {
                        viewModel.deleteWallpaper(wallpaper)
                        selectedWallpaperForDetail = null
                        Toast.makeText(context, "Wallpaper deleted.", Toast.LENGTH_SHORT).show()
                    },
                    onToggleFavorite = {
                        viewModel.toggleFavorite(wallpaper)
                        // Sync visual selection
                        selectedWallpaperForDetail = wallpaper.copy(isFavorite = !wallpaper.isFavorite)
                    }
                )
            }
        }
    }
}

// --- TAB 1: CREATE COMPONENT PANEL ---

@Composable
fun CreateTabScreen(
    viewModel: WallpaperViewModel,
    currentVibeQuery: String,
    generationState: GenerationState,
    currentBatch: List<WallpaperItem>,
    referenceWallpaper: WallpaperItem?,
    onWallpaperClick: (WallpaperItem) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Aesthetic Bold Typography display header block
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = "CURRENT VIBE",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            val headerText = if (currentVibeQuery.isNotBlank()) currentVibeQuery else "WALLPAPER\nGENERATOR"
            Text(
                text = headerText.uppercase(),
                style = TextStyle(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 34.sp,
                    letterSpacing = (-1).sp
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input vibe prompt container
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // If remix image is present, show reference preview
                if (referenceWallpaper != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp, 84.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Gray)
                        ) {
                            AsyncImage(
                                model = File(referenceWallpaper.filePath),
                                contentDescription = "Remix style reference source",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "STYLE REMIX REFERENCE",
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                            )
                            Text(
                                text = "Based on: \"${referenceWallpaper.prompt}\"",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Close button to detach
                        IconButton(
                            onClick = { viewModel.clearReferenceWallpaper() },
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.secondary, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear reference item",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Input prompt text field
                OutlinedTextField(
                    value = currentVibeQuery,
                    onValueChange = { viewModel.currentVibeQuery.value = it },
                    placeholder = {
                        Text(
                            text = "Describe your desired vibe...",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vibe_prompt_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboardController?.hide()
                        viewModel.generateWallpapers()
                    })
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Preset style suggestions chips scroll row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            viewModel.vibePresets.forEach { preset ->
                val isSelected = currentVibeQuery.trim().lowercase() == preset.lowercase()
                Surface(
                    onClick = {
                        viewModel.selectPreset(preset)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("preset_$preset")
                ) {
                    Text(
                        text = preset,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large Action generate button
        Button(
            onClick = {
                keyboardController?.hide()
                viewModel.generateWallpapers()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("generate_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(16.dp),
            enabled = generationState !is GenerationState.Generating
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (referenceWallpaper != null) "REMIX WALLPAPERS" else "GENERATE 4 VARIATIONS",
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Handler for rendering status or batches
        when (generationState) {
            is GenerationState.Idle -> {
                if (currentBatch.isEmpty()) {
                    DefaultWelcomeState()
                } else {
                    WallpaperBatchGrid(
                        batch = currentBatch,
                        onWallpaperClick = onWallpaperClick
                    )
                }
            }
            is GenerationState.Generating -> {
                GenerationLoadingState(progressText = generationState.progressText)
            }
            is GenerationState.Success -> {
                WallpaperBatchGrid(
                    batch = currentBatch,
                    onWallpaperClick = onWallpaperClick
                )
            }
            is GenerationState.Error -> {
                GenerationErrorState(
                    errorMsg = generationState.message,
                    onRetry = { viewModel.generateWallpapers() }
                )
            }
        }
    }
}

@Composable
fun DefaultWelcomeState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings, // Placeholder
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Ready to visualize?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Type in an atmosphere vibe like \"rainy cyberpunk lo-fi\" or tap a suggestion chip above. The AI will synthesize 4 gorgeous 9:16 canvases for your smartphone.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun GenerationLoadingState(progressText: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = progressText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Synthesizing full 9:16 high-quality image renders.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun GenerationErrorState(errorMsg: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                RoundedCornerShape(16.dp)
            )
            .border(1.5.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Error status",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Generation Interrupted",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = errorMsg,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Try Again")
        }
    }
}

@Composable
fun WallpaperBatchGrid(
    batch: List<WallpaperItem>,
    onWallpaperClick: (WallpaperItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "GENERATED VARIATIONS",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.5.sp
                )
            )
        }

        // 2x2 grid representing variation items
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (rowIndex in 0..1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (colIndex in 0..1) {
                        val itemIndex = rowIndex * 2 + colIndex
                        if (itemIndex < batch.size) {
                            val item = batch[itemIndex]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(9f / 16f)
                                ) {
                                WallpaperCardItem(
                                    item = item,
                                    labelTag = "Variation 0${itemIndex + 1}",
                                    onClick = { onWallpaperClick(item) }
                                )
                            }
                        } else {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WallpaperCardItem(
    item: WallpaperItem,
    labelTag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onClick() }
            .testTag("wallpaper_card_${item.id}"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Load base image via file
            AsyncImage(
                model = File(item.filePath),
                contentDescription = "Wallpaper generated variant",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Dynamic vignette gradient layout
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                            startY = 400f
                        )
                    )
            )

            // Badges overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = labelTag,
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    )

                    // Icons representing status
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (item.isFavorite) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = "Favorited icon",
                                tint = Color.Red,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        if (item.isSavedToGallery) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Downloaded to gallery icon",
                                tint = Color.Green,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- TAB 2: GALLERY COMPONENT HISTORICAL TAB ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryTabScreen(
    allWallpapers: List<WallpaperItem>,
    onWallpaperClick: (WallpaperItem) -> Unit,
    onFavoriteClick: (WallpaperItem) -> Unit,
    onDeleteClick: (WallpaperItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Gallery Screen Header
        Text(
            text = "LOCAL HISTORY",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "SAVED & GENERATED",
            style = TextStyle(
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                lineHeight = 34.sp,
                letterSpacing = (-1).sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (allWallpapers.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Your library is empty",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Wallpapers you generate are saved here automatically. Go to Creation tab to synthesize some wallpapers!",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        } else {
            // Display Grid of historical items
            Box(modifier = Modifier.fillMaxSize()) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val chunkedList = allWallpapers.chunked(2)
                    chunkedList.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { item ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(9f / 16f)
                                ) {
                                    GalleryItemCard(
                                        item = item,
                                        onClick = { onWallpaperClick(item) },
                                        onFavoriteToggle = { onFavoriteClick(item) },
                                        onDeleteItem = { onDeleteClick(item) }
                                    )
                                }
                            }
                            if (rowItems.size < 2) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GalleryItemCard(
    item: WallpaperItem,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDeleteItem: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onClick() }
            .testTag("gallery_card_${item.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = File(item.filePath),
                contentDescription = "Past Wallpaper",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Vignette overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                            startY = 300f
                        )
                    )
            )

            // Interactive elements overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                // Delete button at top right
                IconButton(
                    onClick = onDeleteItem,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete item",
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Description + likes footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.prompt,
                        style = TextStyle(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    )

                    // Highlight favorite state
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite Toggle",
                            tint = if (item.isFavorite) Color.Red else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- TAB 3: OPTIONS SETTINGS COMPONENT ---

@Composable
fun SettingsTabScreen(viewModel: WallpaperViewModel) {
    val context = LocalContext.current
    val apiKey by viewModel.apiKeyField.collectAsStateWithLifecycle()
    val responseResolution by viewModel.responseResolution.collectAsStateWithLifecycle()
    var showWarning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "PREFERENCES",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "APP CONFIGURATION",
            style = TextStyle(
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                lineHeight = 34.sp,
                letterSpacing = (-1).sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Security Warning Card - MANDATORY according to build-config/secrets management instructions
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Close, // Warning close surrogate
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SECURITY WARNING",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Android APKs can be decompiled. High-sensitivity API keys stored in source or exposed in BuildConfig are not completely secure. Do not share your APK publically or upload to insecure repositories with valid keys.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card containing inputs
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Gemini API Key",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { viewModel.apiKeyField.value = it },
                    textStyle = TextStyle(fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Image Resolution",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val resolutions = listOf("512px", "1K")
                    resolutions.forEach { res ->
                        val isSel = responseResolution == res
                        Button(
                            onClick = { viewModel.responseResolution.value = res },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = res,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card displaying Model details
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Technical Model Details",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TechnicalRow(label = "Primary Model", value = "gemini-2.5-flash-image")
                    TechnicalRow(label = "Aspect Ratio", value = "9:16 (Vertical Portrait)")
                    TechnicalRow(label = "Generation Config", value = "Temperature (1.0), Image Modality")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action: Clean DB History
        Button(
            onClick = {
                viewModel.clearAllHistory()
                Toast.makeText(context, "All wallpapers and database entries wiped clean.", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Clear History & Storage", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TechnicalRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

// --- CORE UTILITY COMPONENT: DYNAMIC FULL SCREEN VIEWER OVERLAY ---

@Composable
fun FullScreenWallpaperViewer(
    wallpaper: WallpaperItem,
    currentBatch: List<WallpaperItem>,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onRemix: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { /* Block parent clicks */ }
    ) {
        // High def full scale wallpaper render
        AsyncImage(
            model = File(wallpaper.filePath),
            contentDescription = "Detailed Wallpaper layout view",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Visual gradients for legible text markings
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                        startY = 0f
                    )
                )
        )

        // Close and Favorite overlay buttons at root top
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss viewer",
                    tint = Color.White
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (wallpaper.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite status toggle",
                        tint = if (wallpaper.isFavorite) Color.Red else Color.White
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete from history completely",
                        tint = Color.White
                    )
                }
            }
        }

        // Action Control center at the bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header display of target vibing words
            Text(
                text = "CURRENT VIBESOME PROMPT",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 1.5.sp
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "\"${wallpaper.prompt}\"",
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Two prominent functional buttons as requested by instructions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Button 1: Download to media files
                Button(
                    onClick = onDownload,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("download_button"),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Share, // download icon representation
                        contentDescription = null,
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (wallpaper.isSavedToGallery) "DOWNLOADED" else "DOWNLOAD",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    )
                }

                // Button 2: Remix (style reference injection)
                Button(
                    onClick = onRemix,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("remix_button"),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Build, // remix icon representation
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "REMIX",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}

// --- CORE COMPONENT: EMBELLISHED CUSTOM BOTTOM NAV ---

@Composable
fun CustomBottomNavigationBar(
    activeTab: String,
    onTabSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                icon = Icons.Default.Add,
                title = "Create",
                selected = activeTab == "create",
                onClick = { onTabSelected("create") }
            )
            BottomNavItem(
                icon = Icons.Default.Favorite,
                title = "Gallery",
                selected = activeTab == "gallery",
                onClick = { onTabSelected("gallery") }
            )
            BottomNavItem(
                icon = Icons.Default.Settings,
                title = "Options",
                selected = activeTab == "settings",
                onClick = { onTabSelected("settings") }
            )
        }
    }
}

@Composable
fun BottomNavItem(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title.uppercase(),
                style = TextStyle(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}
