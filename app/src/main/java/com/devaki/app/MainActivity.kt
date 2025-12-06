package com.devaki.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.devaki.app.brain.IntentRouter
import com.devaki.app.control.FollowController
import com.devaki.app.data.SettingsStore
import com.devaki.app.databinding.ActivityMainBinding
import com.devaki.app.llm.GeminiClient
import com.devaki.app.net.RateLimiter
import com.devaki.app.net.TcpClient
import com.devaki.app.ui.FaceView
import com.devaki.app.vision.FaceTracker
import com.devaki.app.voice.Humanizer
import com.devaki.app.voice.TtsEngine
import com.devaki.app.voice.VoskEngine
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private const val TAG = "DEVAKI"

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val REQUEST_PERMISSIONS = 1001
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }.toTypedArray()
    }
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsStore
    
    // Network
    private lateinit var tcpClient: TcpClient
    private val speedRateLimiter = RateLimiter(60) // 60ms = ~16Hz
    
    // Voice
    private lateinit var voskEngine: VoskEngine
    private lateinit var ttsEngine: TtsEngine
    private val intentRouter = IntentRouter()
    
    // Vision
    private lateinit var faceTracker: FaceTracker
    private val followController = FollowController()
    private var followModeEnabled = false
    private var baseSpeed = 180
    
    // LLM
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private var llmUrl = SettingsStore.DEFAULT_LLM_URL
    private var geminiClient: GeminiClient? = null
    private var geminiEnabled = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Keep screen on
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        settings = SettingsStore(this)
        
        TODO: Remove this after adding settings UI - temporary hardcoded Gemini setup
        lifecycleScope.launch {
            Uncomment and add your API key from https://aistudio.google.com/app/apikey
            settings.setGeminiEnabled(true)
            settings.setGeminiApiKey("AIzaSyDgV86kLkbnxq6WHpjNJMZRf4Sc-CJZGcE")
        }
        
        if (allPermissionsGranted()) {
            initializeComponents()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS)
        }
    }
    
    private fun initializeComponents() {
        // Load settings
        lifecycleScope.launch {
            settings.baseSpeed.collect { baseSpeed = it; binding.sliderSpeed.value = it.toFloat() }
        }
        lifecycleScope.launch {
            settings.followEnabled.collect { followModeEnabled = it; binding.switchFollow.isChecked = it }
        }
        lifecycleScope.launch {
            settings.llmUrl.collect { llmUrl = it }
        }
        lifecycleScope.launch {
            settings.geminiEnabled.collect { geminiEnabled = it }
        }
        lifecycleScope.launch {
            settings.geminiApiKey.collect { apiKey ->
                geminiClient = if (apiKey.isNotEmpty()) {
                    GeminiClient(apiKey)
                } else {
                    null
                }
            }
        }
        
        // TCP Client
        tcpClient = TcpClient()
        tcpClient.onConnectionChange = { connected, message ->
            runOnUiThread {
                binding.tvStatus.text = message
                binding.btnConnect.text = if (connected) getString(R.string.disconnect) else getString(R.string.connect)
            }
        }
        
        // Voice - TTS
        ttsEngine = TtsEngine(
            context = this,
            onStartSpeaking = {
                voskEngine.setSpeaking(true)
                binding.faceView.setState(FaceView.State.TALKING)
            },
            onDoneSpeaking = {
                voskEngine.setSpeaking(false)
                binding.faceView.setState(FaceView.State.IDLE)
            }
        )
        ttsEngine.init {
            Log.d(TAG, "TTS ready")
        }
        
        // Voice - Vosk STT
        voskEngine = VoskEngine(this)
        voskEngine.onWake = {
            binding.faceView.setState(FaceView.State.LISTENING)
            ttsEngine.speak("Yes?")
        }
        voskEngine.onPartial = { text ->
            Log.d(TAG, "Partial: $text")
        }
        voskEngine.onFinal = { text ->
            handleVoiceCommand(text)
        }
        voskEngine.initialize {
            voskEngine.startListening()
            Log.d(TAG, "Vosk ready and listening")
        }
        
        // Vision - Face Tracking
        if (allPermissionsGranted()) {
            faceTracker = FaceTracker(this, binding.previewView)
            faceTracker.onFaceDetected = { face, width, height ->
                if (followModeEnabled && face != null) {
                    handleFollowMode(face, width, height)
                }
            }
            faceTracker.startCamera()
        }
        
        setupUI()
    }
    
    private fun setupUI() {
        // Connect button
        binding.btnConnect.setOnClickListener {
            if (tcpClient.isConnected()) {
                disconnect()
            } else {
                connect()
            }
        }
        
        // Manual control buttons
        binding.btnForward.setOnClickListener { 
            sendCommand("FWD")
            binding.faceView.setState(FaceView.State.MOVING)
        }
        binding.btnBack.setOnClickListener { 
            sendCommand("BACK")
            binding.faceView.setState(FaceView.State.MOVING)
        }
        binding.btnLeft.setOnClickListener { 
            sendCommand("LEFT")
            binding.faceView.setState(FaceView.State.MOVING)
        }
        binding.btnRight.setOnClickListener { 
            sendCommand("RIGHT")
            binding.faceView.setState(FaceView.State.MOVING)
        }
        binding.btnStop.setOnClickListener { 
            sendCommand("STOP")
            binding.faceView.setState(FaceView.State.IDLE)
        }
        
        // Follow mode toggle
        binding.switchFollow.setOnCheckedChangeListener { _, isChecked ->
            followModeEnabled = isChecked
            lifecycleScope.launch {
                settings.setFollowEnabled(isChecked)
            }
            if (!isChecked) {
                sendCommand("STOP")
                binding.faceView.setState(FaceView.State.IDLE)
            }
        }
        
        // Speed slider
        binding.sliderSpeed.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                baseSpeed = value.toInt()
                binding.tvSpeedValue.text = baseSpeed.toString()
                lifecycleScope.launch {
                    settings.setBaseSpeed(baseSpeed)
                }
            }
        }
        
        // Simulator checkbox
        binding.cbWifiSim.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                settings.setSimulator(isChecked)
            }
            if (isChecked) {
                // Suggest emulator loopback
                binding.etHost.setText("10.0.2.2")
            }
        }
        
        // Save host/port on change
        binding.etHost.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                lifecycleScope.launch {
                    settings.setHost(binding.etHost.text.toString())
                }
            }
        }
        binding.etPort.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                lifecycleScope.launch {
                    settings.setPort(binding.etPort.text.toString().toIntOrNull() ?: 9000)
                }
            }
        }
    }
    
    private fun connect() {
        val host = binding.etHost.text.toString()
        val port = binding.etPort.text.toString().toIntOrNull() ?: 9000
        
        lifecycleScope.launch {
            tcpClient.connect(host, port)
        }
    }
    
    private fun disconnect() {
        tcpClient.close()
        binding.tvStatus.text = getString(R.string.disconnected)
        binding.btnConnect.text = getString(R.string.connect)
    }
    
    private fun sendCommand(command: String) {
        lifecycleScope.launch {
            tcpClient.sendLine(command)
        }
    }
    
    private fun handleFollowMode(face: com.google.mlkit.vision.face.Face, imgWidth: Int, imgHeight: Int) {
        if (!speedRateLimiter.shouldSend("follow")) return
        
        val faceCenterX = face.boundingBox.centerX().toFloat()
        val imgCenterX = imgWidth / 2f
        
        val (leftSpeed, rightSpeed) = followController.computeSpeed(
            faceCenterX, imgCenterX, imgWidth, baseSpeed
        )
        
        sendCommand("SPEED $leftSpeed $rightSpeed")
        binding.faceView.setState(FaceView.State.MOVING)
    }
    
    private fun handleVoiceCommand(text: String) {
        Log.d(TAG, "Voice command: $text")
        
        val intent = intentRouter.route(text)
        
        if (intent != null) {
            // Handle robot control intent
            intent.command?.let { cmd ->
                sendCommand(cmd)
                when (cmd) {
                    "STOP" -> binding.faceView.setState(FaceView.State.IDLE)
                    else -> binding.faceView.setState(FaceView.State.MOVING)
                }
            }
            
            intent.followMode?.let { follow ->
                binding.switchFollow.isChecked = follow
            }
            
            intent.faceState?.let { state ->
                binding.faceView.setState(state)
                if (state == FaceView.State.SLEEPING) {
                    binding.switchFollow.isChecked = false
                }
            }
            
            intent.speedChange.let { change ->
                if (change != 0) {
                    val newSpeed = (baseSpeed + change).coerceIn(0, 255)
                    binding.sliderSpeed.value = newSpeed.toFloat()
                }
            }
            
            // Speak confirmation
            if (intent.confirmation.isNotEmpty()) {
                ttsEngine.speak(intent.confirmation)
            }
        } else {
            // No intent matched - try LLM
            callLLM(text)
        }
    }
    
    private fun callLLM(query: String) {
        if (llmUrl.isEmpty()) {
            ttsEngine.speak("I'm offline right now.")
            return
        }
        
        // Play thinking cue for longer queries
        if (query.length > 20) {
            ttsEngine.speak(Humanizer.thinkingCue())
        }
        
        lifecycleScope.launch {
            try {
                val response = queryLLM(query)
                ttsEngine.speak(response)
            } catch (e: Exception) {
                Log.e(TAG, "LLM query failed", e)
                ttsEngine.speak("Sorry, I couldn't process that.")
            }
        }
    }
    
    private suspend fun queryLLM(query: String): String = withContext(Dispatchers.IO) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val systemPrompt = """You are DEV, a friendly and empathetic robot companion. Today is $today.
            
            You can:
            - Have natural conversations and share feelings
            - Discuss news, sports, entertainment, and current events
            - Offer life advice, motivation, and emotional support
            - Chat casually about anything
            - Use humor and personality
            
            Keep responses conversational (2-3 sentences max for voice). Be warm, supportive, and genuine.""".trimIndent()
        
        // Try Gemini first if enabled (for any query, not just web searches)
        if (geminiEnabled && geminiClient != null) {
            Log.d(TAG, "Using Gemini for web-grounded query")
            val result = geminiClient?.query(query, systemPrompt)
            if (result?.isSuccess == true) {
                return@withContext result.getOrNull() ?: "I couldn't find that information."
            }
            Log.w(TAG, "Gemini failed, falling back to Ollama")
        }
        
        // Fallback to Ollama
        val json = """
            {
                "model": "phi3:mini",
                "prompt": "$query",
                "system": "$systemPrompt",
                "stream": false
            }
        """.trimIndent()
        
        val requestBody = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$llmUrl/api/generate")
            .post(requestBody)
            .build()
        
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        
        val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
        jsonResponse.get("response")?.asString ?: "I don't know."
    }
    
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (allPermissionsGranted()) {
                initializeComponents()
            } else {
                Snackbar.make(
                    binding.root,
                    "Permissions required for voice and camera features",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        tcpClient.release()
        if (::faceTracker.isInitialized) faceTracker.release()
        if (::voskEngine.isInitialized) voskEngine.release()
        if (::ttsEngine.isInitialized) ttsEngine.shutdown()
    }
}
