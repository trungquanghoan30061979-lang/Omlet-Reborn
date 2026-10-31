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

private val Navy = Color(0xFF101C3A)
private val Blue = Color(0xFF3978F6)
private val Pale = Color(0xFFF3F6FF)
private val Muted = Color(0xFF7B8499)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OmletApp() }
    }
}

@Composable
fun OmletApp() {
    var page by remember { mutableStateOf("Home") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var signedIn by remember { mutableStateOf(false) }
    var userEmail by remember { mutableStateOf("") }
    val authReady = remember { FirebaseApp.getApps(androidx.compose.ui.platform.LocalContext.current).isNotEmpty() }

    MaterialTheme(colorScheme = lightColorScheme(primary = Blue, background = Pale, surface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = Pale) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(42.dp).background(Blue, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SportsEsports, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Omlet Reborn", color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                        Text("YOUR MOBILE GAMING HUB", color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
                    }
                    IconButton(onClick = { page = "Profile" }) { Icon(Icons.Default.AccountCircle, "Profile", tint = Navy) }
                }
                when (page) {
                    "Login" -> AuthPage(email, { email = it }, password, { password = it }, message, {
                        message = it
                    }, authReady, { isRegister ->
                        if (!authReady) {
                            message = "Firebase chưa được cấu hình. Hãy thêm app/google-services.json."
                        } else {
                            val auth = FirebaseAuth.getInstance()
                            val task = if (isRegister) auth.createUserWithEmailAndPassword(email.trim(), password)
                                       else auth.signInWithEmailAndPassword(email.trim(), password)
                            task.addOnCompleteListener { result ->
                                if (result.isSuccessful) {
                                    signedIn = true
                                    userEmail = auth.currentUser?.email.orEmpty()
                                    message = ""
                                    page = "Profile"
                                } else message = result.exception?.localizedMessage ?: "Không thể đăng nhập."
                            }
                        }
                    })
                    "Rooms" -> RoomsPage()
                    "Profile" -> ProfilePage(signedIn, userEmail, authReady, { page = "Login" }, {
                        if (authReady) FirebaseAuth.getInstance().signOut()
                        signedIn = false; userEmail = ""; page = "Home"
                    })
                    else -> HomePage(onExplore = { page = "Rooms" }, onLogin = { page = "Login" })
                }
                Spacer(Modifier.weight(1f))
                NavigationBar(containerColor = Color.White) {
                    listOf("Home", "Rooms", "Profile").forEach { item ->
                        NavigationBarItem(
                            selected = page == item || (page == "Login" && item == "Profile"),
                            onClick = { page = item },
                            icon = { Icon(when(item) { "Home" -> Icons.Default.Home; "Rooms" -> Icons.Default.Groups; else -> Icons.Default.Person }, null) },
                            label = { Text(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePage(onExplore: () -> Unit, onLogin: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(Navy, RoundedCornerShape(28.dp)).padding(24.dp)) {
                Text("YOUR MOBILE GAMING HUB", color = Color(0xFF9DBBFF), fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("Minecraft PE,\nmade social.", color = Color.White, fontSize = 31.sp, lineHeight = 37.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                Text("Capture moments, meet players and build your next session.", color = Color(0xFFD6DDF0), fontSize = 14.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(22.dp))
                Button(onClick = onExplore, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) {
                    Text("Explore rooms"); Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ArrowForward, null)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Find your next session", color = Navy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        item { FeatureCard(Icons.Default.Groups, "Gaming rooms", "Meet players and find people to play with.", Blue) }
        item { FeatureCard(Icons.Default.Videocam, "Moments & streaming", "A place for your gaming highlights is coming.", Color(0xFF8057E8)) }
        item { OutlinedButton(onClick = onLogin, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) { Text("Sign in / Create account") } }
    }
}

@Composable
private fun FeatureCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, tint: Color) {
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).background(tint.copy(alpha = .12f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Navy, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(detail, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RoomsPage() {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Gaming rooms", color = Navy, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text("Find your squad and plan your next Minecraft PE session.", color = Muted)
        Spacer(Modifier.height(20.dp))
        FeatureCard(Icons.Default.Cottage, "Minecraft PE", "Room discovery will be connected to the online service next.", Blue)
        Spacer(Modifier.height(12.dp))
        Text("Rooms are being prepared.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun AuthPage(email: String, onEmail: (String) -> Unit, password: String, onPassword: (String) -> Unit,
                     message: String, onMessage: (String) -> Unit, authReady: Boolean, onSubmit: (Boolean) -> Unit) {
    var register by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(if (register) "Create your account" else "Welcome back", color = Navy, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        Text("Sign in to continue with Omlet Reborn.", color = Muted)
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(email, onEmail, modifier = Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, onPassword, modifier = Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(18.dp))
        Button(onClick = { onMessage(""); onSubmit(register) }, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = email.contains("@") && password.length >= 6) {
            Text(if (register) "Create account" else "Sign in")
        }
        TextButton(onClick = { register = !register; onMessage("") }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (register) "Already have an account? Sign in" else "New here? Create account")
        }
        if (message.isNotBlank()) Text(message, color = Color(0xFFB3261E), fontSize = 12.sp)
        if (!authReady) Text("Firebase setup pending: add google-services.json to app/ to enable real accounts.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun ProfilePage(signedIn: Boolean, userEmail: String, authReady: Boolean, onLogin: () -> Unit, onSignOut: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(84.dp).background(Blue.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, null, tint = Blue, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(if (signedIn) userEmail else "Your profile", color = Navy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(if (signedIn) "Signed in" else "Sign in to access your account.", color = Muted)
        Spacer(Modifier.height(20.dp))
        if (signedIn) Button(onClick = onSignOut) { Text("Sign out") } else Button(onClick = onLogin) { Text("Sign in / Register") }
        if (!authReady) Text("Account service needs Firebase configuration.", color = Muted, fontSize = 11.sp)
    }
}
