package com.example.ui

import android.app.Application
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.ImageConfig
import com.example.data.api.Part
import com.example.data.api.PrebuiltVoiceConfig
import com.example.data.api.RetrofitClient
import com.example.data.api.SpeechConfig
import com.example.data.api.ThinkingConfig
import com.example.data.api.VoiceConfig
import com.example.data.database.AppDatabase
import com.example.data.database.HistoryEntity
import com.example.data.repository.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed interface AuthUiState {
    object SignedOut : AuthUiState
    object Loading : AuthUiState
    data class SignedIn(val email: String, val name: String) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class CaptionViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = AppDatabase.getDatabase(context)
    private val repository = HistoryRepository(database.historyDao())

    // UI States
    private val _authUiState = MutableStateFlow<AuthUiState>(AuthUiState.SignedOut)
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    // History stream
    val historyState: StateFlow<List<HistoryEntity>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Token / Credit state for generation (integrates with AdMob)
    private val _credits = MutableStateFlow(5)
    val credits: StateFlow<Int> = _credits.asStateFlow()

    private val _unlimitedCredits = MutableStateFlow(false)
    val unlimitedCredits: StateFlow<Boolean> = _unlimitedCredits.asStateFlow()

    // Caption State
    private val _captionPrompt = MutableStateFlow("")
    val captionPrompt: StateFlow<String> = _captionPrompt.asStateFlow()

    private val _captionMood = MutableStateFlow("Creative")
    val captionMood: StateFlow<String> = _captionMood.asStateFlow()

    private val _captionLength = MutableStateFlow("Medium")
    val captionLength: StateFlow<String> = _captionLength.asStateFlow()

    private val _isThinkingMode = MutableStateFlow(false)
    val isThinkingMode: StateFlow<Boolean> = _isThinkingMode.asStateFlow()

    private val _captionResult = MutableStateFlow("")
    val captionResult: StateFlow<String> = _captionResult.asStateFlow()

    private val _isCaptionLoading = MutableStateFlow(false)
    val isCaptionLoading: StateFlow<Boolean> = _isCaptionLoading.asStateFlow()

    // Hashtags State
    private val _hashtagPrompt = MutableStateFlow("")
    val hashtagPrompt: StateFlow<String> = _hashtagPrompt.asStateFlow()

    private val _hashtagPlatform = MutableStateFlow("All")
    val hashtagPlatform: StateFlow<String> = _hashtagPlatform.asStateFlow()

    private val _hashtagResult = MutableStateFlow("")
    val hashtagResult: StateFlow<String> = _hashtagResult.asStateFlow()

    private val _isHashtagLoading = MutableStateFlow(false)
    val isHashtagLoading: StateFlow<Boolean> = _isHashtagLoading.asStateFlow()

    // Image Generation State
    private val _imagePrompt = MutableStateFlow("")
    val imagePrompt: StateFlow<String> = _imagePrompt.asStateFlow()

    private val _imageResultB64 = MutableStateFlow<String?>(null)
    val imageResultB64: StateFlow<String?> = _imageResultB64.asStateFlow()

    private val _isImageLoading = MutableStateFlow(false)
    val isImageLoading: StateFlow<Boolean> = _isImageLoading.asStateFlow()

    // Audio/TTS State
    private val _isTtsLoading = MutableStateFlow(false)
    val isTtsLoading: StateFlow<Boolean> = _isTtsLoading.asStateFlow()

    private val _isPlayingAudio = MutableStateFlow(false)
    val isPlayingAudio: StateFlow<Boolean> = _isPlayingAudio.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var tempAudioFile: File? = null

    init {
        // Automatically set a mock user if Google Play Services Auth is missing
        // This lets the user inspect the application without blocking setup
        checkUserSession()
    }

    private fun checkUserSession() {
        // For testing / simulation out of the box, we sign in a default mock user
        // The user can sign out/in inside the Settings or Auth screen
        _authUiState.value = AuthUiState.SignedIn("user@aicaptionpro.com", "AI Creator")
    }

    fun signIn(email: String, name: String) {
        _authUiState.value = AuthUiState.Loading
        viewModelScope.launch {
            if (email.isNotBlank() && email.contains("@")) {
                _authUiState.value = AuthUiState.SignedIn(email, name.ifBlank { "Creator" })
            } else {
                _authUiState.value = AuthUiState.Error("Please enter a valid email address.")
            }
        }
    }

    fun signOut() {
        _authUiState.value = AuthUiState.SignedOut
    }

    fun addCredits(amount: Int) {
        _credits.value = _credits.value + amount
    }

    fun setUnlimitedCredits(enabled: Boolean) {
        _unlimitedCredits.value = enabled
    }

    fun updateCaptionPrompt(prompt: String) {
        _captionPrompt.value = prompt
    }

    fun updateCaptionResult(result: String) {
        _captionResult.value = result
    }

    fun updateCaptionMood(mood: String) {
        _captionMood.value = mood
    }

    fun updateCaptionLength(length: String) {
        _captionLength.value = length
    }

    fun toggleThinkingMode(enabled: Boolean) {
        _isThinkingMode.value = enabled
    }

    fun updateHashtagPrompt(prompt: String) {
        _hashtagPrompt.value = prompt
    }

    fun updateHashtagPlatform(platform: String) {
        _hashtagPlatform.value = platform
    }

    fun updateImagePrompt(prompt: String) {
        _imagePrompt.value = prompt
    }

    private fun consumeCredit(): Boolean {
        if (_unlimitedCredits.value) return true
        if (_credits.value > 0) {
            _credits.value = _credits.value - 1
            return true
        }
        return false
    }

    // --- Caption Generation (Standard & High Thinking) ---
    fun generateCaption() {
        val prompt = _captionPrompt.value.trim()
        if (prompt.isEmpty()) return

        if (!consumeCredit()) {
            _captionResult.value = "Out of credits! Please watch a Rewarded Ad in the Settings panel to earn 5 credits, or enable Unlimited Credits."
            return
        }

        _isCaptionLoading.value = true
        _captionResult.value = ""

        viewModelScope.launch {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isBlank()) {
                    _captionResult.value = "API Key is missing! Please configure GEMINI_API_KEY in your Secrets panel inside Google AI Studio."
                    _isCaptionLoading.value = false
                    return@launch
                }

                val useHighThinking = _isThinkingMode.value
                val modelName = if (useHighThinking) "gemini-3.1-pro-preview" else "gemini-3.5-flash"

                val systemInst = "You are AI Caption Pro, a social media copywriter. Generate 3 engaging, polished Instagram captions based on the prompt. Mood: ${_captionMood.value}, Length: ${_captionLength.value}. Ensure a creative layout, appropriate emojis, spacing, and neat structure."

                val requestPrompt = "Prompt topic/image description: $prompt"

                val generationConfig = if (useHighThinking) {
                    // High thinking config
                    GenerationConfig(
                        temperature = null, // Temperature must be null or omitted when thinking level is High
                        thinkingConfig = ThinkingConfig(thinkingLevel = "HIGH")
                    )
                } else {
                    GenerationConfig(temperature = 0.8f)
                }

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = requestPrompt)))),
                    generationConfig = generationConfig,
                    systemInstruction = Content(parts = listOf(Part(text = systemInst)))
                )

                val response = RetrofitClient.service.generateContent(modelName, apiKey, request)
                val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Could not generate caption. Please try again."

                _captionResult.value = textResponse

                // Save to history
                repository.insert(
                    HistoryEntity(
                        type = "caption",
                        prompt = "[$modelName / ${_captionMood.value}] $prompt",
                        result = textResponse,
                        platform = "instagram"
                    )
                )

            } catch (e: Exception) {
                Log.e("CaptionViewModel", "Error generating caption", e)
                _captionResult.value = "Error generating caption: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                _isCaptionLoading.value = false
            }
        }
    }

    // --- Hashtags Generation ---
    fun generateHashtags() {
        val prompt = _hashtagPrompt.value.trim()
        if (prompt.isEmpty()) return

        if (!consumeCredit()) {
            _hashtagResult.value = "Out of credits! Please watch a Rewarded Ad in the Settings panel to earn 5 credits."
            return
        }

        _isHashtagLoading.value = true
        _hashtagResult.value = ""

        viewModelScope.launch {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isBlank()) {
                    _hashtagResult.value = "API Key is missing! Please configure GEMINI_API_KEY in your Secrets panel inside Google AI Studio."
                    _isHashtagLoading.value = false
                    return@launch
                }

                val systemInst = "You are AI Caption Pro, an expert hashtags strategist. Generate highly relevant, trending hashtags for the platform: ${_hashtagPlatform.value}. Topic or description: $prompt. Group them by category (e.g. Popular, Niche, Location/Industry) and list them clearly so they can be copied easily."

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = "Topic: $prompt")))),
                    generationConfig = GenerationConfig(temperature = 0.7f),
                    systemInstruction = Content(parts = listOf(Part(text = systemInst)))
                )

                val response = RetrofitClient.service.generateContent("gemini-3.5-flash", apiKey, request)
                val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Could not generate hashtags. Please try again."

                _hashtagResult.value = textResponse

                // Save to history
                repository.insert(
                    HistoryEntity(
                        type = "hashtag",
                        prompt = "[${_hashtagPlatform.value}] $prompt",
                        result = textResponse,
                        platform = _hashtagPlatform.value.lowercase()
                    )
                )

            } catch (e: Exception) {
                Log.e("CaptionViewModel", "Error generating hashtags", e)
                _hashtagResult.value = "Error generating hashtags: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                _isHashtagLoading.value = false
            }
        }
    }

    // --- Multimodal Image Generation (gemini-3.1-flash-image-preview) ---
    fun generateImage() {
        val prompt = _imagePrompt.value.trim()
        if (prompt.isEmpty()) return

        if (!consumeCredit()) {
            _imageResultB64.value = null
            return
        }

        _isImageLoading.value = true
        _imageResultB64.value = null

        viewModelScope.launch {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isBlank()) {
                    _imageResultB64.value = null
                    _isImageLoading.value = false
                    return@launch
                }

                // Call gemini-3.1-flash-image-preview as requested
                val modelName = "gemini-3.1-flash-image-preview"
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    generationConfig = GenerationConfig(
                        imageConfig = ImageConfig(aspectRatio = "1:1", imageSize = "1K"),
                        responseModalities = listOf("TEXT", "IMAGE")
                    )
                )

                val response = RetrofitClient.service.generateContent(modelName, apiKey, request)
                
                // Find parts containing inlineData (the image bytes in base64 format)
                val imagePart = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull {
                    it.inlineData != null && it.inlineData.mimeType.startsWith("image/")
                }

                val b64Data = imagePart?.inlineData?.data
                if (b64Data != null) {
                    _imageResultB64.value = b64Data
                    
                    // Save log in history
                    repository.insert(
                        HistoryEntity(
                            type = "image",
                            prompt = prompt,
                            result = "Generated complete post visual using $modelName",
                            platform = "all"
                        )
                    )
                } else {
                    Log.e("CaptionViewModel", "No image part found in response candidates.")
                }

            } catch (e: Exception) {
                Log.e("CaptionViewModel", "Error generating image", e)
            } finally {
                _isImageLoading.value = false
            }
        }
    }

    // --- Text-to-Speech Generation (gemini-3.1-flash-tts-preview) ---
    fun generateTts(textToSpeak: String) {
        if (textToSpeak.isBlank()) return

        _isTtsLoading.value = true
        stopAudio()

        viewModelScope.launch {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isBlank()) {
                    _isTtsLoading.value = false
                    return@launch
                }

                // TTS model specified: gemini-3.1-flash-tts-preview
                val modelName = "gemini-3.1-flash-tts-preview"
                
                val prompt = "Read this caption aloud clearly with an enthusiastic, professional narrator voice, do not add any surrounding texts or conversational filler: $textToSpeak"

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    generationConfig = GenerationConfig(
                        responseModalities = listOf("AUDIO"),
                        speechConfig = SpeechConfig(
                            voiceConfig = VoiceConfig(
                                prebuiltVoiceConfig = PrebuiltVoiceConfig(voiceName = "Kore")
                            )
                        )
                    )
                )

                val response = RetrofitClient.service.generateContent(modelName, apiKey, request)
                
                val audioPart = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull {
                    it.inlineData != null && it.inlineData.mimeType.startsWith("audio/")
                }

                val audioB64 = audioPart?.inlineData?.data
                if (audioB64 != null) {
                    playB64Audio(audioB64)
                } else {
                    Log.e("CaptionViewModel", "No audio inline data returned from TTS model")
                }

            } catch (e: Exception) {
                Log.e("CaptionViewModel", "Error generating TTS", e)
            } finally {
                _isTtsLoading.value = false
            }
        }
    }

    private suspend fun playB64Audio(base64Audio: String) = withContext(Dispatchers.IO) {
        try {
            val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
            tempAudioFile = File.createTempFile("gemini_tts", ".mp3", context.cacheDir).apply {
                deleteOnExit()
            }
            FileOutputStream(tempAudioFile).use { fos ->
                fos.write(audioBytes)
                fos.flush()
            }

            withContext(Dispatchers.Main) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(tempAudioFile!!.absolutePath)
                    prepare()
                    setOnCompletionListener {
                        _isPlayingAudio.value = false
                        stopAudio()
                    }
                    start()
                }
                _isPlayingAudio.value = true
            }
        } catch (e: Exception) {
            Log.e("CaptionViewModel", "Error playing decoded audio", e)
        }
    }

    fun stopAudio() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e("CaptionViewModel", "Error releasing media player", e)
        } finally {
            mediaPlayer = null
            _isPlayingAudio.value = false
        }
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clear()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
        try {
            tempAudioFile?.delete()
        } catch (e: Exception) {
            // Safe ignore
        }
    }
}
