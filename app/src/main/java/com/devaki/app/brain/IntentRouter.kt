package com.devaki.app.brain

import android.util.Log
import com.devaki.app.ui.FaceView

private const val TAG = "DEVAKI"

class IntentRouter {
    
    data class Intent(
        val command: String? = null,
        val followMode: Boolean? = null,
        val faceState: FaceView.State? = null,
        val speedChange: Int = 0,
        val confirmation: String = ""
    )
    
    /**
     * Parse user command and return an Intent with action and confirmation.
     * Returns null if no intent matched (caller should try LLM).
     */
    fun route(text: String): Intent? {
        val t = text.lowercase().trim()
        
        // Movement commands
        when {
            matchesAny(t, "forward", "go forward", "move forward", "ahead") -> 
                return Intent(command = "FWD", confirmation = "moving forward")
            
            matchesAny(t, "back", "backward", "go back", "reverse") -> 
                return Intent(command = "BACK", confirmation = "moving backward")
            
            matchesAny(t, "left", "turn left", "go left") -> 
                return Intent(command = "LEFT", confirmation = "turning left")
            
            matchesAny(t, "right", "turn right", "go right") -> 
                return Intent(command = "RIGHT", confirmation = "turning right")
            
            matchesAny(t, "stop", "halt", "freeze", "wait") -> 
                return Intent(command = "STOP", confirmation = "stopping")
        }
        
        // Follow mode
        when {
            matchesAny(t, "follow me", "follow", "track me", "chase me") -> 
                return Intent(followMode = true, confirmation = "okay, I'll follow you")
            
            matchesAny(t, "stop following", "don't follow", "unfollow") -> 
                return Intent(followMode = false, confirmation = "okay, stopped following")
        }
        
        // Speed control
        when {
            matchesAny(t, "faster", "speed up", "go faster", "hurry") -> 
                return Intent(speedChange = 20, confirmation = "speeding up")
            
            matchesAny(t, "slower", "slow down", "go slower") -> 
                return Intent(speedChange = -20, confirmation = "slowing down")
        }
        
        // Face state
        when {
            matchesAny(t, "sleep", "go to sleep", "rest") -> 
                return Intent(faceState = FaceView.State.SLEEPING, confirmation = "going to sleep")
            
            matchesAny(t, "wake", "wake up", "awaken") -> 
                return Intent(faceState = FaceView.State.IDLE, confirmation = "I'm awake")
        }
        
        Log.d(TAG, "No intent matched for: $text")
        return null // No match - caller should try LLM
    }
    
    private fun matchesAny(text: String, vararg patterns: String): Boolean {
        return patterns.any { pattern ->
            text.contains(pattern, ignoreCase = true) || 
            text == pattern ||
            text.split(" ").any { it == pattern }
        }
    }
}
