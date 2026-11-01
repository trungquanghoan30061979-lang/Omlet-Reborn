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
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import kotlin.math.abs

class ChatBubbleService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private var panel: View? = null
    private var trash: TextView? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var dismissedByUser = false

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Mở lại Omlet Reborn sẽ cho phép hiện lại bong bóng nếu người dùng đã kéo nó vào X.
        if (android.provider.Settings.canDrawOverlays(this)) {
            dismissedByUser = false
            showBubble()
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val channelId = "omlet_chat_bubble"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Omlet Reborn bong bóng nổi", NotificationManager.IMPORTANCE_LOW))
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, channelId)
            else @Suppress("DEPRECATION") Notification.Builder(this)
        startForeground(2401, builder.setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("Omlet Reborn đang hoạt động")
            .setContentText("Bong bóng nổi vẫn hiển thị khi chuyển ứng dụng.")
            .setOngoing(true).build())
    }

    private fun params(width: Int, height: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width, height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun screenHeight(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) wm.currentWindowMetrics.bounds.height()
        else @Suppress("DEPRECATION") resources.displayMetrics.heightPixels

    private fun showBubble() {
        if (bubble != null || dismissedByUser || !android.provider.Settings.canDrawOverlays(this)) return
        val view = TextView(this).apply {
            text = "M"
            textSize = 21f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(18, 133, 245))
                setStroke(3, Color.rgb(95, 220, 255))
            }
            elevation = 18f
        }
        bubbleParams = params(dp(58), dp(58)).apply {
            x = 0
            y = screenHeight() / 2
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = bubbleParams.x; startY = bubbleParams.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > dp(5) || abs(dy) > dp(5)) {
                        moved = true
                        showTrash()
                    }
                    bubbleParams.x = (startX + dx).coerceIn(0, resources.displayMetrics.widthPixels - dp(58))
                    bubbleParams.y = (startY + dy).coerceIn(0, screenHeight() - dp(58))
                    runCatching { wm.updateViewLayout(view, bubbleParams) }
                    updateTrashState(event.rawY)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dropOnTrash = moved && isOverTrash(event.rawY)
                    hideTrash()
                    if (dropOnTrash) {
                        dismissedByUser = true
                        closePanel()
                        removeBubble()
                    } else if (!moved) {
                        togglePanel()
                    } else {
                        // Hít nhẹ về cạnh gần nhất để bong bóng gọn như chat head.
                        val maxX = resources.displayMetrics.widthPixels - dp(58)
                        bubbleParams.x = if (bubbleParams.x + dp(29) < resources.displayMetrics.widthPixels / 2) 0 else maxX
                        runCatching { wm.updateViewLayout(view, bubbleParams) }
                    }
                    true
                }
                else -> true
            }
        }
        bubble = view
        runCatching { wm.addView(view, bubbleParams) }.onFailure { bubble = null }
    }

    private fun showTrash() {
        if (trash != null) return
        val target = TextView(this).apply {
            text = "×"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(220, 55, 65))
                setStroke(dp(2), Color.WHITE)
            }
            elevation = 24f
        }
        val p = params(dp(66), dp(66)).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(28)
        }
        trash = target
        runCatching { wm.addView(target, p) }.onFailure { trash = null }
    }

    private fun updateTrashState(rawY: Float) {
        trash?.alpha = if (rawY >= screenHeight() - dp(150)) 1f else .72f
        trash?.scaleX = if (rawY >= screenHeight() - dp(150)) 1.18f else 1f
        trash?.scaleY = if (rawY >= screenHeight() - dp(150)) 1.18f else 1f
    }

    private fun isOverTrash(rawY: Float): Boolean = rawY >= screenHeight() - dp(150)

    private fun hideTrash() {
        trash?.let { runCatching { wm.removeView(it) } }
        trash = null
    }

    private fun removeBubble() {
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
    }

    private fun togglePanel() {
        if (panel != null) { closePanel(); return }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.rgb(5, 10, 27))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(3), Color.rgb(40, 155, 210))
            }
            elevation = 24f
        }
        val title = TextView(this).apply {
            text = "OMLET REBORN"
            textSize = 24f
            setTextColor(Color.rgb(225, 247, 255))
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(18))
        }
        card.addView(title)
        addMenuRow(card, "A", "Tin nhắn", "Mở Omlet Reborn") { openApp() }
        addMenuRow(card, "B", "Phòng chơi", "Tìm đồng đội") { openApp("rooms") }
        addMenuRow(card, "C", "Bạn bè", "Kết nối cộng đồng") { openApp() }
        addMenuRow(card, "D", "Mở Omlet", "Mở ứng dụng đầy đủ") { openApp() }
        val note = TextView(this).apply {
            text = "Bong bóng nổi luôn hiển thị khi chuyển ứng dụng. Kéo xuống dấu × để ẩn."
            textSize = 11f
            setTextColor(Color.rgb(150, 190, 210))
            setPadding(0, dp(14), 0, 0)
        }
        card.addView(note)
        val p = params(dp(310), WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
            x = 0; y = 0
        }
        panel = card
        runCatching { wm.addView(card, p) }.onFailure { panel = null }
    }

    private fun addMenuRow(parent: LinearLayout, letter: String, label: String, hint: String, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), dp(10), dp(9), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.rgb(13, 25, 43))
                setStroke(dp(2), Color.rgb(65, 165, 215))
                cornerRadius = dp(5).toFloat()
            }
            val badge = TextView(this@ChatBubbleService).apply {
                text = letter; textSize = 21f; gravity = Gravity.CENTER
                setTextColor(Color.rgb(100, 225, 255))
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    setColor(Color.rgb(5, 18, 35)); setStroke(dp(2), Color.rgb(65, 165, 215))
                    cornerRadius = dp(3).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(dp(46), dp(42))
            }
            addView(badge)
            val labels = LinearLayout(this@ChatBubbleService).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                addView(TextView(this@ChatBubbleService).apply {
                    text = label; textSize = 17f; setTextColor(Color.rgb(195, 240, 250))
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                })
                addView(TextView(this@ChatBubbleService).apply {
                    text = hint; textSize = 10f; setTextColor(Color.LTGRAY)
                })
            }
            addView(labels)
            isClickable = true
            setOnClickListener { closePanel(); action() }
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(62))
        lp.bottomMargin = dp(9)
        parent.addView(row, lp)
    }

    private fun openApp(destination: String? = null) {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        if (destination != null) launch.putExtra("open_page", destination)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { startActivity(launch) }
    }

    private fun closePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        closePanel()
        hideTrash()
        removeBubble()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object { const val MINECRAFT_PACKAGE = "com.mojang.minecraftpe" }
}
