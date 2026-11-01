package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.BoxBoyRepository
import com.example.ui.AuthScreen
import com.example.ui.GameScreen
import com.example.ui.signOut
import com.example.ui.theme.MyApplicationTheme
import com.example.util.configureAppCheck
import com.example.viewmodel.BoxBoyViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

import android.os.SystemClock
import android.view.KeyEvent
import com.example.input.ControllerManager

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                AppNavigation()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action = event.action
        if (action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP) {
            val handledByGamepad = ControllerManager.onHardwareKeyEvent(
                keyCode = event.keyCode,
                isDown = action == KeyEvent.ACTION_DOWN,
                eventTimeMs = SystemClock.uptimeMillis()
            )
            // Still pass through to super so Compose modals/key-bind capture can inspect it if needed,
            // or consume arrow/space keys so Android doesn't move focus highlight around buttons!
            if (handledByGamepad) {
                super.dispatchKeyEvent(event)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    var isOfflineMode by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    if (user == null && !isOfflineMode) {
        AuthScreen(
            onAuthSuccess = { isOfflineMode = false },
            onPlayOfflineClicked = { isOfflineMode = true }
        )
    } else {
        val isCloudAuthenticated = user != null
        val uid = user?.uid ?: "offline_local_player"
        val viewModel: BoxBoyViewModel = viewModel(
            key = "$uid-$isCloudAuthenticated",
            factory = viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY]) {
                        "APPLICATION_KEY missing from CreationExtras"
                    }
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    val prefs = app.getSharedPreferences("boxboy_offline_saves", Context.MODE_PRIVATE)
                    BoxBoyViewModel(
                        repository = BoxBoyRepository(db),
                        localPrefs = prefs,
                        currentUserId = uid,
                        isCloudAuthenticated = isCloudAuthenticated
                    )
                }
            }
        )

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
