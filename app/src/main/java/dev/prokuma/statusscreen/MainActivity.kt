package dev.prokuma.statusscreen

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alorma.compose.settings.storage.base.rememberStringSettingState
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dev.prokuma.statusscreen.ui.theme.StatusScreenTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.lang.Exception

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.windowInsetsController?.hide(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        } else {
            window.decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )
        }
        setContent {
            StatusScreenTheme {
                val navController = rememberNavController()
                val userStore = UserStore(LocalContext.current)
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    NavHost(navController = navController, startDestination = "main") {
                        composable(route = "main") {
                            Greeting(navController = navController, userStore = userStore)
                        }

                        composable(route = "settings") {
                            Settings(navController = navController, userStore = userStore)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
@SuppressLint("UnusedMaterialScaffoldPaddingParameter", "CoroutineCreationDuringComposition",
    "FlowOperatorInvokedInComposition"
)
@Composable
fun Greeting(navController: NavController, userStore: UserStore) {
    val context = LocalContext.current
    val db = Firebase.firestore
    val currentUser = remember { Firebase.auth.currentUser }
    val statusColor = remember { mutableStateOf(Color(0xFF42AFD6)) }
    val statusString = remember{ mutableStateOf("Could not Read") }
    val colorMap = arrayOf(
        Color(0xFF42AFD6), Color(0xFFBB75D9), Color(0xFF93E38F),
        Color(0xFFEDFBC1), Color(0xBFCBC2FF), Color(0xFF7FFFD4),
        Color(0xFF000000)
    )
    val statusTextColor = remember { mutableStateOf(Color.White) }
    var deviceId by remember { mutableStateOf("") }
    deviceId = userStore.getDeviceId.collectAsState(initial = "").value

    Scaffold (backgroundColor = statusColor.value) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 15.dp, vertical = 0.dp)
        ) {
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    Firebase.auth.signOut()
                }) {
                if (currentUser != null) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = Color.White
                    )
                }
            }
            IconButton(onClick = {
                navController.navigate("settings")
            }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                    tint = Color.White
                )
            }
        }
        Column (
            modifier = Modifier
                .fillMaxSize()
                .padding(15.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (currentUser != null)  {
                deviceId = userStore.getDeviceId.collectAsState(initial = "").value
                Log.d("MAIN", deviceId)
                try {
                    val docRef = db.collection("users/${currentUser.uid}/device_ids").document(deviceId)
                    docRef.addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            Log.w("MAIN", "Listen failed.", e)
                            return@addSnapshotListener
                        }

                        if (snapshot != null && snapshot.exists()) {
                            Log.d("MAIN", "${snapshot.data?.get("status")}")
                            val statusLong = snapshot.data?.get("status") as Long
                            statusColor.value = colorMap[statusLong.toInt()]
                            statusString.value = when (statusLong) {
                                0L -> "外出"
                                1L -> "テレワーク"
                                2L -> "帰宅"
                                3L -> "在席"
                                4L -> "移動中"
                                5L -> "その他"
                                else -> "Error Status"
                            }
                            statusTextColor.value = if (statusLong < 3L) Color.White else Color.Black

                        }
                    }
                } catch (e: Exception) {
                    Log.e("MAIN", e.toString())
                }
                Text(text = statusString.value,
                    style = TextStyle(
                        fontSize = 120.sp,
                        color = statusTextColor.value
                    )
                )
            } else {
                Button(onClick = {
                    signIn(context)
                }) {
                    Text("Login With Github")
                }
            }
        }
    }
}

@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Composable
fun Settings(navController: NavController, userStore: UserStore) {
    var popupControl by remember { mutableStateOf(false) }
    var deviceId by remember { mutableStateOf("") }
    deviceId = userStore.getDeviceId.collectAsState(initial = "").value

    Scaffold (backgroundColor = Color.White,
        topBar = {
            TopAppBar(title = {Text("設定")},
                navigationIcon = {
                    IconButton(onClick = {navController.navigate("main")}) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null)
                    }
                })
        }) {
        SettingsMenuLink(
            icon = { Icon(imageVector = Icons.Default.Edit, contentDescription = null) },
            title = {Text("デバイスID")},
            subtitle = {Text(deviceId)} ,
            onClick = {
                popupControl = true
            }
        )
        if (popupControl) {
            Dialog(onDismissRequest = {popupControl = false}) {
                Surface {
                    Column {
                        TextField(
                            value = deviceId,
                            onValueChange = {deviceId = it},
                            modifier = Modifier.padding(8.dp)
                        )
                        Box(
                            modifier = Modifier
                                .padding(8.dp)
                        ) {
                            TextButton(
                                onClick =
                                {
                                    popupControl = false
                                    CoroutineScope(Dispatchers.IO).launch {
                                        userStore.setDeviceId(deviceId)
                                    }
                                },
                                modifier = Modifier.align(Alignment.BottomEnd),
                            ) {
                                Text("保存")
                            }
                        }
                    }
                }
            }
        }
    }
}

fun signIn(context: Context) {
    val provider = OAuthProvider.newBuilder("github.com")
    provider.scopes = listOf("user:email")
    val pendingResultTask = Firebase.auth.pendingAuthResult
    if (pendingResultTask != null) {
        pendingResultTask.addOnSuccessListener {
            Toast.makeText(context, "User exist", Toast.LENGTH_LONG).show()
        }
        .addOnFailureListener {
            Toast.makeText(context, "Error: $it", Toast.LENGTH_LONG).show()
        }
    } else {
        Firebase.auth.startActivityForSignInWithProvider(context as Activity, provider.build())
            .addOnSuccessListener {
                Toast.makeText(context, "Login Successfully", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Error: $it", Toast.LENGTH_LONG).show()
            }
    }
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    StatusScreenTheme {
        Greeting(navController = rememberNavController(), userStore = UserStore(LocalContext.current))
    }
}