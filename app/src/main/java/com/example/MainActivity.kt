package com.example

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.BoxBoyRepository
import com.example.data.TrashArcadeLaunchParams
import com.example.input.ControllerManager
import com.example.ui.AuthScreen
import com.example.ui.BioNoirSpriteAtlasRegistry
import com.example.ui.GameScreen
import com.example.ui.signOut
import com.example.ui.theme.MyApplicationTheme
import com.example.util.configureAppCheck
import com.example.viewmodel.BoxBoyViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

internal fun isFirebaseInitialized(context: Context): Boolean {
    return runCatching {
        FirebaseApp.getApps(context).isNotEmpty()
    }.getOrDefault(false)
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

internal fun parseTrashArcadeIntent(intent: Intent?): TrashArcadeLaunchParams {
    if (intent == null) return TrashArcadeLaunchParams()
    val extras = intent.extras
    val data = intent.data

    fun readParam(key: String): String? {
        val fromExtra = extras?.getString(key)?.takeIf { it.isNotBlank() }
        if (fromExtra != null) return fromExtra
        return if (data != null && data.isHierarchical) {
            data.getQueryParameter(key)?.takeIf { it.isNotBlank() }
        } else {
            null
        }
    }

    val autostartRaw = extras?.get("oasis_autostart")?.toString()
        ?: if (data != null && data.isHierarchical) data.getQueryParameter("oasis_autostart") else null
    val autostart = autostartRaw?.equals("true", ignoreCase = true) == true ||
        autostartRaw == "1"

    return TrashArcadeLaunchParams(
        oasisRoomCode = readParam("oasis_room_code"),
        oasisHostMode = readParam("oasis_host_mode"),
        oasisSubuserId = readParam("oasis_subuser_id"),
        oasisDisplayName = readParam("oasis_display_name"),
        oasisLdsTheme = readParam("oasis_lds_theme"),
        oasisAutostart = autostart
    )
}

class MainActivity : ComponentActivity() {

    private val launchParamsState = mutableStateOf(TrashArcadeLaunchParams())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        launchParamsState.value = parseTrashArcadeIntent(intent)

        enableEdgeToEdge()
        configurePixel8ProDisplay(targetFps = 120f)
        BioNoirSpriteAtlasRegistry.ensureInitialized(this)

        setContent {
            MyApplicationTheme {
                AppNavigation(launchParams = launchParamsState.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchParamsState.value = parseTrashArcadeIntent(intent)
    }

    internal fun configurePixel8ProDisplay(targetFps: Float = 120f) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val display = this.display
                if (display != null) {
                    val currentMode = display.mode
                    val best120Mode = display.supportedModes
                        .filter {
                            it.physicalWidth == currentMode.physicalWidth &&
                                it.physicalHeight == currentMode.physicalHeight
                        }
                        .maxByOrNull { it.refreshRate }
                    if (best120Mode != null && best120Mode.refreshRate >= 90f) {
                        window.attributes = window.attributes.apply {
                            preferredDisplayModeId = best120Mode.modeId
                            preferredRefreshRate = targetFps
                        }
                    }
                }
            }
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Let the platform and Compose view hierarchy process the event first.
        // This ensures Back navigation and focused UI elements (like text fields or modals)
        // consume the event before we treat it as a raw gameplay input.
        if (super.dispatchKeyEvent(event)) {
            return true
        }

        // If it wasn't consumed, pass it to the ControllerManager fallback.
        val action = event.action
        if (action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP) {
            val handledByGamepad = ControllerManager.onHardwareKeyEvent(
                keyCode = event.keyCode,
                isDown = action == KeyEvent.ACTION_DOWN,
                eventTimeMs = SystemClock.uptimeMillis()
            )
            if (handledByGamepad) {
                return true
            }
        }
        return false
    }
}

@Composable
fun AppNavigation(
    auth: FirebaseAuth? = null,
    launchParams: TrashArcadeLaunchParams = TrashArcadeLaunchParams()
) {
    val context = LocalContext.current
    val effectiveAuth: FirebaseAuth? = remember(auth, context) {
        auth ?: if (isFirebaseInitialized(context)) {
            runCatching { Firebase.auth }.getOrNull()
        } else {
            null
        }
    }
    val authFlow = remember(effectiveAuth) {
        effectiveAuth?.authStateFlow() ?: flowOf(null)
    }
    val currentUser by authFlow.collectAsStateWithLifecycle(
        initialValue = runCatching { effectiveAuth?.currentUser }.getOrNull()
    )
    val user = currentUser
    var isOfflineMode by rememberSaveable {
        mutableStateOf(true)
    }

    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    if (user == null && !isOfflineMode) {
        AuthScreen(
            onAuthSuccess = { isOfflineMode = false },
            onPlayOfflineClicked = { isOfflineMode = true }
        )
    } else {
        val isCloudAuthenticated = user != null
        val uid = user?.uid ?: launchParams.oasisSubuserId ?: "offline_local_player"
        val viewModel: BoxBoyViewModel = viewModel(
            key = "$uid-$isCloudAuthenticated",
            factory = viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY]) {
                        "APPLICATION_KEY missing from CreationExtras"
                    }
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val repo = if (isFirebaseInitialized(app)) {
                        runCatching {
                            val db = FirebaseFirestore.getInstance(databaseId)
                            BoxBoyRepository(db)
                        }.getOrNull()
                    } else {
                        null
                    }
                    val prefs = app.getSharedPreferences("boxboy_offline_saves", Context.MODE_PRIVATE)
                    BoxBoyViewModel(
                        repository = repo,
                        localPrefs = prefs,
                        currentUserId = uid,
                        isCloudAuthenticated = isCloudAuthenticated && repo != null
                    )
                }
            }
        )

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, viewModel) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> viewModel.onAppForeground()
                    Lifecycle.Event.ON_STOP -> viewModel.onAppBackground()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        LaunchedEffect(launchParams) {
            if (launchParams.hasDeepLinkOverrides) {
                viewModel.applyTrashArcadeLaunchParams(launchParams)
            }
        }

        GameScreen(
            viewModel = viewModel,
            onSignOutRequested = {
                if (user != null) {
                    signOut(
                        context = context,
                        credentialManager = credentialManager,
                        onSignOutComplete = { isOfflineMode = false },
                        scope = scope
                    )
                } else {
                    isOfflineMode = false
                }
            }
        )
    }
}
