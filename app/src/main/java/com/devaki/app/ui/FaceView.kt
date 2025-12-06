package com.devaki.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import kotlin.math.sin

class FaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    enum class State {
        SLEEPING,   // Closed eyes
        LISTENING,  // Eyes open, mouth idle
        TALKING,    // Eyes open, mouth animating
        MOVING,     // Eyes open
        IDLE        // Neutral
    }
    
    private var currentState = State.IDLE
    
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#2C2C2C")
        style = Paint.Style.FILL
    }
    
    private val eyePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val mouthPaint = Paint().apply {
        color = Color.parseColor("#666666")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val mouthTalkingPaint = Paint().apply {
        color = Color.parseColor("#00FF00")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    // Animation state
    private var mouthAnimPhase = 0f
    private val animRunnable = object : Runnable {
        override fun run() {
            if (currentState == State.TALKING) {
                mouthAnimPhase += 0.2f
                invalidate()
                postDelayed(this, 50) // 20 FPS animation
            }
        }
    }
    
    fun setState(state: State) {
        if (currentState != state) {
            currentState = state
            
            // Start/stop mouth animation
            removeCallbacks(animRunnable)
            if (state == State.TALKING) {
                post(animRunnable)
            }
            
            invalidate()
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        
        // Background
        canvas.drawColor(bgPaint.color)
        
        // Calculate sizes based on view dimensions
        val faceSize = min(w, h) * 0.8f
        val centerX = w / 2f
        val centerY = h / 2f
        
        // Eyes
        val eyeY = centerY - faceSize * 0.15f
        val eyeSpacing = faceSize * 0.25f
        val eyeWidth = faceSize * 0.15f
        val eyeHeight = when (currentState) {
            State.SLEEPING -> faceSize * 0.02f // Thin line when sleeping
            else -> faceSize * 0.12f
        }
        
        // Left eye
        val leftEyeRect = RectF(
            centerX - eyeSpacing - eyeWidth,
            eyeY - eyeHeight / 2,
            centerX - eyeSpacing,
            eyeY + eyeHeight / 2
        )
        canvas.drawOval(leftEyeRect, eyePaint)
        
        // Right eye
        val rightEyeRect = RectF(
            centerX + eyeSpacing,
            eyeY - eyeHeight / 2,
            centerX + eyeSpacing + eyeWidth,
            eyeY + eyeHeight / 2
        )
        canvas.drawOval(rightEyeRect, eyePaint)
        
        // Mouth
        val mouthY = centerY + faceSize * 0.2f
        val mouthWidth = faceSize * 0.4f
        
        val mouthHeight = when (currentState) {
            State.TALKING -> {
                // Animate mouth height
                val base = faceSize * 0.08f
                val variation = faceSize * 0.05f * sin(mouthAnimPhase)
                base + variation
            }
            State.LISTENING -> faceSize * 0.05f
            else -> faceSize * 0.06f
        }
        
        val paint = if (currentState == State.TALKING) mouthTalkingPaint else mouthPaint
        
        val mouthRect = RectF(
            centerX - mouthWidth / 2,
            mouthY - mouthHeight / 2,
            centerX + mouthWidth / 2,
            mouthY + mouthHeight / 2
        )
        canvas.drawRect(mouthRect, paint)
    }
}
