package com.devaki.app.llm

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "DEV"

/**
 * Google Gemini API client with web grounding capability.
 * Uses googleSearchRetrieval tool to fetch real-time information from the web.
 */
class GeminiClient(private val apiKey: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta"
    
    /**
     * Query Gemini with web grounding enabled.
     * The model will automatically search the web when needed for real-time information.
     */
    suspend fun query(prompt: String, systemInstruction: String = ""): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildRequest(prompt, systemInstruction)
            
            val request = Request.Builder()
                .url("$baseUrl/models/gemini-2.0-flash-exp:generateContent?key=$apiKey")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            Log.d(TAG, "Gemini request: ${prompt.take(50)}...")
            
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini API error: ${response.code} - $body")
                return@withContext Result.failure(Exception("Gemini API error: ${response.code}"))
            }
            
            if (body.isNullOrEmpty()) {
                Log.e(TAG, "Empty response from Gemini")
                return@withContext Result.failure(Exception("Empty response from Gemini"))
            }
            
            val text = parseResponse(body)
            if (text.isEmpty()) {
                Log.e(TAG, "No text in Gemini response")
                return@withContext Result.failure(Exception("No text in response"))
            }
            
            Log.d(TAG, "Gemini response: ${text.take(100)}...")
            Result.success(text)
            
        } catch (e: Exception) {
            Log.e(TAG, "Gemini query failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Build the Gemini API request with web grounding enabled.
     */
    private fun buildRequest(prompt: String, systemInstruction: String): String {
        val json = JsonObject().apply {
            // Add system instruction if provided
            if (systemInstruction.isNotEmpty()) {
                add("systemInstruction", JsonObject().apply {
                    add("parts", gson.toJsonTree(listOf(mapOf("text" to systemInstruction))))
                })
            }
            
            // User prompt
            add("contents", gson.toJsonTree(listOf(
                mapOf("parts" to listOf(mapOf("text" to prompt)))
            )))
            
            // Enable Google Search grounding
            add("tools", gson.toJsonTree(listOf(
                mapOf("googleSearchRetrieval" to mapOf(
                    "dynamicRetrievalConfig" to mapOf(
                        "mode" to "MODE_DYNAMIC",
                        "dynamicThreshold" to 0.3
                    )
                ))
            )))
            
            // Generation config for natural conversation
            add("generationConfig", JsonObject().apply {
                addProperty("temperature", 0.9)  // More creative and natural
                addProperty("topP", 0.95)
                addProperty("topK", 40)
                addProperty("maxOutputTokens", 150)  // 2-3 sentences for voice
            })
        }
        
        return gson.toJson(json)
    }
    
    /**
     * Parse Gemini response and extract text.
     */
    private fun parseResponse(responseBody: String): String {
        try {
            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            
            // Extract text from candidates[0].content.parts[0].text
            val candidates = jsonResponse.getAsJsonArray("candidates")
            if (candidates == null || candidates.size() == 0) {
                return ""
            }
            
            val content = candidates[0].asJsonObject.getAsJsonObject("content")
            val parts = content.getAsJsonArray("parts")
            
            if (parts == null || parts.size() == 0) {
                return ""
            }
            
            // Concatenate all text parts
            return parts.joinToString("\n") { part ->
                part.asJsonObject.get("text")?.asString ?: ""
            }.trim()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini response", e)
            return ""
        }
    }
    
    companion object {
        /**
         * Check if a query likely needs real-time web information.
         */
        fun needsWebSearch(query: String): Boolean {
            val lowerQuery = query.lowercase()
            return lowerQuery.contains(Regex(
                "today|tonight|tomorrow|yesterday|" +
                "news|latest|recent|current|" +
                "weather|temperature|forecast|" +
                "stock|price|" +
                "score|game|match|" +
                "happening|going on|" +
                "what's|whats|" +
                "update|status"
            ))
        }
    }
}
