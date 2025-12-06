package com.devaki.app.control

import android.util.Log
import kotlin.math.abs

private const val TAG = "DEVAKI"

class FollowController {
    
    companion object {
        private const val KP = 1.8f // Proportional gain - tune this for responsiveness
    }
    
    /**
     * Compute differential wheel speeds based on face position.
     * 
     * @param faceX Center X coordinate of detected face
     * @param centerX Center X coordinate of image
     * @param imgWidth Width of image
     * @param baseSpeed Base forward speed (0-255)
     * @return Pair of (leftSpeed, rightSpeed) each clamped to 0-255
     */
    fun computeSpeed(faceX: Float, centerX: Float, imgWidth: Int, baseSpeed: Int): Pair<Int, Int> {
        // Compute horizontal error normalized to [-1, 1]
        val halfWidth = imgWidth / 2f
        val error = (faceX - centerX) / halfWidth
        
        // P-controller: turn amount based on error
        val turn = (KP * error * baseSpeed).toInt()
        
        // Differential drive: subtract turn from left, add to right
        var leftSpeed = baseSpeed - turn
        var rightSpeed = baseSpeed + turn
        
        // Clamp to valid PWM range
        leftSpeed = leftSpeed.coerceIn(0, 255)
        rightSpeed = rightSpeed.coerceIn(0, 255)
        
        Log.d(TAG, "Follow: error=${"%.2f".format(error)}, turn=$turn, L=$leftSpeed, R=$rightSpeed")
        
        return Pair(leftSpeed, rightSpeed)
    }
    
    /**
     * Simple distance-based speed adjustment (optional enhancement).
     * If face is too small/far, slow down. If too large/close, slow down.
     */
    fun adjustForDistance(faceWidth: Float, targetWidth: Float, baseSpeed: Int): Int {
        val ratio = faceWidth / targetWidth
        return when {
            ratio < 0.5f -> (baseSpeed * 0.7f).toInt() // Too far, slow down
            ratio > 2.0f -> (baseSpeed * 0.5f).toInt() // Too close, slow down
            else -> baseSpeed
        }.coerceIn(0, 255)
    }
}
