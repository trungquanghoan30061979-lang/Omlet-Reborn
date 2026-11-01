package com.tm.omletreborn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class ChatBubbleService : Service() {
    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var bubble: TextView? = null
    private var panel: View? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private val monitor = object : Runnable {
        override fun run() {
            val minecraftIsForeground = currentForegroundPackage() == MINECRAFT_PACKAGE
            if (minecraftIsForeground && bubble == null && android.provider.Settings.canDrawOverlays(this@ChatBubbleService)) {
                showBubble()
            } else if (!minecraftIsForeground && bubble != null) {
                hideBubble()
            }
            handler.postDelayed(this, 800)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        handler.post(monitor)
    }

    private fun currentForegroundPackage(): String? {
        val usage = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        return try {
            usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 15000, now)
                ?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (_: Exception) { null }
    }

    private fun startAsForeground() {
        val channelId = "omlet_chat_bubble"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Omlet tự động hiện bong bóng", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        startForeground(2401, builder.setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("Omlet Reborn đang chờ Minecraft")
            .setContentText("Bong bóng chỉ xuất hiện khi Minecraft PE được mở.")
            .setOngoing(true).build())
    }

    private fun overlayParams(width: Int, height: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width, height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun showBubble() {
        if (bubble != null) return
        val view = TextView(this).apply {
            text = "M"; textSize = 20f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(android.graphics.Color.rgb(22, 139, 255))
                setStroke(3, android.graphics.Color.WHITE)
            }
            elevation = 12f
        }
        bubbleParams = overlayParams(58, 58).apply { x = 12; y = 260 }
        var downX = 0f; var downY = 0f
        var startX = 0; var startY = 0
        var dragged = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = bubbleParams.x; startY = bubbleParams.y
                    dragged = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > 5 || kotlin.math.abs(dy) > 5) dragged = true
                    bubbleParams.x = startX + dx; bubbleParams.y = startY + dy
                    try { windowManager.updateViewLayout(view, bubbleParams) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> { if (!dragged) togglePanel(); true }
                else -> true
            }
        }
        bubble = view
        try { windowManager.addView(view, bubbleParams) } catch (_: Exception) { bubble = null }
    }

    private fun hideBubble() {
        closePanel()
        bubble?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        bubble = null
    }

    private fun togglePanel() {
        if (panel != null) { closePanel(); return }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 18, 22, 18)
            background = GradientDrawable().apply {
                setColor(android.graphics.Color.rgb(12, 29, 50))
                cornerRadius = 28f
                setStroke(2, android.graphics.Color.rgb(54, 197, 240))
            }
            elevation = 18f
        }
        card.addView(TextView(this).apply {
            text = "Omlet Reborn"; textSize = 19f
            setTextColor(android.graphics.Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        card.addView(TextView(this).apply {
            text = "Bảng chat nổi đang hoạt động trong Minecraft PE."
            textSize = 13f
            setTextColor(android.graphics.Color.LTGRAY)
            setPadding(0, 12, 0, 12)
        })
        card.addView(Button(this).apply {
            text = "Mở Omlet Reborn"
            setOnClickListener {
                val launch = packageManager.getLaunchIntentForPackage(packageName)
                launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                if (launch != null) startActivity(launch)
                closePanel()
            }
        })
        card.addView(Button(this).apply { text = "Đóng"; setOnClickListener { closePanel() } })
        val params = overlayParams(300, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            x = 18; y = (bubbleParams.y + 70).coerceAtMost(resources.displayMetrics.heightPixels - 420)
        }
        panel = card
        try { windowManager.addView(card, params) } catch (_: Exception) { panel = null }
    }

    private fun closePanel() {
        panel?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        panel = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onDestroy() {
        handler.removeCallbacks(monitor)
        hideBubble()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object { const val MINECRAFT_PACKAGE = "com.mojang.minecraftpe" }
}
