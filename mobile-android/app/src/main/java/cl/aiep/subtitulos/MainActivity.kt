package cl.aiep.subtitulos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cl.aiep.subtitulos.audio.MicrophoneStreamingService
import cl.aiep.subtitulos.prefs.AppPreferences
import cl.aiep.subtitulos.qr.QrParser
import cl.aiep.subtitulos.qr.QrPayload
import cl.aiep.subtitulos.sessions.SessionsRepository
import cl.aiep.subtitulos.ui.AiepBottomNav
import cl.aiep.subtitulos.ui.AiepTab
import cl.aiep.subtitulos.ui.SessionDetailScreen
import cl.aiep.subtitulos.ui.SessionsListScreen
import cl.aiep.subtitulos.ui.SettingsScreen
import cl.aiep.subtitulos.ui.theme.AiepCream
import cl.aiep.subtitulos.ui.theme.AiepSubtitulosTheme
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var pendingStart: (() -> Unit)? = null

    private val scanner by lazy {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(this, options)
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (micGranted) pendingStart?.invoke()
            pendingStart = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefetchScannerModule()
        SessionsRepository.get(this)
        setContent {
            AiepSubtitulosTheme {
                AiepNavRoot(
                    onStart = { relayUrl, sessionId, mode, localSessionId, localOnly ->
                        startAfterPermissions(relayUrl, sessionId, mode, localSessionId, localOnly)
                    },
                    onStop = {
                        stopService(Intent(this, MicrophoneStreamingService::class.java))
                    },
                    onScanQr = { onResult -> launchScanner(onResult) },
                )
            }
        }
    }

    private fun prefetchScannerModule() {
        runCatching {
            val request = ModuleInstallRequest.newBuilder()
                .addApi(scanner)
                .build()
            ModuleInstall.getClient(this)
                .installModules(request)
                .addOnFailureListener { Log.w(LOG_TAG, "Scanner module install failed", it) }
        }
    }

    private fun launchScanner(onResult: (QrPayload) -> Unit) {
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val payload = QrParser.parse(barcode.rawValue)
                if (payload == null) {
                    Toast.makeText(this, R.string.scan_qr_error_invalid, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.scan_qr_session_loaded, payload.sessionId),
                        Toast.LENGTH_SHORT,
                    ).show()
                    onResult(payload)
                }
            }
            .addOnCanceledListener { }
            .addOnFailureListener { error ->
                Log.w(LOG_TAG, "QR scan failed", error)
                Toast.makeText(this, R.string.scan_qr_error_unavailable, Toast.LENGTH_LONG).show()
            }
    }

    private fun startAfterPermissions(
        relayUrl: String,
        sessionId: String,
        mode: CaptureMode,
        localSessionId: String,
        localOnly: Boolean,
    ) {
        pendingStart = {
            val intent = MicrophoneStreamingService.startIntent(
                this,
                relayUrl,
                sessionId,
                mode,
                localSessionId,
                localOnly,
            )
            ContextCompat.startForegroundService(this, intent)
        }

        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissions.isEmpty()) {
            pendingStart?.invoke()
            pendingStart = null
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}

@Composable
private fun AiepNavRoot(
    onStart: (relayUrl: String, sessionId: String, mode: CaptureMode, localSessionId: String, localOnly: Boolean) -> Unit,
    onStop: () -> Unit,
    onScanQr: ((QrPayload) -> Unit) -> Unit,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val repo = remember { SessionsRepository.get(context) }
    val coroutineScope = rememberCoroutineScope()
    val prefsState by AppPreferences.stateFlow(context)
        .collectAsStateWithLifecycle(initialValue = AppPreferences.State())

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == AiepTab.Sessions.route || currentRoute == AiepTab.Settings.route

    Scaffold(
        containerColor = AiepCream,
        bottomBar = {
            if (showBottomBar) {
                AiepBottomNav(
                    currentRoute = currentRoute,
                    onSelect = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(AiepTab.Sessions.route) {
                                inclusive = false
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { inner: PaddingValues ->
        NavHost(
            navController = navController,
            startDestination = AiepTab.Sessions.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            composable(AiepTab.Sessions.route) {
                SessionsListScreen(
                    onOpenSession = { id -> navController.navigate("session/$id") },
                    onCreateSession = {
                        coroutineScope.launch {
                            val meta = repo.createSession(mode = prefsState.mode, relaySessionId = null)
                            navController.navigate("session/${meta.id}")
                        }
                    },
                )
            }
            composable(AiepTab.Settings.route) {
                SettingsScreen()
            }
            composable(
                route = "session/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                if (id.isBlank()) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                    return@composable
                }
                SessionDetailScreen(
                    sessionId = id,
                    onBack = { navController.popBackStack() },
                    onStart = onStart,
                    onStop = onStop,
                    onScanQr = onScanQr,
                )
            }
        }
    }
}
