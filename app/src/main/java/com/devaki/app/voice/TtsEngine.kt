package com.devaki.app.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

private const val TAG = "DEVAKI"

class TtsEngine(
    private val context: Context,
    private val onStartSpeaking: () -> Unit = {},
    private val onDoneSpeaking: () -> Unit = {}
) {
    private var tts: TextToSpeech? = null
    private var ready = false

    fun init(onReady: () -> Unit = {}) {
        tts = TextToSpeech(context) { status ->
            ready = (status == TextToSpeech.SUCCESS)
            if (ready) {
                tts?.language = Locale.US
                // Try to pick a pleasant offline English voice if available
                selectEnglishOfflineVoice()
                // Gentle defaults; we'll also randomize a bit per utterance
                tts?.setSpeechRate(0.95f)
                tts?.setPitch(1.03f)
                Log.d(TAG, "TTS initialized successfully")
                onReady()
            } else {
                Log.e(TAG, "TTS initialization failed")
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { 
                Log.d(TAG, "TTS started speaking")
                onStartSpeaking() 
            }
            override fun onDone(id: String?) { 
                Log.d(TAG, "TTS finished speaking")
                onDoneSpeaking() 
            }
            override fun onError(id: String?) { 
                Log.e(TAG, "TTS error")
                onDoneSpeaking() 
            }
        })
    }

    /** Avoids explicit Voice types to dodge import/SDK quirks */
    private fun selectEnglishOfflineVoice() {
        val engine = tts ?: return
        val voices = engine.voices ?: return
        // Prefer offline English (US/GB/IN). If none, first English. If none, keep default.
        val preferredLocales = listOf(Locale.US, Locale.UK, Locale("en", "IN"))

        // 1) offline + preferred locale
        for (loc in preferredLocales) {
            val v = voices.firstOrNull { v ->
                v.locale == loc && !v.isNetworkConnectionRequired
            }
            if (v != null) { 
                engine.voice = v
                Log.d(TAG, "Selected voice: ${v.name}")
                return 
            }
        }
        // 2) any offline English
        voices.firstOrNull { v -> v.locale.language == "en" && !v.isNetworkConnectionRequired }
            ?.let { 
                engine.voice = it 
                Log.d(TAG, "Selected offline English voice: ${it.name}")
            } ?: run {
                // 3) any English
                voices.firstOrNull { v -> v.locale.language == "en" }
                    ?.let { 
                        engine.voice = it
                        Log.d(TAG, "Selected English voice: ${it.name}")
                    }
            }
    }

    fun speak(text: String) {
        val friendly = Humanizer.conversationalize(text)
        val ssml = Humanizer.toSsml(friendly)
        Log.d(TAG, "Speaking: $friendly")
        speakSsml(ssml)
    }

    fun speakSsml(ssml: String) {
        if (!ready) {
            Log.w(TAG, "TTS not ready, skipping speak")
            return
        }
        // Subtle variation per line
        tts?.setSpeechRate(listOf(0.92f, 0.95f, 0.98f, 1.0f).random())
        tts?.setPitch(listOf(0.98f, 1.0f, 1.03f).random())
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "DEVAKI_${System.nanoTime()}")
        }
        tts?.speak(ssml, TextToSpeech.QUEUE_FLUSH, params, "DEVAKI_${System.nanoTime()}")
    }

    fun shutdown() { 
        tts?.shutdown()
        Log.d(TAG, "TTS shutdown")
    }
}
