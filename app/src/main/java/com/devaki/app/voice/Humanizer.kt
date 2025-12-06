package com.devaki.app.voice

import kotlin.random.Random

object Humanizer {
    // Light rephrasing + subtle fillers/backchannels
    fun conversationalize(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return "Hmm, I didn't catch that."
        val openers = listOf("okay", "got it", "sure", "alright", "mm-hmm")
        val opener = if (t.length > 25 && Random.nextFloat() < 0.35f) openers.random() + ", " else ""
        return opener + t
            .replace(Regex("\\s+"), " ")
            .replace("i am", "I'm", ignoreCase = true)
            .replace("do not", "don't", ignoreCase = true)
            .replace("cannot", "can't", ignoreCase = true)
            .replace("it is", "it's", ignoreCase = true)
            .replace("we are", "we're", ignoreCase = true)
            .replace("they are", "they're", ignoreCase = true)
    }

    // Wrap in SSML with natural pauses/emphasis
    fun toSsml(text: String): String {
        val clean = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        // Add gentle clause breaks
        val withBreaks = clean
            .replace(Regex(",\\s*"), ", <break time=\"200ms\"/> ")
            .replace(Regex("\\.\\s*"), ". <break time=\"300ms\"/> ")
            .replace(Regex("\\?\\s*"), "? <break time=\"250ms\"/> ")

        // Slight randomness makes it feel alive
        val ratePct = listOf(92, 95, 98, 100, 102).random()
        val pitchSemis = listOf(-1, 0, 0, 1).random() // biased to neutral

        return """
            <speak>
              <prosody rate="${ratePct}%" pitch="${pitchSemis}st">
                $withBreaks
              </prosody>
            </speak>
        """.trimIndent()
    }

    // Tiny "thinking" filler for long LLM replies
    fun thinkingCue(): String = listOf("hmm...", "let me think...", "one sec...").random()
}
