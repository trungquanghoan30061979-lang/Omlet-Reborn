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
    private var trashTarget: TextView? = null
    private var trashParams: WindowManager.LayoutParams? = null
    private var panel: View? = null
    private var roomListener: ListenerRegistration? = null
    private var messageListener: ListenerRegistration? = null
    private var friendsListener: ListenerRegistration? = null
    private var privateMessageListener: ListenerRegistration? = null
    private var selectedRoom: String? = null
    private var selectedPrivateFriend: Pair<String, String>? = null
    private var roomList: LinearLayout? = null
    private var chatList: LinearLayout? = null
    private var roomChatTabs: LinearLayout? = null
    private var roomMessageDocs: List<com.google.firebase.firestore.DocumentSnapshot> = emptyList()
    private var roomMemberUids: List<String> = emptyList()
    private var roomMemberNames: Map<String, String> = emptyMap()
    private var selectedChatMemberUid: String? = null
    private var messageInput: EditText? = null
    private var privateFriendsList: LinearLayout? = null
    private var privateChatHome: LinearLayout? = null
    private var privateChatArea: LinearLayout? = null
    private var privateMessagesList: LinearLayout? = null
    private var privateMessageInput: EditText? = null
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
        fun showTrash() {
            if(trashTarget!=null) return
            val target=TextView(this).apply {
                text="×"; textSize=34f; gravity=Gravity.CENTER
                setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD
                background=GradientDrawable().apply {
                    shape=GradientDrawable.OVAL
                    setColor(Color.rgb(205,48,62))
                    setStroke(dp(2),Color.WHITE)
                }
            }
            val p=lp(dp(66),dp(66)).apply {
                gravity=Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y=dp(34)
            }
            trashTarget=target; trashParams=p
            runCatching { wm.addView(target,p) }.onFailure { trashTarget=null; trashParams=null }
        }
        fun hideTrash() {
            trashTarget?.let { runCatching { wm.removeView(it) } }
            trashTarget=null; trashParams=null
        }
        v.setOnTouchListener { _, e ->
            when(e.action) {
                MotionEvent.ACTION_DOWN -> { sx=e.rawX; sy=e.rawY; ox=bubbleParams.x; oy=bubbleParams.y; moved=false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx=(e.rawX-sx).toInt(); val dy=(e.rawY-sy).toInt()
                    if(abs(dx)>dp(5)||abs(dy)>dp(5)) { moved=true; showTrash() }
                    bubbleParams.x=(ox+dx).coerceIn(0, resources.displayMetrics.widthPixels-dp(58))
                    bubbleParams.y=(oy+dy).coerceIn(0, resources.displayMetrics.heightPixels-dp(58))
                    runCatching { wm.updateViewLayout(v,bubbleParams) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val nearTrash = moved && e.rawY >= resources.displayMetrics.heightPixels-dp(145)
                    hideTrash()
                    if(nearTrash) {
                        closePanel()
                        bubble?.let { runCatching { wm.removeView(it) } }; bubble=null
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else if(!moved) openPanel()
                    true
                }
                MotionEvent.ACTION_CANCEL -> { hideTrash(); true }
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
            orientation=LinearLayout.VERTICAL
            clipToOutline=true
            setPadding(dp(16),dp(12),dp(16),dp(12))
            background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(3,5,15),Color.rgb(8,13,42),Color.rgb(35,47,145)))
        }

        // Reference layout: logo + three icon controls + close button in the top row.
        val header=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        val logo=TextView(this).apply {
            text="OMLET REBORN"
            textSize=if(wide) 25f else 20f
            typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
            gravity=Gravity.CENTER
            setTextColor(Color.rgb(45,112,255))
            background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(5,13,42),Color.rgb(29,39,112))).apply {
                cornerRadius=dp(18).toFloat()
            }
        }
        header.addView(logo,LinearLayout.LayoutParams(0,dp(66),1f).apply { rightMargin=dp(12) })
        val nav=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        val title=TextView(this).apply {
            text="CÁC THẾ GIỚI"; textSize=if(wide) 21f else 17f
            typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
            setTextColor(Color.WHITE); gravity=Gravity.CENTER
        }
        val right=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(8),0,0,0)
        }
        val content=FrameLayout(this)
        val worldPage=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val chatPage=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; visibility=View.GONE }
        val mapPage=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; visibility=View.GONE }

        lateinit var selectTab:(Int)->Unit
        fun navIcon(iconRes:Int, selected:Boolean, action:()->Unit):ImageView = ImageView(this).apply {
            setImageResource(iconRes)
            scaleType=ImageView.ScaleType.FIT_CENTER
            setPadding(dp(7),dp(7),dp(7),dp(7))
            background=GradientDrawable().apply {
                setColor(if(selected) Color.rgb(22,52,150) else Color.rgb(7,12,37))
                cornerRadius=dp(13).toFloat()
                if(selected) setStroke(dp(2),Color.WHITE)
            }
            setOnClickListener { action() }
        }
        fun renderNav(which:Int) {
            nav.removeAllViews()
            nav.addView(navIcon(R.drawable.world_art,which==0){selectTab(0)},
                LinearLayout.LayoutParams(dp(58),dp(58)).apply { rightMargin=dp(8) })
            nav.addView(navIcon(R.drawable.chat_tab_icon,which==1){selectTab(1)},
                LinearLayout.LayoutParams(dp(58),dp(58)).apply { rightMargin=dp(8) })
            nav.addView(navIcon(R.drawable.create_map_tab_icon,which==2){selectTab(2)},
                LinearLayout.LayoutParams(dp(58),dp(58)).apply { rightMargin=dp(8) })
            nav.addView(TextView(this).apply {
                text="X"; textSize=28f; gravity=Gravity.CENTER
                typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(45,112,205))
                background=GradientDrawable().apply {
                    setColor(Color.rgb(215,229,248)); cornerRadius=dp(13).toFloat()
                }
                setOnClickListener { closePanel() }
            },LinearLayout.LayoutParams(dp(40),dp(40)))
        }
        selectTab = { which ->
            worldPage.visibility=if(which==0) View.VISIBLE else View.GONE
            chatPage.visibility=if(which==1) View.VISIBLE else View.GONE
            mapPage.visibility=if(which==2) View.VISIBLE else View.GONE
            title.text=when(which) { 1->"CHAT"; 2->"TẠO MAP"; else->"CÁC THẾ GIỚI" }
            renderNav(which)
            if(which==1) listenFriends()
        }
        renderNav(0)
        header.addView(nav,LinearLayout.LayoutParams(-2,dp(66)))
        root.addView(header,LinearLayout.LayoutParams(-1,dp(72)))

        val body=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            setPadding(0,dp(10),0,0)
        }
        val hero=FrameLayout(this).apply {
            background=GradientDrawable().apply { setColor(Color.BLACK); cornerRadius=dp(17).toFloat() }
            clipToOutline=true
        }
        hero.addView(ImageView(this).apply {
            setImageResource(R.drawable.minecraft_background)
            scaleType=ImageView.ScaleType.CENTER_CROP
        },FrameLayout.LayoutParams(-1,-1))
        hero.addView(View(this).apply { setBackgroundColor(Color.argb(28,0,0,0)) },
            FrameLayout.LayoutParams(-1,-1))
        hero.addView(TextView(this).apply {
            text="MINECRAFT"; textSize=if(wide) 29f else 23f
            typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
            setTextColor(Color.WHITE); gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(18),0,dp(12),dp(10))
            setShadowLayer(dp(4).toFloat(),0f,dp(2).toFloat(),Color.BLACK)
        },FrameLayout.LayoutParams(-1,dp(62),Gravity.BOTTOM))
        body.addView(hero,LinearLayout.LayoutParams(0,-1,0.59f).apply { rightMargin=dp(12) })
        right.addView(title,LinearLayout.LayoutParams(-1,dp(46)))
        status=label("Đang tải map từ máy chủ…")
        worldPage.addView(status)
        val rs=ScrollView(this)
        roomList=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        rs.addView(roomList)
        worldPage.addView(rs,LinearLayout.LayoutParams(-1,0,1f))

        privateChatHome=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
        }
        privateChatHome!!.addView(TextView(this).apply {
            text="CHAT CHUNG"; textSize=18f; typeface=Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); setPadding(0,dp(3),0,dp(5))
        })
        privateChatHome!!.addView(label("TIN NHẮN TRONG MAP ĐANG CHỌN"))
        val memberTabsScroll=HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false }
        roomChatTabs=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        memberTabsScroll.addView(roomChatTabs)
        privateChatHome!!.addView(memberTabsScroll,LinearLayout.LayoutParams(-1,dp(42)))
        val cs=ScrollView(this)
        chatList=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        cs.addView(chatList)
        privateChatHome!!.addView(cs,LinearLayout.LayoutParams(-1,0,0.42f))
        val send=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        messageInput=EditText(this).apply {
            hint="Nhắn tin chung…"; setSingleLine(); textSize=13f
            setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
        }
        send.addView(messageInput,LinearLayout.LayoutParams(0,dp(42),1f))
        send.addView(TextView(this).apply {
            text="GỬI"; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
            setPadding(dp(10),0,dp(10),0)
            background=GradientDrawable().apply { setColor(Color.rgb(25,95,210)); cornerRadius=dp(8).toFloat() }
            setOnClickListener { sendMessage() }
        },LinearLayout.LayoutParams(dp(58),dp(42)))
        privateChatHome!!.addView(send)
        privateChatHome!!.addView(View(this).apply {
            setBackgroundColor(Color.argb(130,190,205,235))
        },LinearLayout.LayoutParams(-1,dp(1)).apply { topMargin=dp(7); bottomMargin=dp(7) })
        privateChatHome!!.addView(TextView(this).apply {
            text="CHAT RIÊNG"; textSize=18f; typeface=Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(205,215,245)); setPadding(0,dp(2),0,dp(5))
        })
        val friendsScroll=ScrollView(this)
        privateFriendsList=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        friendsScroll.addView(privateFriendsList)
        privateChatHome!!.addView(friendsScroll,LinearLayout.LayoutParams(-1,0,0.58f))
        chatPage.addView(privateChatHome,LinearLayout.LayoutParams(-1,-1))

        privateChatArea=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            visibility=View.GONE
        }
        chatPage.addView(privateChatArea,LinearLayout.LayoutParams(-1,-1))

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
        body.addView(right,LinearLayout.LayoutParams(0,-1,0.41f))
        root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        panel=root
        bubble?.visibility=View.GONE
        // Cửa sổ nổi ở giữa màn hình, giữ lại phần Minecraft phía sau.
        val panelWidth=screenW
        val panelHeight=screenH
        runCatching {
            wm.addView(root,lp(panelWidth,panelHeight,true).apply {
                gravity=Gravity.TOP or Gravity.START; x=0; y=0
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
            selectedRoom?.let { selectedId ->
                all.firstOrNull { it.id==selectedId }?.let { selectedDoc ->
                    roomMemberUids=(selectedDoc.get("members") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    roomMemberNames=(selectedDoc.get("memberNames") as? Map<*,*>)?.entries
                        ?.mapNotNull { (k,v) -> if(k is String && v is String) k to v else null }?.toMap().orEmpty()
                    renderRoomChatTabs()
                }
            }
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
                // Không hiển thị avatar/biểu tượng riêng cho từng map.
                val details=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(4),0,dp(8),0) }
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
                    roomMemberUids=(d.get("members") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    roomMemberNames=(d.get("memberNames") as? Map<*,*>)?.entries
                        ?.mapNotNull { (k,v) -> if(k is String && v is String) k to v else null }?.toMap().orEmpty()
                    selectedChatMemberUid=null
                    renderRoomChatTabs()
                    listenMessages(d.id)
                    status?.text="Đã chọn map: $n • Mở tab CHAT để nhắn tin"
                }
                box.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
            }
        }
    }

    private fun renderRoomChatTabs() {
        val tabs=roomChatTabs ?: return
        tabs.removeAllViews()
        fun addTab(caption:String, memberUid:String?) {
            val selected=selectedChatMemberUid==memberUid
            val chip=TextView(this).apply {
                text=caption
                textSize=11f
                typeface=Typeface.DEFAULT_BOLD
                gravity=Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(dp(13),dp(7),dp(13),dp(7))
                background=GradientDrawable().apply {
                    setColor(if(selected) Color.rgb(25,83,195) else Color.rgb(12,22,52))
                    cornerRadius=dp(10).toFloat()
                    if(selected) setStroke(dp(1),Color.WHITE)
                }
                setOnClickListener {
                    selectedChatMemberUid=memberUid
                    renderRoomChatTabs()
                    renderRoomMessages()
                }
            }
            tabs.addView(chip,LinearLayout.LayoutParams(-2,dp(36)).apply { rightMargin=dp(6) })
        }
        addTab("TẤT CẢ",null)
        roomMemberUids.forEach { memberUid ->
            val accountName=roomMemberNames[memberUid]
                ?: if(memberUid==FirebaseAuth.getInstance().currentUser?.uid) "Bạn"
                else "Thành viên ${memberUid.takeLast(4)}"
            addTab(accountName,memberUid)
        }
    }

    private fun renderRoomMessages() {
        val box=chatList ?: return
        box.removeAllViews()
        val filtered=if(selectedChatMemberUid==null) roomMessageDocs
            else roomMessageDocs.filter { it.getString("senderUid")==selectedChatMemberUid }
        if(filtered.isEmpty()) {
            box.addView(label(if(selectedChatMemberUid==null) "Chưa có tin nhắn trong map." else "Thành viên này chưa gửi tin nhắn."))
            return
        }
        filtered.forEach { doc ->
            val uid=doc.getString("senderUid").orEmpty()
            val name=doc.getString("senderName") ?: roomMemberNames[uid] ?: "Người chơi"
            box.addView(label("$name: ${doc.getString("text") ?: ""}"))
        }
    }

    private fun listenMessages(id:String) {
        messageListener?.remove(); selectedRoom=id; chatList?.removeAllViews()
        messageListener=FirebaseFirestore.getInstance().collection("rooms").document(id).collection("messages")
            .orderBy("createdAt").limit(100).addSnapshotListener { snap,err ->
                if(err!=null) {
                    chatList?.removeAllViews()
                    chatList?.addView(label("Chat chưa được cấp quyền Firestore."))
                    return@addSnapshotListener
                }
                roomMessageDocs=snap?.documents.orEmpty()
                renderRoomMessages()
            }
    }

    private fun listenFriends() {
        val uid=FirebaseAuth.getInstance().currentUser?.uid
        val box=privateFriendsList ?: return
        box.removeAllViews()
        if(uid.isNullOrBlank()) {
            box.addView(label("Đăng nhập Omlet Reborn để xem bạn bè."))
            return
        }
        friendsListener?.remove()
        friendsListener=FirebaseFirestore.getInstance().collection("friendships")
            .whereArrayContains("memberUids",uid)
            .addSnapshotListener { snap,err ->
                val list=privateFriendsList ?: return@addSnapshotListener
                list.removeAllViews()
                if(err!=null) {
                    list.addView(label("Không tải được danh sách bạn bè. Kiểm tra Firestore Rules."))
                    return@addSnapshotListener
                }
                val friends=snap?.documents.orEmpty().mapNotNull { doc ->
                    val ids=(doc.get("memberUids") as? List<*>)?.filterIsInstance<String>() ?: return@mapNotNull null
                    val other=ids.firstOrNull { it!=uid } ?: return@mapNotNull null
                    val names=doc.get("memberNames") as? Map<*,*>
                    val name=names?.get(other) as? String ?: "Người chơi"
                    other to name
                }
                if(friends.isEmpty()) {
                    list.addView(label("Chưa có bạn bè. Hãy kết bạn trong tab Chat của ứng dụng Omlet Reborn."))
                } else friends.forEach { (friendUid,friendName) ->
                    val row=LinearLayout(this).apply {
                        orientation=LinearLayout.HORIZONTAL
                        gravity=Gravity.CENTER_VERTICAL
                        setPadding(dp(9),dp(7),dp(9),dp(7))
                        background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                            intArrayOf(Color.rgb(5,8,15),Color.rgb(58,62,72))).apply {
                            cornerRadius=dp(13).toFloat()
                        }
                    }
                    row.addView(TextView(this).apply {
                        text=friendName.take(1).uppercase()
                        textSize=21f; gravity=Gravity.CENTER; typeface=Typeface.DEFAULT_BOLD
                        setTextColor(Color.WHITE)
                        background=GradientDrawable().apply {
                            setColor(Color.rgb(30,155,75)); cornerRadius=dp(10).toFloat()
                        }
                    },LinearLayout.LayoutParams(dp(48),dp(48)))
                    row.addView(TextView(this).apply {
                        text=friendName.uppercase()
                        textSize=14f; typeface=Typeface.DEFAULT_BOLD
                        setTextColor(Color.WHITE); gravity=Gravity.CENTER_VERTICAL
                        setPadding(dp(9),0,dp(6),0)
                    },LinearLayout.LayoutParams(0,dp(48),1f))
                    row.addView(TextView(this).apply {
                        text="CHAT"; textSize=11f; gravity=Gravity.CENTER
                        typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
                        setPadding(dp(12),dp(10),dp(12),dp(10))
                        background=GradientDrawable().apply {
                            setColor(Color.rgb(25,95,210)); cornerRadius=dp(8).toFloat()
                        }
                    })
                    row.setOnClickListener { openPrivateChat(friendUid,friendName) }
                    list.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(7) })
                }
            }
    }

    private fun openPrivateChat(friendUid:String,friendName:String) {
        val uid=FirebaseAuth.getInstance().currentUser?.uid ?: return
        selectedPrivateFriend=friendUid to friendName
        privateChatHome?.visibility=View.GONE
        val area=privateChatArea ?: return
        area.removeAllViews()
        area.visibility=View.VISIBLE
        val header=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text="‹"; textSize=30f; gravity=Gravity.CENTER
            setTextColor(Color.WHITE)
            setOnClickListener {
                privateMessageListener?.remove(); privateMessageListener=null
                selectedPrivateFriend=null
                area.visibility=View.GONE
                privateChatHome?.visibility=View.VISIBLE
            }
        },LinearLayout.LayoutParams(dp(42),dp(48)))
        header.addView(TextView(this).apply {
            text=friendName; textSize=17f; typeface=Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity=Gravity.CENTER_VERTICAL
        },LinearLayout.LayoutParams(0,dp(48),1f))
        header.addView(label("CHAT RIÊNG"))
        area.addView(header)

        val messagesScroll=ScrollView(this)
        privateMessagesList=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(7),dp(8),dp(7),dp(8))
        }
        messagesScroll.addView(privateMessagesList)
        area.addView(messagesScroll,LinearLayout.LayoutParams(-1,0,1f))

        val sendRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        privateMessageInput=EditText(this).apply {
            hint="Nhắn tin riêng…"; setSingleLine(); textSize=13f
            setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
        }
        sendRow.addView(privateMessageInput,LinearLayout.LayoutParams(0,dp(44),1f))
        sendRow.addView(TextView(this).apply {
            text="GỬI"; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
            typeface=Typeface.DEFAULT_BOLD
            background=GradientDrawable().apply { setColor(Color.rgb(25,95,210)); cornerRadius=dp(8).toFloat() }
            setOnClickListener { sendPrivateMessage() }
        },LinearLayout.LayoutParams(dp(60),dp(44)))
        area.addView(sendRow)

        val cid=listOf(uid,friendUid).sorted().joinToString("_")
        val names=mapOf(uid to (FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@") ?: "Người chơi"),friendUid to friendName)
        FirebaseFirestore.getInstance().collection("conversations").document(cid)
            .set(mapOf("memberUids" to listOf(uid,friendUid).sorted(),"memberNames" to names),
                com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                if(selectedPrivateFriend?.first==friendUid) listenPrivateMessages(cid)
            }
            .addOnFailureListener { privateMessagesList?.addView(label("Không mở được cuộc trò chuyện. Kiểm tra Firestore Rules.")) }
    }

    private fun listenPrivateMessages(conversationId:String) {
        privateMessageListener?.remove()
        privateMessagesList?.removeAllViews()
        privateMessageListener=FirebaseFirestore.getInstance().collection("conversations")
            .document(conversationId).collection("messages")
            .orderBy("createdAt").limit(100)
            .addSnapshotListener { snap,err ->
                val box=privateMessagesList ?: return@addSnapshotListener
                box.removeAllViews()
                if(err!=null) {
                    box.addView(label("Không tải được tin nhắn riêng."))
                    return@addSnapshotListener
                }
                val myUid=FirebaseAuth.getInstance().currentUser?.uid
                snap?.documents?.forEach { doc ->
                    val mine=doc.getString("senderUid")==myUid
                    val bubble=TextView(this).apply {
                        text=(if(mine) "Bạn: " else (doc.getString("senderName") ?: "Bạn bè")+": ")+
                            (doc.getString("text") ?: "")
                        textSize=13f; setTextColor(Color.WHITE)
                        setPadding(dp(10),dp(8),dp(10),dp(8))
                        background=GradientDrawable().apply {
                            setColor(if(mine) Color.rgb(25,85,185) else Color.rgb(48,55,75))
                            cornerRadius=dp(11).toFloat()
                        }
                    }
                    box.addView(bubble,LinearLayout.LayoutParams(-1,-2).apply {
                        bottomMargin=dp(6)
                        leftMargin=if(mine) dp(35) else 0
                        rightMargin=if(mine) 0 else dp(35)
                    })
                }
            }
    }

    private fun sendPrivateMessage() {
        val uid=FirebaseAuth.getInstance().currentUser?.uid ?: return
        val friend=selectedPrivateFriend ?: return
        val text=privateMessageInput?.text?.toString()?.trim().orEmpty().take(300)
        if(text.isBlank()) return
        val cid=listOf(uid,friend.first).sorted().joinToString("_")
        val db=FirebaseFirestore.getInstance()
        val conversation=db.collection("conversations").document(cid)
        val myName=FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@") ?: "Người chơi"
        conversation.set(mapOf(
            "memberUids" to listOf(uid,friend.first).sorted(),
            "memberNames" to mapOf(uid to myName,friend.first to friend.second)
        ),com.google.firebase.firestore.SetOptions.merge())
            .continueWithTask {
                conversation.collection("messages").add(mapOf(
                    "senderUid" to uid,
                    "senderName" to myName,
                    "text" to text,
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ))
            }
            .addOnSuccessListener { privateMessageInput?.setText("") }
            .addOnFailureListener { privateMessagesList?.addView(label("Gửi tin nhắn thất bại. Kiểm tra Firestore Rules.")) }
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
        friendsListener?.remove(); friendsListener=null
        privateMessageListener?.remove(); privateMessageListener=null
        selectedPrivateFriend=null
        panel?.let { runCatching { wm.removeView(it) } }; panel=null
        roomList=null; chatList=null; messageInput=null; status=null
        roomChatTabs=null; roomMessageDocs=emptyList(); roomMemberUids=emptyList()
        roomMemberNames=emptyMap(); selectedChatMemberUid=null
        privateFriendsList=null; privateChatHome=null; privateChatArea=null
        privateMessagesList=null; privateMessageInput=null
        bubble?.visibility=View.VISIBLE
    }

    override fun onDestroy() { heartbeatHandler.removeCallbacks(heartbeatTask); closePanel(); bubble?.let { runCatching { wm.removeView(it) } }; bubble=null; super.onDestroy() }
    override fun onBind(intent:Intent?):IBinder?=null
}
