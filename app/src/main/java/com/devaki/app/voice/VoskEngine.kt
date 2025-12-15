package com.devaki.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException

private const val TAG = "DEVAKI"

class VoskEngine(private val context: Context) {
    
    companion object {
        private const val SAMPLE_RATE = 16000f
        // Wake phrase aliases - tolerant matching (easier to recognize)
        private val WAKE_ALIASES = listOf(
            // Short & clear wake words (easier to detect)
            "hey robot", "hey bot", "robot", 
            "okay robot", "ok robot",
            // Original variants
            "hey dev", "hey dave", "hey deb", "hey dove", "hey dead", "aid dev",
            // Additional variants
            "dev", "devaki", "hey devaki",
            "listen", "wake up"
        )
        private const val SILENCE_TIMEOUT_MS = 2000L // Wait 2 seconds of silence before finalizing
    }
    
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var agc: AutomaticGainControl? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRecording = false
    private var isSpeaking = false
    
    var onWake: (() -> Unit)? = null
    var onPartial: ((String) -> Unit)? = null
    var onFinal: ((String) -> Unit)? = null
    
    private var wakeHot = false
    
    // Prevent barge-in while robot is speaking
    fun setSpeaking(speaking: Boolean) {
        isSpeaking = speaking
        Log.d(TAG, "Vosk setSpeaking: $speaking")
    }
    
    var onError: ((String) -> Unit)? = null
    
    fun initialize(onReady: () -> Unit) {
        scope.launch {
            try {
                // Load external model from app's external files directory
                // User must manually copy model to: Android/data/com.devaki.app/files/models/
                val appFiles = context.getExternalFilesDir(null)
                val modelDir = File(appFiles, "models/vosk-model-en-us-0.22-lgraph")
                
                if (!modelDir.exists()) {
                    val errorMsg = "Vosk model not found! Copy vosk-model-en-us-0.22-lgraph to: Android/data/com.devaki.app/files/models/"
                    Log.e(TAG, "Vosk model not found at: ${modelDir.absolutePath}")
                    Log.e(TAG, errorMsg)
                    withContext(Dispatchers.Main) {
                        onError?.invoke(errorMsg)
                    }
                    return@launch
                }
                
                Log.d(TAG, "Loading Vosk model from: ${modelDir.absolutePath}")
                model = Model(modelDir.absolutePath)
                recognizer = Recognizer(model, SAMPLE_RATE)
                Log.d(TAG, "Vosk model loaded successfully")
                
                withContext(Dispatchers.Main) {
                    onReady()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Vosk model", e)
                withContext(Dispatchers.Main) {
                    onError?.invoke("Failed to load Vosk model: ${e.message}")
                }
            }
        }
    }
    
    private fun copyAssets(path: String, destDir: File) {
        val assets = context.assets
        val files = assets.list(path) ?: emptyArray()
        
        if (files.isEmpty()) {
            // It's a file
            destDir.parentFile?.mkdirs()
            assets.open(path).use { input ->
                destDir.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            // It's a directory
            destDir.mkdirs()
            for (file in files) {
                copyAssets("$path/$file", File(destDir, file))
            }
        }
    }
    
    fun startListening() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission not granted")
            return
        }
        
        scope.launch {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE.toInt(),
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                
                // Use VOICE_RECOGNITION for better sensitivity and noise rejection
                audioRecord = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE.toInt())
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize * 2) // Larger buffer for stability
                    .build()
                
                // Enable Automatic Gain Control for better mic sensitivity
                audioRecord?.audioSessionId?.let { sessionId ->
                    if (AutomaticGainControl.isAvailable()) {
                        agc = AutomaticGainControl.create(sessionId)
                        agc?.enabled = true
                        Log.d(TAG, "AGC enabled for better mic sensitivity")
                    } else {
                        Log.w(TAG, "AGC not available on this device")
                    }
                }
                
                audioRecord?.startRecording()
                isRecording = true
                Log.d(TAG, "Vosk listening started with VOICE_RECOGNITION source")
                
                val buffer = ShortArray(bufferSize)
                
                while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val byteBuffer = shortArrayToByteArray(buffer, read)
                        processAudio(byteBuffer)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in Vosk audio recording", e)
            }
        }
    }
    
    private fun processAudio(data: ByteArray) {
        // Ignore audio while robot is speaking (barge-in prevention)
        if (isSpeaking) return
        
        recognizer?.let { rec ->
            if (rec.acceptWaveForm(data, data.size)) {
                // Final result
                val result = rec.result
                handleFinalResult(result)
            } else {
                // Partial result
                val partial = rec.partialResult
                handlePartialResult(partial)
            }
        }
    }
    
    private fun handlePartialResult(jsonStr: String) {
        try {
            val json = JsonParser.parseString(jsonStr).asJsonObject
            val partial = json.get("partial")?.asString?.lowercase() ?: ""
            
            if (partial.isNotEmpty()) {
                Log.d(TAG, "Vosk partial: '$partial' (wakeHot=$wakeHot)")
            }
            
            // Check for wake aliases
            if (!wakeHot && WAKE_ALIASES.any { partial.contains(it) }) {
                wakeHot = true
                Log.d(TAG, "✓ Wake detected: $partial")
                scope.launch(Dispatchers.Main) {
                    onWake?.invoke()
                }
            }
            
            // Check for immediate commands like "stop" even without wake
            if (!wakeHot && partial.contains("stop")) {
                wakeHot = true // Treat as wakeup
                Log.d(TAG, "Immediate stop command detected")
                scope.launch(Dispatchers.Main) {
                    onWake?.invoke()
                }
            }
            
            scope.launch(Dispatchers.Main) {
                onPartial?.invoke(partial)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing partial result", e)
        }
    }
    
    private fun handleFinalResult(jsonStr: String) {
        try {
            val json = JsonParser.parseString(jsonStr).asJsonObject
            val text = json.get("text")?.asString?.trim() ?: ""
            
            Log.d(TAG, "Vosk final: '$text' (wakeHot=$wakeHot)")
            
            if (wakeHot && text.isNotEmpty()) {
                wakeHot = false
                Log.d(TAG, "✓ Final command received: '$text'")
                scope.launch(Dispatchers.Main) {
                    onFinal?.invoke(text)
                }
            } else if (wakeHot) {
                Log.d(TAG, "Waiting for command... (got empty text)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing final result", e)
        }
    }
    
    private fun shortArrayToByteArray(shorts: ShortArray, length: Int): ByteArray {
        val bytes = ByteArray(length * 2)
        for (i in 0 until length) {
            bytes[i * 2] = (shorts[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((shorts[i].toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }
    
    fun stopListening() {
        isRecording = false
        agc?.release()
        agc = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        Log.d(TAG, "Vosk listening stopped")
    }
    
    fun release() {
        stopListening()
        recognizer?.close()
        model?.close()
        scope.cancel()
        Log.d(TAG, "Vosk engine released")
    }
}































































































































































