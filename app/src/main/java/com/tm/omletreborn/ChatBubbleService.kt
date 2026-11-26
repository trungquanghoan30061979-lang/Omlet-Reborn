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
    private val heartbeatHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val heartbeatTask = object : Runnable {
        override fun run() {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                FirebaseFirestore.getInstance().collection("rooms").whereEqualTo("hostUid", uid).whereEqualTo("status", "open").whereEqualTo("isLive", true).get()
                    .addOnSuccessListener { snapshot -> snapshot.documents.forEach { doc ->
                        doc.reference.update("lastHeartbeat", com.google.firebase.firestore.FieldValue.serverTimestamp())
                    } }
            }
            heartbeatHandler.postDelayed(this, 15000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("omlet_bubble", "Omlet Reborn", NotificationManager.IMPORTANCE_LOW))
        val nb = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "omlet_bubble") else @Suppress("DEPRECATION") Notification.Builder(this)
        startForeground(2401, nb.setSmallIcon(android.R.drawable.sym_action_chat).setContentTitle("Omlet Reborn").setContentText("Bong bóng chat đang hoạt động").setOngoing(true).build())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
        heartbeatHandler.post(heartbeatTask)
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
        val screenW=resources.displayMetrics.widthPixels
        val screenH=resources.displayMetrics.heightPixels
        val wide=screenW>screenH
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            setPadding(dp(5),dp(5),dp(5),dp(5))
            background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(4,7,18),Color.rgb(25,35,105),Color.rgb(49,58,170))).apply {
                cornerRadius=0f
                setStroke(dp(2),Color.rgb(52,105,220))
            }
        }

        // Banner Minecraft ở cột trái, theo bố cục trong bản thiết kế PDF.
        val hero=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER
            setPadding(dp(12),dp(12),dp(12),dp(12))
            background=GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(7,15,42),Color.rgb(23,48,115),Color.rgb(12,22,48))).apply {
                cornerRadius=dp(15).toFloat()
            }
            addView(TextView(this@ChatBubbleService).apply {
                text="OMLET REBORN"; textSize=if(wide) 23f else 20f
                typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
                gravity=Gravity.CENTER; setTextColor(Color.rgb(60,112,255))
            },LinearLayout.LayoutParams(-1,dp(52)))
            val spacer=View(this@ChatBubbleService)
            heroSpacer@ run {
                addView(spacer,LinearLayout.LayoutParams(1,0,1f))
            }
            addView(TextView(this@ChatBubbleService).apply {
                text="MINECRAFT"; textSize=if(wide) 27f else 23f
                typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
                gravity=Gravity.CENTER; setTextColor(Color.WHITE)
            },LinearLayout.LayoutParams(-1,dp(54)))
            addView(TextView(this@ChatBubbleService).apply {
                text="CHƠI CÙNG BẠN BÈ"; textSize=10f
                gravity=Gravity.CENTER; setTextColor(Color.rgb(150,205,255))
            })
        }

        val right=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(8),dp(2),dp(2),dp(2))
        }
        val top=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        val title=TextView(this).apply {
            text="CÁC THẾ GIỚI"; textSize=20f; typeface=Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity=Gravity.CENTER_VERTICAL
        }
        top.addView(title,LinearLayout.LayoutParams(0,dp(42),1f))
        top.addView(TextView(this).apply {
            text="×"; textSize=29f; gravity=Gravity.CENTER; setTextColor(Color.rgb(50,135,235))
            background=GradientDrawable().apply { setColor(Color.rgb(210,225,245)); cornerRadius=dp(10).toFloat() }
            setOnClickListener { closePanel() }
        },LinearLayout.LayoutParams(dp(42),dp(42)))
        right.addView(top)

        val tabs=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER }
        fun tabButton(caption:String, selected:Boolean, action:()->Unit):TextView = TextView(this).apply {
            text=caption; textSize=if(wide) 12f else 11f; gravity=Gravity.CENTER
            typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            setPadding(dp(7),dp(9),dp(7),dp(9))
            background=GradientDrawable().apply {
                setColor(if(selected) Color.rgb(24,75,190) else Color.rgb(10,18,42))
                cornerRadius=dp(9).toFloat()
                if(selected) setStroke(dp(2),Color.WHITE)
            }
            setOnClickListener { action() }
        }
        val content=FrameLayout(this)
        val worldPage=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val chatPage=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; visibility=View.GONE }
        val mapPage=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; visibility=View.GONE }
        fun selectTab(which:Int) {
            worldPage.visibility=if(which==0) View.VISIBLE else View.GONE
            chatPage.visibility=if(which==1) View.VISIBLE else View.GONE
            mapPage.visibility=if(which==2) View.VISIBLE else View.GONE
            title.text=when(which) { 1->"CHAT CHUNG"; 2->"TẠO MAP"; else->"CÁC THẾ GIỚI" }
            tabs.removeAllViews()
            tabs.addView(tabButton("▣  THẾ GIỚI",which==0){selectTab(0)},LinearLayout.LayoutParams(0,dp(42),1f))
            tabs.addView(tabButton("●  CHAT",which==1){selectTab(1)},LinearLayout.LayoutParams(0,dp(42),1f))
            tabs.addView(tabButton("▦  TẠO MAP",which==2){selectTab(2)},LinearLayout.LayoutParams(0,dp(42),1f))
        }
        tabs.addView(tabButton("▣  THẾ GIỚI",true){selectTab(0)},LinearLayout.LayoutParams(0,dp(42),1f))
        tabs.addView(tabButton("●  CHAT",false){selectTab(1)},LinearLayout.LayoutParams(0,dp(42),1f))
        tabs.addView(tabButton("▦  TẠO MAP",false){selectTab(2)},LinearLayout.LayoutParams(0,dp(42),1f))
        right.addView(tabs)
        status=label("Đang tải map từ máy chủ…")
        worldPage.addView(status)
        val rs=ScrollView(this)
        roomList=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        rs.addView(roomList)
        worldPage.addView(rs,LinearLayout.LayoutParams(-1,0,1f))

        chatPage.addView(label("TIN NHẮN TRONG MAP ĐANG CHỌN"))
        val cs=ScrollView(this)
        chatList=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        cs.addView(chatList)
        chatPage.addView(cs,LinearLayout.LayoutParams(-1,0,1f))
        val send=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        messageInput=EditText(this).apply {
            hint="Nhắn tin…"; setSingleLine(); textSize=13f
            setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
        }
        send.addView(messageInput,LinearLayout.LayoutParams(0,dp(44),1f))
        send.addView(TextView(this).apply {
            text="GỬI"; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
            setPadding(dp(10),0,dp(10),0)
            background=GradientDrawable().apply { setColor(Color.rgb(25,95,210)); cornerRadius=dp(8).toFloat() }
            setOnClickListener { sendMessage() }
        },LinearLayout.LayoutParams(dp(58),dp(44)))
        chatPage.addView(send)
        chatPage.addView(label("Chat riêng sẽ được bổ sung ở bước tiếp theo."))

        mapPage.addView(label("QUẢN LÝ THẾ GIỚI CỦA BẠN"))
        mapPage.addView(label("Map do bạn tạo sẽ hiện ở tab THẾ GIỚI. Chọn map để xem chat và trạng thái."))
        val createMapButton=TextView(this).apply {
            text="＋  MỞ OMLET REBORN ĐỂ TẠO / SỬA MAP"
            textSize=12f; gravity=Gravity.CENTER; typeface=Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); setPadding(dp(10),dp(15),dp(10),dp(15))
            background=GradientDrawable().apply { setColor(Color.rgb(24,75,190)); cornerRadius=dp(11).toFloat() }
            setOnClickListener {
                val launch=packageManager.getLaunchIntentForPackage(packageName)
                if(launch!=null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(launch)
                } else {
                    status?.text="Không mở được Omlet Reborn."
                }
            }
        }
        mapPage.addView(createMapButton)
        val mapScroll=ScrollView(this)
        val mapHint=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        mapHint.addView(label("HƯỚNG DẪN"))
        mapHint.addView(label("1. Mở Omlet Reborn và vào Phòng chơi."))
        mapHint.addView(label("2. Chọn Tạo phòng, nhập tên map, phiên bản và số người."))
        mapHint.addView(label("3. Vào Minecraft, sau đó bật trạng thái map trong bảng nổi."))
        mapHint.addView(label("4. Người chơi chỉ thấy map khi chủ map đang bật online."))
        mapScroll.addView(mapHint)
        mapPage.addView(mapScroll,LinearLayout.LayoutParams(-1,0,1f))

        content.addView(worldPage,FrameLayout.LayoutParams(-1,-1))
        content.addView(chatPage,FrameLayout.LayoutParams(-1,-1))
        content.addView(mapPage,FrameLayout.LayoutParams(-1,-1))
        right.addView(content,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(hero,LinearLayout.LayoutParams(0,-1,0.36f))
        root.addView(right,LinearLayout.LayoutParams(0,-1,0.64f))
        panel=root
        bubble?.visibility=View.GONE
        // Cửa sổ nổi ở giữa màn hình, giữ lại phần Minecraft phía sau.
        val panelWidth=(screenW*0.90f).toInt().coerceAtMost(dp(980))
        val panelHeight=(screenH*0.76f).toInt().coerceAtMost(dp(560))
        runCatching {
            wm.addView(root,lp(panelWidth,panelHeight,true).apply {
                gravity=Gravity.CENTER; x=0; y=0
            })
        }.onFailure { panel=null; bubble?.visibility=View.VISIBLE }
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
            val all=snap?.documents.orEmpty()
            val own=all.filter { it.getString("hostUid")==uid }
            val docs=all.filter { d ->
                val heartbeat=d.getTimestamp("lastHeartbeat")
                d.getBoolean("isLive")==true && heartbeat != null && System.currentTimeMillis()-heartbeat.toDate().time <= 45000L
            }
            status?.text=if(own.isNotEmpty()) "MAP CỦA BẠN • ${own.count { it.getBoolean("isLive")==true }} map đang mở" else "MAP ĐANG ONLINE • ${docs.size}"
            val shown=if(own.isNotEmpty()) own else docs
            if(shown.isEmpty()) box.addView(label("Chưa có map thật nào đang mở."))
            shown.forEach { d ->
                val n=d.getString("name") ?: "Map Minecraft"
                val count=(d.get("members") as? List<*>)?.filterIsInstance<String>()?.distinct()?.size ?: 1
                val max=d.getLong("maxPlayers") ?: 8L
                val live=d.getBoolean("isLive")==true && d.getTimestamp("lastHeartbeat")?.let { System.currentTimeMillis()-it.toDate().time <= 45000L }==true
                val card=LinearLayout(this).apply {
                    orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
                    setPadding(dp(9),dp(9),dp(9),dp(9))
                    background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(Color.rgb(5,8,15),Color.rgb(55,60,68))).apply { cornerRadius=dp(13).toFloat() }
                }
                val mark=TextView(this).apply {
                    text=n.take(1).uppercase(); textSize=22f; gravity=Gravity.CENTER
                    typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
                    background=GradientDrawable().apply {
                        setColor(if(live) Color.rgb(45,170,75) else Color.rgb(25,90,205))
                        cornerRadius=dp(11).toFloat()
                    }
                }
                card.addView(mark,LinearLayout.LayoutParams(dp(48),dp(54)))
                val details=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(9),0,0,0) }
                details.addView(TextView(this).apply {
                    text=n.uppercase(); textSize=15f; typeface=Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                })
                details.addView(label("NGƯỜI CHƠI: $count/$max  •  "+(d.getString("version") ?: "Minecraft PE")))
                details.addView(TextView(this).apply {
                    text=if(live) "● ĐANG MỞ" else "● CHƯA MỞ"
                    textSize=10f; setTextColor(if(live) Color.rgb(100,225,110) else Color.LTGRAY)
                })
                card.addView(details,LinearLayout.LayoutParams(0,-2,1f))
                if(d.getString("hostUid")==uid) {
                    val toggle=TextView(this@ChatBubbleService).apply {
                        text=if(live) "TẮT MAP" else "BẬT MAP"
                        textSize=10f; gravity=Gravity.CENTER; typeface=Typeface.DEFAULT_BOLD
                        setTextColor(Color.WHITE); setPadding(dp(8),dp(10),dp(8),dp(10))
                        background=GradientDrawable().apply {
                            setColor(if(live) Color.rgb(155,45,45) else Color.rgb(25,125,75))
                            cornerRadius=dp(8).toFloat()
                        }
                        setOnClickListener {
                            d.reference.update(mapOf("isLive" to !live, "lastHeartbeat" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
                                .addOnFailureListener { status?.text="Không đổi được trạng thái map. Kiểm tra Firestore Rules." }
                        }
                    }
                    card.addView(toggle,LinearLayout.LayoutParams(dp(70),dp(42)))
                }
                card.setOnClickListener {
                    selectedRoom=d.id
                    listenMessages(d.id)
                    status?.text="Đã chọn map: $n • Mở tab CHAT để nhắn tin"
                }
                box.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
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

    override fun onDestroy() { heartbeatHandler.removeCallbacks(heartbeatTask); closePanel(); bubble?.let { runCatching { wm.removeView(it) } }; bubble=null; super.onDestroy() }
    override fun onBind(intent:Intent?):IBinder?=null
}
