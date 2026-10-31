package com.tm.omletreborn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class ChatBubbleService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private var panel: View? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    private fun startAsForeground() {
        val channelId = "omlet_chat_bubble"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Bong bóng chat Omlet", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("Omlet Reborn đang bật bong bóng")
            .setContentText("Chạm vào bong bóng nổi để mở bảng chat.")
            .setOngoing(true)
            .build()
        startForeground(2401, notification)
    }

    private fun overlayParams(width: Int, height: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width, height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    private fun showBubble() {
        val view = TextView(this).apply {
            text = "M"
            textSize = 20f
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
        bubbleParams = overlayParams(58, 58).apply {
            x = 12
            y = 260
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragged = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = bubbleParams.x
                    startY = bubbleParams.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > 5 || kotlin.math.abs(dy) > 5) dragged = true
                    bubbleParams.x = startX + dx
                    bubbleParams.y = startY + dy
                    windowManager.updateViewLayout(view, bubbleParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) togglePanel()
                    true
                }
                else -> true
            }
        }
        bubble = view
        windowManager.addView(view, bubbleParams)
    }

    private fun togglePanel() {
        if (panel != null) {
            windowManager.removeView(panel)
            panel = null
            return
        }
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
            text = "Omlet Reborn"
            textSize = 19f
            setTextColor(android.graphics.Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        card.addView(TextView(this).apply {
            text = "Bong bóng chat đã sẵn sàng. Khung chat trực tuyến sẽ được kết nối ở bản tiếp theo."
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
        card.addView(Button(this).apply {
            text = "Đóng bảng chat"
            setOnClickListener { closePanel() }
        })
        val params = overlayParams(300, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            x = 18
            y = (bubbleParams.y + 70).coerceAtMost(resources.displayMetrics.heightPixels - 420)
        }
        panel = card
        windowManager.addView(card, params)
    }

    private fun closePanel() {
        panel?.let { windowManager.removeView(it) }
        panel = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        closePanel()
        bubble?.let { windowManager.removeView(it) }
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
