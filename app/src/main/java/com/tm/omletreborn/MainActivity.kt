package com.tm.omletreborn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

private val Navy = Color(0xFF071426)
private val Panel = Color(0xFF10233D)
private val Blue = Color(0xFF168BFF)
private val Cyan = Color(0xFF36C5F0)
private val Muted = Color(0xFFA9B9CC)
private val PageBg = Color(0xFF050D19)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OmletApp() }
    }
}

@Composable
fun OmletApp() {
    var page by remember { mutableStateOf("home") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val firebaseApp = remember(context) {
        FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
    }
    val authReady = firebaseApp != null
    var signedIn by remember { mutableStateOf(false) }
    var userEmail by remember { mutableStateOf("") }

    LaunchedEffect(authReady) {
        if (authReady) {
            val user = FirebaseAuth.getInstance().currentUser
            signedIn = user != null
            userEmail = user?.email.orEmpty()
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Blue, secondary = Cyan, background = PageBg,
            surface = Panel, onBackground = Color.White, onSurface = Color.White
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = PageBg) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(Navy).padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(44.dp).background(Blue, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SportsEsports, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Omlet Reborn", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text("CỘNG ĐỒNG GAME THỦ", color = Cyan, fontSize = 9.sp, letterSpacing = 1.4.sp)
                    }
                    IconButton(onClick = { page = "profile" }) {
                        Icon(Icons.Default.AccountCircle, "Tài khoản", tint = Color.White)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (page) {
                        "login" -> AuthPage(
                            email, { email = it }, password, { password = it }, message,
                            authReady, { message = it },
                            { isRegister ->
                                if (!authReady) {
                                    message = "Chưa tìm thấy cấu hình Firebase. Cần đặt google-services.json trong thư mục app/."
                                } else {
                                    val auth = FirebaseAuth.getInstance()
                                    val task = if (isRegister)
                                        auth.createUserWithEmailAndPassword(email.trim(), password)
                                    else auth.signInWithEmailAndPassword(email.trim(), password)
                                    task.addOnCompleteListener { result ->
                                        if (result.isSuccessful) {
                                            signedIn = true
                                            userEmail = auth.currentUser?.email.orEmpty()
                                            message = ""
                                            page = "profile"
                                        } else {
                                            message = firebaseError(result.exception)
                                        }
                                    }
                                }
                            }
                        )
                        "rooms" -> RoomsPage(signedIn, userEmail, { page = "login" })
                        "profile" -> ProfilePage(signedIn, userEmail, authReady,
                            { page = "login" }, {
                                if (authReady) FirebaseAuth.getInstance().signOut()
                                signedIn = false
                                userEmail = ""
                                page = "home"
                            })
                        else -> HomePage({ page = "rooms" }, { page = "login" })
                    }
                }
                NavigationBar(containerColor = Navy, contentColor = Color.White) {
                    listOf("home", "rooms", "profile").forEach { item ->
                        NavigationBarItem(
                            selected = page == item || (page == "login" && item == "profile"),
                            onClick = { page = item },
                            icon = {
                                Icon(when (item) {
                                    "home" -> Icons.Default.Home
                                    "rooms" -> Icons.Default.Groups
                                    else -> Icons.Default.Person
                                }, null)
                            },
                            label = { Text(when (item) {
                                "home" -> "Trang chủ"
                                "rooms" -> "Phòng chơi"
                                else -> "Tài khoản"
                            }) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Cyan, selectedTextColor = Cyan,
                                indicatorColor = Blue.copy(alpha = .20f),
                                unselectedIconColor = Muted, unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun firebaseError(error: Exception?): String {
    val code = (error as? com.google.firebase.auth.FirebaseAuthException)?.errorCode.orEmpty()
    return when {
        code.contains("INVALID_EMAIL") -> "Địa chỉ email không hợp lệ."
        code.contains("EMAIL_ALREADY_IN_USE") -> "Email này đã được đăng ký."
        code.contains("WEAK_PASSWORD") -> "Mật khẩu quá yếu. Hãy dùng ít nhất 6 ký tự."
        code.contains("USER_NOT_FOUND") || code.contains("INVALID_LOGIN_CREDENTIALS") ->
            "Email hoặc mật khẩu không chính xác."
        code.contains("WRONG_PASSWORD") -> "Mật khẩu không chính xác."
        code.contains("NETWORK") -> "Không có kết nối mạng. Hãy thử lại."
        code.contains("OPERATION_NOT_ALLOWED") -> "Firebase chưa bật đăng nhập bằng Email/Mật khẩu."
        else -> "Không thể xác thực lúc này. Kiểm tra cấu hình Firebase và thử lại."
    }
}

@Composable
private fun HomePage(onExplore: () -> Unit, onLogin: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().background(PageBg).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().background(
                    androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF123B69), Navy)),
                    RoundedCornerShape(26.dp)
                ).padding(24.dp)
            ) {
                Text("OMLET REBORN • PHIÊN BẢN CỘNG ĐỒNG", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                Text("Chơi Minecraft PE.\nKết nối bạn bè.", color = Color.White, fontSize = 30.sp, lineHeight = 37.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(12.dp))
                Text("Tìm đồng đội, tham gia phòng chơi và chia sẻ khoảnh khắc game của bạn.", color = Color(0xFFD2E1F3), fontSize = 14.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(22.dp))
                Button(onClick = onExplore, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("Khám phá phòng chơi")
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowForward, null)
                }
            }
        }
        item { Text("Khám phá Omlet", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
        item { FeatureCard(Icons.Default.Groups, "Phòng chơi", "Tìm người chơi và lập nhóm Minecraft PE.", Blue) }
        item { FeatureCard(Icons.Default.Videocam, "Khoảnh khắc & phát trực tiếp", "Không gian chia sẻ video đang được phát triển.", Color(0xFF8057E8)) }
        item {
            OutlinedButton(onClick = onLogin, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                Text("Đăng nhập / Tạo tài khoản")
            }
        }
    }
}

@Composable
private fun FeatureCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, tint: Color) {
    Row(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).background(tint.copy(alpha = .18f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(detail, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

private data class GameRoom(
    val id: String,
    val name: String,
    val game: String,
    val description: String,
    val hostUid: String,
    val hostEmail: String,
    val maxPlayers: Long,
    val members: List<String>
)

@Composable
private fun RoomsPage(signedIn: Boolean, userEmail: String, onLogin: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    val user = FirebaseAuth.getInstance().currentUser
    var rooms by remember { mutableStateOf<List<GameRoom>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var roomName by remember { mutableStateOf("") }
    var game by remember { mutableStateOf("Minecraft PE") }
    var description by remember { mutableStateOf("") }
    var maxPlayers by remember { mutableStateOf("8") }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(signedIn) {
        if (!signedIn) {
            rooms = emptyList()
            loading = false
            onDispose { }
        } else {
            val registration: ListenerRegistration = db.collection("rooms")
                .whereEqualTo("status", "open")
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        error = "Không tải được phòng: ${e.localizedMessage ?: "lỗi kết nối"}"
                        loading = false
                    } else {
                        rooms = snapshot?.documents?.mapNotNull { doc ->
                            val data = doc.data ?: return@mapNotNull null
                            GameRoom(
                                doc.id,
                                data["name"] as? String ?: "Phòng chưa đặt tên",
                                data["game"] as? String ?: "Minecraft PE",
                                data["description"] as? String ?: "",
                                data["hostUid"] as? String ?: "",
                                data["hostEmail"] as? String ?: "Người chơi",
                                (data["maxPlayers"] as? Number)?.toLong() ?: 8L,
                                (data["members"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                            )
                        } ?: emptyList()
                        loading = false
                        error = ""
                    }
                }
            onDispose { registration.remove() }
        }
    }

    Column(Modifier.fillMaxSize().background(PageBg).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Phòng chơi", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("Tạo phòng hoặc tìm đồng đội để chơi cùng.", color = Muted, fontSize = 12.sp)
            }
            Button(onClick = {
                if (signedIn) showCreate = true else onLogin()
            }, shape = RoundedCornerShape(13.dp)) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(5.dp))
                Text("Tạo phòng")
            }
        }
        Spacer(Modifier.height(14.dp))
        if (!signedIn) {
            FeatureCard(Icons.Default.Lock, "Đăng nhập để chơi cùng", "Bạn cần đăng nhập trước khi tạo hoặc tham gia phòng.", Blue)
            Spacer(Modifier.height(10.dp))
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Đăng nhập / Đăng ký") }
        } else {
            if (info.isNotBlank()) Text(info, color = Cyan, fontSize = 12.sp)
            if (error.isNotBlank()) Text(error, color = Color(0xFFFF8A80), fontSize = 12.sp)
            when {
                loading -> CircularProgressIndicator(color = Cyan, modifier = Modifier.align(Alignment.CenterHorizontally))
                rooms.isEmpty() -> Column(
                    Modifier.fillMaxWidth().padding(top = 42.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.MeetingRoom, null, tint = Muted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Chưa có phòng nào đang mở", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Hãy tạo phòng đầu tiên và mời bạn bè nhé.", color = Muted, fontSize = 12.sp)
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(rooms.size) { index ->
                        val room = rooms[index]
                        val joined = user?.uid in room.members
                        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SportsEsports, null, tint = Cyan)
                                Spacer(Modifier.width(8.dp))
                                Text(room.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                                Text("${room.members.size}/${room.maxPlayers}", color = Cyan, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(room.game + " • Chủ phòng: " + room.hostEmail.substringBefore("@"), color = Muted, fontSize = 11.sp)
                            if (room.description.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(room.description, color = Color(0xFFD2E1F3), fontSize = 13.sp)
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Mã: ${room.id.takeLast(6).uppercase()}", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                if (room.hostUid == user?.uid) {
                                    OutlinedButton(onClick = {
                                        db.collection("rooms").document(room.id).update("status", "closed")
                                            .addOnSuccessListener { info = "Đã đóng phòng." }
                                            .addOnFailureListener { error = it.localizedMessage ?: "Không đóng được phòng." }
                                    }) { Text("Đóng phòng") }
                                } else if (joined) {
                                    OutlinedButton(onClick = {
                                        db.collection("rooms").document(room.id).update("members", FieldValue.arrayRemove(user?.uid))
                                            .addOnSuccessListener { info = "Đã rời phòng." }
                                            .addOnFailureListener { error = it.localizedMessage ?: "Không thể rời phòng." }
                                    }) { Text("Rời phòng") }
                                } else {
                                    Button(
                                        enabled = !busy && room.members.size < room.maxPlayers.toInt(),
                                        onClick = {
                                            val uid = user?.uid ?: return@Button
                                            busy = true
                                            val ref = db.collection("rooms").document(room.id)
                                            db.runTransaction { tx ->
                                                val snap = tx.get(ref)
                                                val members = (snap.get("members") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                                                val cap = (snap.getLong("maxPlayers") ?: 8L).toInt()
                                                if (snap.getString("status") != "open") throw IllegalStateException("Phòng đã đóng.")
                                                if (uid !in members && members.size >= cap) throw IllegalStateException("Phòng đã đủ người.")
                                                if (uid !in members) tx.update(ref, "members", FieldValue.arrayUnion(uid))
                                            }.addOnSuccessListener { info = "Đã tham gia phòng!" ; busy = false }
                                                .addOnFailureListener { error = it.localizedMessage ?: "Không thể tham gia."; busy = false }
                                        }
                                    ) { Text(if (room.members.size >= room.maxPlayers) "Đã đầy" else "Tham gia") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        Dialog(onDismissRequest = { if (!busy) showCreate = false }) {
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(22.dp)).padding(20.dp)) {
                Text("Tạo phòng mới", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = roomName, onValueChange = { roomName = it.take(40) }, label = { Text("Tên phòng") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = game, onValueChange = { game = it.take(30) }, label = { Text("Game") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it.take(120) }, label = { Text("Mô tả (không bắt buộc)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = maxPlayers, onValueChange = { maxPlayers = it.filter(Char::isDigit).take(2) }, label = { Text("Số người tối đa (2–20)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showCreate = false }, modifier = Modifier.weight(1f), enabled = !busy) { Text("Hủy") }
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !busy && roomName.isNotBlank() && (maxPlayers.toIntOrNull() ?: 0) in 2..20,
                        onClick = {
                            val uid = user?.uid ?: return@Button
                            busy = true
                            val data = hashMapOf(
                                "name" to roomName.trim(),
                                "game" to game.trim().ifBlank { "Minecraft PE" },
                                "description" to description.trim(),
                                "hostUid" to uid,
                                "hostEmail" to (userEmail.ifBlank { "Người chơi" }),
                                "maxPlayers" to (maxPlayers.toLongOrNull() ?: 8L),
                                "members" to listOf(uid),
                                "status" to "open",
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                            db.collection("rooms").add(data)
                                .addOnSuccessListener {
                                    busy = false
                                    showCreate = false
                                    roomName = ""
                                    description = ""
                                    maxPlayers = "8"
                                    info = "Tạo phòng thành công! Mã phòng: ${it.id.takeLast(6).uppercase()}"
                                }
                                .addOnFailureListener {
                                    busy = false
                                    error = it.localizedMessage ?: "Không thể tạo phòng. Kiểm tra Firestore Rules."
                                }
                        }
                    ) { Text(if (busy) "Đang tạo..." else "Tạo") }
                }
            }
        }
    }
}

@Composable
private fun AuthPage(
    email: String, onEmail: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    message: String, authReady: Boolean, onMessage: (String) -> Unit,
    onSubmit: (Boolean) -> Unit
) {
    var register by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(PageBg).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (register) "Tạo tài khoản" else "Chào mừng trở lại", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Text("Đăng nhập để tiếp tục với Omlet Reborn.", color = Muted)
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            value = email, onValueChange = onEmail, modifier = Modifier.fillMaxWidth(),
            label = { Text("Địa chỉ email") }, singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = onPassword, modifier = Modifier.fillMaxWidth(),
            label = { Text("Mật khẩu") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { onMessage(""); onSubmit(register) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = email.contains("@") && password.length >= 6
        ) { Text(if (register) "Đăng ký" else "Đăng nhập") }
        TextButton(onClick = { register = !register; onMessage("") }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (register) "Đã có tài khoản? Đăng nhập" else "Chưa có tài khoản? Đăng ký")
        }
        if (message.isNotBlank()) Text(message, color = Color(0xFFFF8A80), fontSize = 12.sp)
        if (!authReady) {
            Text(
                "Firebase chưa sẵn sàng: kiểm tra tệp app/google-services.json và cấu hình ứng dụng Android.",
                color = Muted, fontSize = 11.sp, lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun ProfilePage(
    signedIn: Boolean, userEmail: String, authReady: Boolean,
    onLogin: () -> Unit, onSignOut: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(PageBg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(84.dp).background(Blue.copy(alpha = .20f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, null, tint = Cyan, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(if (signedIn) userEmail else "Tài khoản của bạn", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(if (signedIn) "Đã đăng nhập" else "Đăng nhập để sử dụng tài khoản.", color = Muted)
        Spacer(Modifier.height(20.dp))
        if (signedIn) Button(onClick = onSignOut) { Text("Đăng xuất") }
        else Button(onClick = onLogin) { Text("Đăng nhập / Đăng ký") }
        if (!authReady) Text("Dịch vụ tài khoản cần được cấu hình Firebase.", color = Muted, fontSize = 11.sp)
    }
}
