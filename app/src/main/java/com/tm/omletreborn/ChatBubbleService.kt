package com.tm.omletreborn

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlin.math.abs

class ChatBubbleService : Service() {
    companion object { const val MINECRAFT_PACKAGE = "com.mojang.minecraftpe" }

    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private var panel: View? = null
    private var roomListener: ListenerRegistration? = null
    private var messageListener: ListenerRegistration? = null
    private var selectedRoom: String? = null
    private var roomList: LinearLayout? = null
    private var chatList: LinearLayout? = null
    private var messageInput: EditText? = null
    private var status: TextView? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("omlet_bubble", "Omlet Reborn", NotificationManager.IMPORTANCE_LOW))
        val nb = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "omlet_bubble") else @Suppress("DEPRECATION") Notification.Builder(this)
        startForeground(2401, nb.setSmallIcon(android.R.drawable.sym_action_chat).setContentTitle("Omlet Reborn").setContentText("Bong bóng chat đang hoạt động").setOngoing(true).build())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showBubble()
        return START_STICKY
    }

    private fun lp(w: Int, h: Int, focus: Boolean = false) = WindowManager.LayoutParams(
        w, h, if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        (if (focus) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun showBubble() {
        if (bubble != null || !android.provider.Settings.canDrawOverlays(this)) return
        val v = TextView(this).apply {
            text = "M"; textSize = 21f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(18,133,245)); setStroke(dp(3), Color.CYAN) }
        }
        bubbleParams = lp(dp(58), dp(58)).apply { x = dp(10); y = resources.displayMetrics.heightPixels / 2 }
        var sx=0f; var sy=0f; var ox=0; var oy=0; var moved=false
        v.setOnTouchListener { _, e ->
            when(e.action) {
                MotionEvent.ACTION_DOWN -> { sx=e.rawX; sy=e.rawY; ox=bubbleParams.x; oy=bubbleParams.y; moved=false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx=(e.rawX-sx).toInt(); val dy=(e.rawY-sy).toInt()
                    if(abs(dx)>dp(5)||abs(dy)>dp(5)) moved=true
                    bubbleParams.x=(ox+dx).coerceIn(0, resources.displayMetrics.widthPixels-dp(58))
                    bubbleParams.y=(oy+dy).coerceIn(0, resources.displayMetrics.heightPixels-dp(58))
                    runCatching { wm.updateViewLayout(v,bubbleParams) }; true
                }
                MotionEvent.ACTION_UP -> { if(!moved) openPanel(); true }
                else -> true
            }
        }
        bubble=v
        runCatching { wm.addView(v,bubbleParams) }.onFailure { bubble=null }
    }

    private fun openPanel() {
        if(panel!=null) return
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(13),dp(12),dp(13),dp(12))
            background=GradientDrawable().apply { setColor(Color.rgb(5,13,27)); cornerRadius=dp(18).toFloat(); setStroke(dp(2),Color.rgb(35,145,220)) }
        }
        val header=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text="OMLET REBORN"; textSize=19f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) },LinearLayout.LayoutParams(0,dp(42),1f))
        header.addView(TextView(this).apply { text="×"; textSize=30f; gravity=Gravity.CENTER; setTextColor(Color.WHITE); setOnClickListener { closePanel() } },LinearLayout.LayoutParams(dp(42),dp(42)))
        root.addView(header)
        root.addView(TextView(this).apply {
            text="MINECRAFT • PLAY TOGETHER"; gravity=Gravity.CENTER; textSize=15f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(15,85,65),Color.rgb(25,130,100),Color.rgb(20,65,95))).apply { cornerRadius=dp(10).toFloat() }
        },LinearLayout.LayoutParams(-1,dp(62)))
        status=label("Đang tải map từ máy chủ…"); root.addView(status)
        val rs=ScrollView(this); roomList=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; rs.addView(roomList)
        root.addView(rs,LinearLayout.LayoutParams(-1,dp(150)))
        root.addView(label("TIN NHẮN CHUNG"))
        val cs=ScrollView(this); chatList=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; cs.addView(chatList)
        root.addView(cs,LinearLayout.LayoutParams(-1,dp(110)))
        val send=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        messageInput=EditText(this).apply { hint="Nhắn tin…"; setSingleLine(); textSize=13f; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        send.addView(messageInput,LinearLayout.LayoutParams(0,dp(44),1f))
        send.addView(TextView(this).apply { text="GỬI"; gravity=Gravity.CENTER; setTextColor(Color.WHITE); setPadding(dp(10),0,dp(10),0); setOnClickListener { sendMessage() } },LinearLayout.LayoutParams(dp(55),dp(44)))
        root.addView(send)
        root.addView(label("Kéo M đến vị trí tùy ý. Nút × chỉ đóng bảng và hiện lại bong bóng."))
        panel=root
        bubble?.visibility=View.GONE
        runCatching { wm.addView(root,lp(dp(330),WindowManager.LayoutParams.WRAP_CONTENT,true).apply { gravity=Gravity.CENTER }) }.onFailure { panel=null; bubble?.visibility=View.VISIBLE }
        listenRooms()
    }

    private fun listenRooms() {
        val uid=FirebaseAuth.getInstance().currentUser?.uid
        if(uid==null) { status?.text="Đăng nhập trong Omlet Reborn để xem phòng."; return }
        roomListener?.remove()
        roomListener=FirebaseFirestore.getInstance().collection("rooms").whereEqualTo("status","open").addSnapshotListener { snap,err ->
            val box=roomList ?: return@addSnapshotListener
            box.removeAllViews()
            if(err!=null) { status?.text="Không tải được phòng. Kiểm tra Firestore Rules."; return@addSnapshotListener }
            val docs=snap?.documents.orEmpty()
            val own=docs.filter { it.getString("hostUid")==uid }
            status?.text=if(own.isNotEmpty()) "MAP CỦA BẠN • ${own.size} map online" else "MAP ĐANG ONLINE • ${docs.size}"
            val shown=if(own.isNotEmpty()) own else docs
            if(shown.isEmpty()) box.addView(label("Chưa có map thật nào đang mở."))
            shown.forEach { d ->
                val n=d.getString("name") ?: "Map Minecraft"
                val count=(d.get("members") as? List<*>)?.size ?: 1
                val max=d.getLong("maxPlayers") ?: 8L
                val row=LinearLayout(this).apply {
                    orientation=LinearLayout.VERTICAL; setPadding(dp(9),dp(7),dp(9),dp(7))
                    background=GradientDrawable().apply { setColor(Color.rgb(16,34,55)); cornerRadius=dp(8).toFloat() }
                    addView(label(n + if(d.getString("hostUid")==uid) " • CHỦ MAP" else ""))
                    addView(label("● ONLINE     Người chơi: $count/$max"))
                    setOnClickListener { selectedRoom=d.id; listenMessages(d.id) }
                }
                box.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(6) })
            }
        }
    }

    private fun listenMessages(id:String) {
        messageListener?.remove(); selectedRoom=id; chatList?.removeAllViews()
        messageListener=FirebaseFirestore.getInstance().collection("rooms").document(id).collection("messages").orderBy("createdAt").limit(30).addSnapshotListener { snap,err ->
            val box=chatList ?: return@addSnapshotListener
            box.removeAllViews()
            if(err!=null) { box.addView(label("Chat chưa được cấp quyền Firestore.")); return@addSnapshotListener }
            snap?.documents?.forEach { d -> box.addView(label("${d.getString("senderName") ?: "Người chơi"}: ${d.getString("text") ?: ""}")) }
        }
    }

    private fun sendMessage() {
        val uid=FirebaseAuth.getInstance().currentUser?.uid ?: return
        val id=selectedRoom ?: run { status?.text="Chọn map trước khi chat."; return }
        val value=messageInput?.text?.toString()?.trim().orEmpty().take(300)
        if(value.isBlank()) return
        val who=FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@") ?: "Người chơi"
        FirebaseFirestore.getInstance().collection("rooms").document(id).collection("messages").add(
            mapOf("senderUid" to uid,"senderName" to who,"text" to value,"createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())
        ).addOnSuccessListener { messageInput?.setText("") }.addOnFailureListener { status?.text="Gửi tin nhắn thất bại. Kiểm tra Rules." }
    }

    private fun label(s:String)=TextView(this).apply { text=s; textSize=12f; setTextColor(Color.rgb(220,235,248)); setPadding(0,dp(4),0,dp(4)) }

    private fun closePanel() {
        roomListener?.remove(); roomListener=null; messageListener?.remove(); messageListener=null
        panel?.let { runCatching { wm.removeView(it) } }; panel=null
        roomList=null; chatList=null; messageInput=null; status=null
        bubble?.visibility=View.VISIBLE
    }

    override fun onDestroy() { closePanel(); bubble?.let { runCatching { wm.removeView(it) } }; bubble=null; super.onDestroy() }
    override fun onBind(intent:Intent?):IBinder?=null
}
