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
                        "rooms" -> RoomsPage()
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

@Composable
private fun RoomsPage() {
    Column(Modifier.fillMaxSize().background(PageBg).padding(20.dp)) {
        Text("Phòng chơi", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text("Tìm đồng đội và lên lịch chơi Minecraft PE.", color = Muted)
        Spacer(Modifier.height(20.dp))
        FeatureCard(Icons.Default.Cottage, "Minecraft PE", "Tính năng tìm và tham gia phòng đang được hoàn thiện.", Blue)
        Spacer(Modifier.height(12.dp))
        Text("Các phòng chơi sẽ sớm xuất hiện tại đây.", color = Muted, fontSize = 12.sp)
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
