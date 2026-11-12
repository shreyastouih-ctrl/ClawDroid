package com.openclaw.nativeapp.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import com.openclaw.nativeapp.R
import com.openclaw.nativeapp.ai.GeminiAgent
import com.openclaw.nativeapp.data.LogManager
import com.openclaw.nativeapp.shizuku.DeviceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingOverlayService : Service() {
    private val TAG = "FloatingOverlay"
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var geminiAgent: GeminiAgent

    override fun onCreate() {
        super.onCreate()
        geminiAgent = GeminiAgent(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            LogManager.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW permission missing.")
            stopSelf()
            return
        }

        setupFloatingView()
        LogManager.i(TAG, "Floating Overlay Watcher started.")
    }

    private fun setupFloatingView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_hud, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        windowManager?.addView(floatingView, params)

        val collapsedBubble = floatingView?.findViewById<View>(R.id.layoutCollapsedBubble)
        val expandedPanel = floatingView?.findViewById<View>(R.id.layoutExpandedPanel)
        val btnCollapse = floatingView?.findViewById<TextView>(R.id.btnCollapsePanel)
        val tvTicker = floatingView?.findViewById<TextView>(R.id.tvOverlayTicker)
        val btnInspect = floatingView?.findViewById<View>(R.id.btnOverlayInspect)
        val btnHome = floatingView?.findViewById<View>(R.id.btnOverlayHome)
        val etPrompt = floatingView?.findViewById<EditText>(R.id.etOverlayPrompt)
        val btnSend = floatingView?.findViewById<ImageButton>(R.id.btnOverlaySend)

        // Make draggable on touch
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        collapsedBubble?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isClick = false
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        // Expand HUD panel
                        collapsedBubble.visibility = View.GONE
                        expandedPanel?.visibility = View.VISIBLE
                        // Enable focusable so user can type in the prompt box
                        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                        windowManager?.updateViewLayout(floatingView, params)
                    }
                    true
                }
                else -> false
            }
        }

        btnCollapse?.setOnClickListener {
            expandedPanel?.visibility = View.GONE
            collapsedBubble?.visibility = View.VISIBLE
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager?.updateViewLayout(floatingView, params)
        }

        btnHome?.setOnClickListener {
            scope.launch {
                DeviceController.pressKeyByName("HOME")
                tvTicker?.text = "Pressed Home"
            }
        }

        btnInspect?.setOnClickListener {
            scope.launch {
                tvTicker?.text = "Capturing screen & asking AI..."
                val response = geminiAgent.executeGoal("Look at the screen right now and tell me what game/app is open and what is happening.") { progress ->
                    tvTicker?.text = progress
                }
                tvTicker?.text = response.take(150)
            }
        }

        btnSend?.setOnClickListener {
            val prompt = etPrompt?.text?.toString()?.trim() ?: ""
            if (prompt.isNotBlank()) {
                etPrompt?.setText("")
                scope.launch {
                    tvTicker?.text = "Executing: $prompt"
                    val response = geminiAgent.executeGoal(prompt) { progress ->
                        tvTicker?.text = progress
                    }
                    tvTicker?.text = "Done: ${response.take(150)}"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) {
            windowManager?.removeView(floatingView)
            floatingView = null
        }
        scope.cancel()
        LogManager.i(TAG, "Floating Overlay Watcher stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
