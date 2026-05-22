package com.example.viewmodel

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.BuildConfig
import com.example.api.*
import com.example.db.WallpaperDatabase
import com.example.db.WallpaperItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface GenerationState {
    object Idle : GenerationState
    data class Generating(val progressText: String) : GenerationState
    object Success : GenerationState
    data class Error(val message: String) : GenerationState
}

class WallpaperViewModel(private val context: Context) : ViewModel() {

    // Database
    private val database = Room.databaseBuilder(
        context.applicationContext,
        WallpaperDatabase::class.java,
        "vibe_wallpapers.db"
    ).build()

    private val wallpaperDao = database.wallpaperDao()

    // Expose all wallpapers reactively
    val allWallpapers: StateFlow<List<WallpaperItem>> = wallpaperDao.getAllWallpapers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current inputs & states
    val currentVibeQuery = MutableStateFlow("")
    val referenceWallpaper = MutableStateFlow<WallpaperItem?>(null)
    val currentBatch = MutableStateFlow<List<WallpaperItem>>(listOf())
    
    val currentTab = MutableStateFlow("create") // "create", "gallery", "settings"
    val generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)

    // Config options
    val apiKeyField = MutableStateFlow(BuildConfig.GEMINI_API_KEY)
    val responseResolution = MutableStateFlow("1K") // options "1K", "512px"

    // Vibe Preset/Quick Prompts
    val vibePresets = listOf(
        "rainy cyberpunk lo-fi",
        "minimalist pastel sand",
        "cosmic retro wave",
        "neon jungle midnight",
        "ethereal cloud dream",
        "brutalist steel concretescape"
    )

    init {
        // Log status
        if (apiKeyField.value.isEmpty() || apiKeyField.value == "MY_GEMINI_API_KEY") {
            // Log that API Key is missing or default
        }
    }

    fun selectPreset(preset: String) {
        currentVibeQuery.value = preset
    }

    fun clearReferenceWallpaper() {
        referenceWallpaper.value = null
    }

    fun generateWallpapers(onFinish: () -> Unit = {}) {
        val vibe = currentVibeQuery.value.trim()
        if (vibe.isEmpty()) {
            generationState.value = GenerationState.Error("Please enter your desired vibe!")
            return
        }

        val key = apiKeyField.value.trim()
        if (key.isEmpty() || key == "MY_GEMINI_API_KEY") {
            generationState.value = GenerationState.Error("API Key is missing! Set it in the Options tab or the Secrets panel.")
            return
        }

        viewModelScope.launch {
            generationState.value = GenerationState.Generating("Sensing the vibe...")
            
            try {
                // Fetch reference image base64 if remixing
                val referenceBase64 = referenceWallpaper.value?.let { wp ->
                    try {
                        val file = File(wp.filePath)
                        if (file.exists()) {
                            Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }

                // We want 4 variations. Let's trigger 4 concurrent API requests!
                // We add slight variation modifiers to prompt text to make each image distinct.
                val variationModifiers = listOf(
                    "variation 1, cinematic rendering, detailed atmospheric mood, elegant composition",
                    "variation 2, artistic interpretation, high visual contrasting accents, beautiful style",
                    "variation 3, stylized depth, custom aesthetic framing, rich textures",
                    "variation 4, abstract wallpaper layout, clean minimalist visual balance"
                )

                generationState.value = GenerationState.Generating("Synthesizing visual variants...")

                val requests = (0..3).map { index ->
                    async(Dispatchers.IO) {
                        val variationPrompt = "$vibe. 9:16 clean smartphone wallpaper. ${variationModifiers[index]}"
                        
                        val parts = mutableListOf<Part>()
                        
                        // If remixing, inject reference image as first part
                        if (referenceBase64 != null) {
                            parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = referenceBase64)))
                            // Also adjust wording to denote remix / style similarity
                            parts.add(Part(text = "Generate a style remix of the reference image matching this vibe prompt: $variationPrompt"))
                        } else {
                            parts.add(Part(text = variationPrompt))
                        }

                        val req = GenerateContentRequest(
                            contents = listOf(Content(parts = parts)),
                            generationConfig = GenerationConfig(
                                imageConfig = ImageConfig(
                                    aspectRatio = "9:16",
                                    imageSize = responseResolution.value
                                ),
                                responseModalities = listOf("IMAGE"),
                                temperature = 1.0f // Higher temperature for visual creativity
                            )
                        )

                        try {
                            val response = RetrofitClient.service.generateImage(key, req)
                            val base64Img = response.candidates?.firstOrNull()
                                ?.content?.parts?.firstOrNull()?.inlineData?.data
                            if (base64Img != null) {
                                base64Img
                            } else {
                                throw Exception("API returned a response but no image data parts were found.")
                            }
                        } catch (e: Exception) {
                            throw Exception("Variation ${index + 1} failed: ${e.message}")
                        }
                    }
                }

                generationState.value = GenerationState.Generating("Rendering 9:16 canvases...")

                // Await all concurrently
                val base64Results = requests.awaitAll()

                // Save files to internal storage and Room persistence
                generationState.value = GenerationState.Generating("Saving to local gallery...")

                val savedItems = withContext(Dispatchers.IO) {
                    base64Results.mapIndexed { index, base64Str ->
                        val directory = File(context.filesDir, "wallpapers")
                        if (!directory.exists()) {
                            directory.mkdirs()
                        }
                        val file = File(directory, "wp_${System.currentTimeMillis()}_${index}.jpg")
                        val imageBytes = Base64.decode(base64Str, Base64.DEFAULT)
                        file.outputStream().use { fos ->
                            fos.write(imageBytes)
                        }

                        // Save to Room DB
                        val item = WallpaperItem(
                            filePath = file.absolutePath,
                            prompt = vibe,
                            isSavedToGallery = false,
                            isFavorite = false
                        )
                        val generatedId = wallpaperDao.insertWallpaper(item)
                        item.copy(id = generatedId.toInt())
                    }
                }

                currentBatch.value = savedItems
                generationState.value = GenerationState.Success
                onFinish()

            } catch (e: Exception) {
                generationState.value = GenerationState.Error(e.message ?: "An unexpected error occurred.")
            }
        }
    }

    fun downloadWallpaperToPublicGallery(
        item: WallpaperItem,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sourceFile = File(item.filePath)
                if (!sourceFile.exists()) {
                    withContext(Dispatchers.Main) { onError("Source file not found locally.") }
                    return@launch
                }

                val contentResolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "VibeWall_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VibeWallpapers")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    contentResolver.openOutputStream(uri).use { out ->
                        if (out != null) {
                            sourceFile.inputStream().use { inp ->
                                inp.copyTo(out)
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        contentResolver.update(uri, contentValues, null, null)
                    }

                    // Mark as saved in local Room Database
                    val updatedItem = item.copy(isSavedToGallery = true)
                    wallpaperDao.updateWallpaper(updatedItem)

                    // Also update in active visual batch if present
                    val activeList = currentBatch.value.toMutableList()
                    val idx = activeList.indexOfFirst { it.id == item.id }
                    if (idx != -1) {
                        activeList[idx] = updatedItem
                        currentBatch.value = activeList
                    }

                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                } else {
                    withContext(Dispatchers.Main) { onError("Could not create public download slot in MediaStore.") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "Conversion error") }
            }
        }
    }

    fun toggleFavorite(item: WallpaperItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = item.copy(isFavorite = !item.isFavorite)
            wallpaperDao.updateWallpaper(updated)

            // Sync with current batch
            val activeList = currentBatch.value.toMutableList()
            val idx = activeList.indexOfFirst { it.id == item.id }
            if (idx != -1) {
                activeList[idx] = updated
                currentBatch.value = activeList
            }
        }
    }

    fun deleteWallpaper(item: WallpaperItem) {
        viewModelScope.launch(Dispatchers.IO) {
            // Delete actual file
            try {
                val f = File(item.filePath)
                if (f.exists()) f.delete()
            } catch (e: Exception) {
                // ignore
            }
            // Delete DB record
            wallpaperDao.deleteWallpaperById(item.id)

            // Remove from current batch if it belongs to it
            val activeList = currentBatch.value.toMutableList()
            val idx = activeList.indexOfFirst { it.id == item.id }
            if (idx != -1) {
                activeList.removeAt(idx)
                currentBatch.value = activeList
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            // Clear files
            try {
                val dir = File(context.filesDir, "wallpapers")
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            } catch (e: Exception) {
                // ignore
            }
            wallpaperDao.deleteAllWallpapers()
            currentBatch.value = emptyList()
            referenceWallpaper.value = null
        }
    }
}

class WallpaperViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WallpaperViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WallpaperViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
