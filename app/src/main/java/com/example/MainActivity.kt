package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.settings.LanguagePreferenceStore
import com.example.ui.components.FloatingMackButton
import kotlinx.coroutines.launch
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.configureAppCheck
import com.example.data.firebase.signOutMotora
import com.example.data.obd.ObdConnectionManager
import com.example.data.obd.ObdConnectionMethod
import com.example.data.repository.FirestoreVehicleRepository
import com.example.data.repository.InMemoryPreviewVehicleDataSource
import com.example.data.repository.VehicleDataSource
import com.example.ui.auth.AuthScreen
import com.example.ui.chat.VehicleAiChatScreen
import com.example.ui.chat.VehicleAiChatViewModel
import com.example.ui.diagnostics.DiagnosticsScreen
import com.example.ui.diagnostics.DiagnosticsSessionCoordinator
import com.example.ui.diagnostics.DiagnosticsViewModel
import com.example.ui.diagnostics.toObdVehicleSnapshot
import com.example.ui.diagnostics.unregisteredObdVehicleSnapshot
import com.example.ui.diagnostics.connect.DiagnosticsConnectScreen
import com.example.ui.diagnostics.connect.DiagnosticsConnectViewModel
import com.example.ui.diagnostics.explain.ObdExplainScreen
import com.example.ui.diagnostics.history.DiagnosticsHistoryScreen
import com.example.ui.diagnostics.history.DiagnosticsHistoryViewModel
import com.example.ui.diagnostics.report.DiagnosticsReportScreen
import com.example.ui.diagnostics.results.DiagnosticsResultsScreen
import com.example.ui.diagnostics.scan.DiagnosticsScanScreen
import com.example.ui.diagnostics.scan.DiagnosticsScanViewModel
import com.example.ui.garage.GarageScreen
import com.example.ui.garage.GarageViewModel
import com.example.ui.log.VehicleIssueLogScreen
import com.example.ui.log.VehicleIssueLogViewModel
import com.example.ui.overview.VehicleOverviewScreen
import com.example.ui.overview.VehicleOverviewViewModel
import com.example.ui.registration.PhotoCaptureGuideScreen
import com.example.ui.registration.VehicleFormScreen
import com.example.ui.registration.VehicleFormViewModel
import com.example.ui.theme.MotoraTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_GARAGE = "garage"
const val ROUTE_PHOTO_GUIDE = "photo_guide"
const val ROUTE_VEHICLE_FORM = "vehicle_form"
const val ROUTE_VEHICLE_OVERVIEW = "vehicle_overview"
const val ROUTE_VEHICLE_AI_CHAT = "vehicle_ai_chat"
const val ROUTE_VEHICLE_ISSUE_LOG = "vehicle_issue_log"
const val ROUTE_DIAGNOSTICS = "diagnostics"
const val ROUTE_DIAGNOSTICS_EXPLAIN = "diagnostics/explain"
const val ROUTE_DIAGNOSTICS_CONNECT = "diagnostics/connect"
const val ROUTE_DIAGNOSTICS_SCAN = "diagnostics/scan"
const val ROUTE_DIAGNOSTICS_RESULTS = "diagnostics/results"
const val ROUTE_DIAGNOSTICS_HISTORY = "diagnostics/history"
const val ROUTE_DIAGNOSTICS_REPORT = "diagnostics/report"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        lifecycleScope.launch {
            LanguagePreferenceStore.loadSavedLanguage(this@MainActivity)
        }
        enableEdgeToEdge()
        setContent {
            MotoraTheme {
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    var isPreviewSession by remember { mutableStateOf(false) }

    if (user == null && !isPreviewSession) {
        AuthScreen(
            onEnterPreviewMode = { isPreviewSession = true }
        )
    } else if (user == null && isPreviewSession) {
        val previewDataSource = remember { InMemoryPreviewVehicleDataSource() }
        key("preview_local_session") {
            MotoraAppShell(
                currentUserId = "preview_local_session",
                ownerDisplayName = "Preview Session",
                isPreviewMode = true,
                dataSourceProvider = { previewDataSource },
                onSignOut = { isPreviewSession = false }
            )
        }
    } else if (user != null) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val credentialManager = remember(context) { CredentialManager.create(context) }

        key(user.uid) {
            MotoraAppShell(
                currentUserId = user.uid,
                ownerDisplayName = user.displayName ?: user.email ?: "Owner",
                isPreviewMode = false,
                dataSourceProvider = { app ->
                    val dbId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(dbId)
                    FirestoreVehicleRepository(db)
                },
                onSignOut = {
                    isPreviewSession = false
                    signOutMotora(
                        credentialManager = credentialManager,
                        scope = scope
                    )
                }
            )
        }
    }
}

@Composable
fun MotoraAppShell(
    currentUserId: String,
    ownerDisplayName: String,
    isPreviewMode: Boolean = false,
    dataSourceProvider: (Application) -> VehicleDataSource,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val sharedDataSource = remember(currentUserId) { dataSourceProvider(application) }
    val obdConnectionManager = remember(currentUserId) { ObdConnectionManager() }
    val diagnosticsCoordinator = remember(currentUserId) {
        DiagnosticsSessionCoordinator(
            userId = currentUserId,
            repository = sharedDataSource,
            connectionManager = obdConnectionManager
        )
    }
    var activeDiagnosticVehicle by remember(currentUserId) {
        mutableStateOf<com.example.data.model.Vehicle?>(null)
    }
    val registeredVehicles by sharedDataSource
        .observeVehicles(currentUserId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var lastFocusedVehicleId by remember(currentUserId) { mutableStateOf<String?>(null) }

    DisposableEffect(obdConnectionManager, context) {
        obdConnectionManager.attachBluetoothObserver(context, autoDiscoverIfEnabled = false)
        onDispose {
            obdConnectionManager.detachBluetoothObserver()
        }
    }

    LaunchedEffect(activeDiagnosticVehicle, registeredVehicles) {
        val targetVehicle = activeDiagnosticVehicle
            ?: registeredVehicles.firstOrNull { it.primary }
            ?: registeredVehicles.firstOrNull()
        obdConnectionManager.updateTargetVehicleSnapshot(
            targetVehicle?.toObdVehicleSnapshot() ?: unregisteredObdVehicleSnapshot()
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val routeVehicleId = navBackStackEntry?.arguments?.getString("vehicleId")?.takeIf { it.isNotBlank() }

    LaunchedEffect(routeVehicleId, activeDiagnosticVehicle?.id) {
        val candidate = routeVehicleId ?: activeDiagnosticVehicle?.id
        if (!candidate.isNullOrBlank()) {
            lastFocusedVehicleId = candidate
        }
    }

    val floatingMackVehicleId = remember(
        routeVehicleId,
        currentRoute,
        activeDiagnosticVehicle?.id,
        lastFocusedVehicleId,
        registeredVehicles
    ) {
        val availableIds = registeredVehicles.map { it.id }.toSet()
        val fromRoute = routeVehicleId?.takeIf { it in availableIds || availableIds.isEmpty() }
        val fromDiagnostic = if (currentRoute?.startsWith(ROUTE_DIAGNOSTICS) == true) {
            activeDiagnosticVehicle?.id?.takeIf { it in availableIds }
        } else null
        val fromHistory = lastFocusedVehicleId?.takeIf { it in availableIds }
        val primaryOrFirst = registeredVehicles.firstOrNull { it.primary }?.id
            ?: registeredVehicles.firstOrNull()?.id
        fromRoute ?: fromDiagnostic ?: fromHistory ?: primaryOrFirst
    }

    val floatingMackTargetVehicleId = if (
        currentRoute != null &&
        !currentRoute.startsWith(ROUTE_VEHICLE_AI_CHAT) &&
        !currentRoute.startsWith(ROUTE_VEHICLE_FORM) &&
        currentRoute != ROUTE_PHOTO_GUIDE
    ) {
        floatingMackVehicleId
    } else {
        null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = ROUTE_GARAGE
        ) {
        composable(route = ROUTE_GARAGE) {
            val garageViewModel: GarageViewModel = viewModel(
                key = "garage_$currentUserId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        GarageViewModel(
                            repository = dataSourceProvider(app),
                            currentUserId = currentUserId,
                            displayName = ownerDisplayName,
                            connectionManager = obdConnectionManager
                        )
                    }
                }
            )

            GarageScreen(
                viewModel = garageViewModel,
                ownerDisplayName = ownerDisplayName,
                isPreviewMode = isPreviewMode,
                onAddVehicle = {
                    navController.navigate(ROUTE_PHOTO_GUIDE) {
                        launchSingleTop = true
                    }
                },
                onOpenVehicleOverview = { vehicleId ->
                    navController.navigate("$ROUTE_VEHICLE_OVERVIEW/$vehicleId")
                },
                onEditVehicle = { vehicleId ->
                    navController.navigate("$ROUTE_VEHICLE_FORM?vehicleId=$vehicleId")
                },
                onOpenAiChat = { vehicleId ->
                    navController.navigate("$ROUTE_VEHICLE_AI_CHAT/$vehicleId")
                },
                onOpenIssueLog = { vehicleId ->
                    navController.navigate("$ROUTE_VEHICLE_ISSUE_LOG/$vehicleId")
                },
                onOpenDiagnostics = { vehicleId ->
                    val route = if (!vehicleId.isNullOrBlank()) {
                        "$ROUTE_DIAGNOSTICS?vehicleId=${android.net.Uri.encode(vehicleId)}"
                    } else {
                        ROUTE_DIAGNOSTICS
                    }
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                },
                onSignOut = onSignOut
            )
        }

        composable(route = ROUTE_PHOTO_GUIDE) {
            PhotoCaptureGuideScreen(
                onNavigateBack = { navController.popBackStack() },
                onContinueToPhotoCapture = {
                    navController.navigate(ROUTE_VEHICLE_FORM) {
                        popUpTo(ROUTE_PHOTO_GUIDE) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "$ROUTE_VEHICLE_FORM?vehicleId={vehicleId}",
            arguments = listOf(
                navArgument("vehicleId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val editingVehicleId = backStackEntry.arguments?.getString("vehicleId")
            val formViewModel: VehicleFormViewModel = viewModel(
                key = "form_${currentUserId}_${editingVehicleId ?: "new"}",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        VehicleFormViewModel(
                            repository = dataSourceProvider(app),
                            currentUserId = currentUserId,
                            editingVehicleId = editingVehicleId
                        )
                    }
                }
            )

            VehicleFormScreen(
                viewModel = formViewModel,
                onNavigateBack = { navController.popBackStack() },
                onVehicleSaved = { savedId ->
                    navController.popBackStack()
                    if (editingVehicleId == null) {
                        navController.navigate("$ROUTE_VEHICLE_OVERVIEW/$savedId")
                    }
                }
            )
        }

        composable(
            route = "$ROUTE_VEHICLE_OVERVIEW/{vehicleId}",
            arguments = listOf(
                navArgument("vehicleId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getString("vehicleId").orEmpty()
            val overviewViewModel: VehicleOverviewViewModel = viewModel(
                key = "overview_${currentUserId}_$vehicleId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        VehicleOverviewViewModel(
                            repository = dataSourceProvider(app),
                            currentUserId = currentUserId,
                            vehicleId = vehicleId,
                            connectionManager = obdConnectionManager,
                            diagnosticsCoordinator = diagnosticsCoordinator
                        )
                    }
                }
            )

            VehicleOverviewScreen(
                viewModel = overviewViewModel,
                onNavigateBack = { navController.popBackStack() },
                onEditVehicle = { id ->
                    navController.navigate("$ROUTE_VEHICLE_FORM?vehicleId=$id")
                },
                onOpenAiChat = { id, initialTopic ->
                    val suffix = if (!initialTopic.isNullOrBlank()) {
                        "?topic=${android.net.Uri.encode(initialTopic)}"
                    } else {
                        ""
                    }
                    navController.navigate("$ROUTE_VEHICLE_AI_CHAT/$id$suffix")
                },
                onOpenIssueLog = { id ->
                    navController.navigate("$ROUTE_VEHICLE_ISSUE_LOG/$id")
                },
                onOpenDiagnostics = { id ->
                    navController.navigate("$ROUTE_DIAGNOSTICS?vehicleId=${android.net.Uri.encode(id)}") {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = "$ROUTE_VEHICLE_AI_CHAT/{vehicleId}?topic={topic}",
            arguments = listOf(
                navArgument("vehicleId") {
                    type = NavType.StringType
                },
                navArgument("topic") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getString("vehicleId").orEmpty()
            val topic = backStackEntry.arguments?.getString("topic")
            val chatViewModel: VehicleAiChatViewModel = viewModel(
                key = "chat_${currentUserId}_${vehicleId}_${topic.orEmpty()}",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        VehicleAiChatViewModel(
                            repository = dataSourceProvider(app),
                            currentUserId = currentUserId,
                            vehicleId = vehicleId,
                            initialTopic = topic,
                            connectionManager = obdConnectionManager
                        )
                    }
                }
            )

            VehicleAiChatScreen(
                viewModel = chatViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenIssueLog = { id ->
                    navController.navigate("$ROUTE_VEHICLE_ISSUE_LOG/$id") {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = "$ROUTE_VEHICLE_ISSUE_LOG/{vehicleId}",
            arguments = listOf(
                navArgument("vehicleId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getString("vehicleId").orEmpty()
            val logViewModel: VehicleIssueLogViewModel = viewModel(
                key = "log_${currentUserId}_$vehicleId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        VehicleIssueLogViewModel(
                            repository = dataSourceProvider(app),
                            currentUserId = currentUserId,
                            vehicleId = vehicleId
                        )
                    }
                }
            )

            VehicleIssueLogScreen(
                viewModel = logViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenAiChat = { initialTopic ->
                    val suffix = if (!initialTopic.isNullOrBlank()) {
                        "?topic=${android.net.Uri.encode(initialTopic)}"
                    } else {
                        ""
                    }
                    navController.navigate("$ROUTE_VEHICLE_AI_CHAT/$vehicleId$suffix") {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = "$ROUTE_DIAGNOSTICS?vehicleId={vehicleId}",
            arguments = listOf(
                navArgument("vehicleId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val initialVehicleId = backStackEntry.arguments?.getString("vehicleId")
            val diagnosticsViewModel: DiagnosticsViewModel = viewModel(
                key = "diagnostics_${currentUserId}_${initialVehicleId.orEmpty()}",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        DiagnosticsViewModel(
                            repository = dataSourceProvider(app),
                            currentUserId = currentUserId,
                            initialVehicleId = initialVehicleId,
                            connectionManager = obdConnectionManager
                        )
                    }
                }
            )
            val diagState by diagnosticsViewModel.uiState.collectAsStateWithLifecycle()
            activeDiagnosticVehicle = diagState.selectedVehicle
            val selectedLabel = diagState.selectedVehicle?.let {
                "${it.modelYear} ${it.manufacturer} ${it.model}"
            }.orEmpty()
            val activeVehicleId = diagState.selectedVehicle?.id.orEmpty()
            val queryParams = buildString {
                val parts = mutableListOf<String>()
                if (selectedLabel.isNotBlank()) parts += "vehicleLabel=${android.net.Uri.encode(selectedLabel)}"
                if (activeVehicleId.isNotBlank()) parts += "vehicleId=${android.net.Uri.encode(activeVehicleId)}"
                if (parts.isNotEmpty()) append("?${parts.joinToString("&")}")
            }

            DiagnosticsScreen(
                viewModel = diagnosticsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onAddVehicle = {
                    navController.navigate(ROUTE_PHOTO_GUIDE) {
                        launchSingleTop = true
                    }
                },
                onOpenConnect = { method ->
                    val connectQs = buildDiagnosticsQuery(selectedLabel, activeVehicleId, method)
                    navController.navigate("$ROUTE_DIAGNOSTICS_CONNECT$connectQs") {
                        launchSingleTop = true
                    }
                },
                onOpenScan = {
                    navController.navigate("$ROUTE_DIAGNOSTICS_SCAN$queryParams") {
                        launchSingleTop = true
                    }
                },
                onOpenResults = {
                    navController.navigate("$ROUTE_DIAGNOSTICS_RESULTS$queryParams") {
                        launchSingleTop = true
                    }
                },
                onOpenHistory = {
                    navController.navigate("$ROUTE_DIAGNOSTICS_HISTORY$queryParams") {
                        launchSingleTop = true
                    }
                },
                onOpenReport = {
                    navController.navigate("$ROUTE_DIAGNOSTICS_REPORT$queryParams") {
                        launchSingleTop = true
                    }
                },
                onOpenExplain = {
                    navController.navigate("$ROUTE_DIAGNOSTICS_EXPLAIN$queryParams") {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = "$ROUTE_DIAGNOSTICS_EXPLAIN?vehicleLabel={vehicleLabel}&vehicleId={vehicleId}",
            arguments = listOf(
                navArgument("vehicleLabel") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("vehicleId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val vehicleLabel = backStackEntry.arguments?.getString("vehicleLabel")
            val vehicleId = backStackEntry.arguments?.getString("vehicleId") ?: activeDiagnosticVehicle?.id
            ObdExplainScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenConnect = { method ->
                    val qs = buildDiagnosticsQuery(vehicleLabel, vehicleId, method)
                    navController.navigate("$ROUTE_DIAGNOSTICS_CONNECT$qs") {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = "$ROUTE_DIAGNOSTICS_CONNECT?vehicleLabel={vehicleLabel}&vehicleId={vehicleId}&method={method}",
            arguments = listOf(
                navArgument("vehicleLabel") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("vehicleId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("method") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val connectViewModel: DiagnosticsConnectViewModel = viewModel(
                key = "diagnostics_connect_$currentUserId",
                factory = viewModelFactory {
                    initializer {
                        DiagnosticsConnectViewModel(connectionManager = obdConnectionManager)
                    }
                }
            )
            val methodArg = backStackEntry.arguments?.getString("method")
            val initialMethod = methodArg?.let { raw ->
                ObdConnectionMethod.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            }
            DiagnosticsConnectScreen(
                viewModel = connectViewModel,
                selectedVehicleLabel = backStackEntry.arguments?.getString("vehicleLabel"),
                onNavigateBack = { navController.popBackStack() },
                initialMethod = initialMethod
            )
        }

        composable(
            route = "$ROUTE_DIAGNOSTICS_SCAN?vehicleLabel={vehicleLabel}&vehicleId={vehicleId}",
            arguments = listOf(
                navArgument("vehicleLabel") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("vehicleId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val vehicleLabel = backStackEntry.arguments?.getString("vehicleLabel")
            val vehicleId = backStackEntry.arguments?.getString("vehicleId") ?: activeDiagnosticVehicle?.id
            val scanViewModel: DiagnosticsScanViewModel = viewModel(
                key = "diagnostics_scan_${currentUserId}_${vehicleId.orEmpty()}",
                factory = viewModelFactory {
                    initializer {
                        DiagnosticsScanViewModel(
                            connectionManager = obdConnectionManager,
                            coordinator = diagnosticsCoordinator,
                            selectedVehicleProvider = { activeDiagnosticVehicle }
                        )
                    }
                }
            )
            val qs = buildDiagnosticsQuery(vehicleLabel, vehicleId)
            DiagnosticsScanScreen(
                selectedVehicleLabel = vehicleLabel,
                onNavigateBack = { navController.popBackStack() },
                viewModel = scanViewModel,
                onOpenConnect = {
                    navController.navigate("$ROUTE_DIAGNOSTICS_CONNECT$qs") {
                        launchSingleTop = true
                    }
                },
                onNavigateToResults = {
                    navController.navigate("$ROUTE_DIAGNOSTICS_RESULTS$qs") {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = "$ROUTE_DIAGNOSTICS_RESULTS?vehicleLabel={vehicleLabel}&vehicleId={vehicleId}",
            arguments = listOf(
                navArgument("vehicleLabel") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("vehicleId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val vehicleLabel = backStackEntry.arguments?.getString("vehicleLabel")
            val vehicleId = backStackEntry.arguments?.getString("vehicleId") ?: activeDiagnosticVehicle?.id
            val qs = buildDiagnosticsQuery(vehicleLabel, vehicleId)
            DiagnosticsResultsScreen(
                selectedVehicleLabel = vehicleLabel,
                onNavigateBack = { navController.popBackStack() },
                coordinator = diagnosticsCoordinator,
                connectionManager = obdConnectionManager,
                onOpenScan = {
                    navController.navigate("$ROUTE_DIAGNOSTICS_SCAN$qs") {
                        launchSingleTop = true
                    }
                },
                onOpenReport = {
                    navController.navigate("$ROUTE_DIAGNOSTICS_REPORT$qs") {
                        launchSingleTop = true
                    }
                },
                onOpenHistory = {
                    navController.navigate("$ROUTE_DIAGNOSTICS_HISTORY$qs") {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = "$ROUTE_DIAGNOSTICS_HISTORY?vehicleLabel={vehicleLabel}&vehicleId={vehicleId}",
            arguments = listOf(
                navArgument("vehicleLabel") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("vehicleId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val vehicleLabel = backStackEntry.arguments?.getString("vehicleLabel")
            val vehicleId = backStackEntry.arguments?.getString("vehicleId") ?: activeDiagnosticVehicle?.id
            val historyViewModel: DiagnosticsHistoryViewModel = viewModel(
                key = "diagnostics_history_${currentUserId}_${vehicleId.orEmpty()}",
                factory = viewModelFactory {
                    initializer {
                        DiagnosticsHistoryViewModel(
                            repository = sharedDataSource,
                            userId = currentUserId,
                            vehicleId = vehicleId
                        )
                    }
                }
            )
            val qs = buildDiagnosticsQuery(vehicleLabel, vehicleId)
            DiagnosticsHistoryScreen(
                selectedVehicleLabel = vehicleLabel,
                onNavigateBack = { navController.popBackStack() },
                viewModel = historyViewModel,
                onOpenScan = {
                    navController.navigate("$ROUTE_DIAGNOSTICS_SCAN$qs") {
                        launchSingleTop = true
                    }
                },
                onOpenSavedSessionInResults = { record ->
                    diagnosticsCoordinator.openSavedSession(record)
                    navController.navigate("$ROUTE_DIAGNOSTICS_RESULTS$qs") {
                        launchSingleTop = true
                    }
                },
                onOpenSavedSessionInReport = { record ->
                    diagnosticsCoordinator.openSavedSession(record)
                    navController.navigate("$ROUTE_DIAGNOSTICS_REPORT$qs") {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = "$ROUTE_DIAGNOSTICS_REPORT?vehicleLabel={vehicleLabel}&vehicleId={vehicleId}",
            arguments = listOf(
                navArgument("vehicleLabel") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("vehicleId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val vehicleLabel = backStackEntry.arguments?.getString("vehicleLabel")
            val vehicleId = backStackEntry.arguments?.getString("vehicleId") ?: activeDiagnosticVehicle?.id
            val qs = buildDiagnosticsQuery(vehicleLabel, vehicleId)
            DiagnosticsReportScreen(
                selectedVehicleLabel = vehicleLabel,
                onNavigateBack = { navController.popBackStack() },
                coordinator = diagnosticsCoordinator,
                onOpenScan = {
                    navController.navigate("$ROUTE_DIAGNOSTICS_SCAN$qs") {
                        launchSingleTop = true
                    }
                }
            )
        }
    }

        if (floatingMackTargetVehicleId != null) {
            FloatingMackButton(
                onClick = {
                    navController.navigate("$ROUTE_VEHICLE_AI_CHAT/$floatingMackTargetVehicleId") {
                        launchSingleTop = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(
                        end = 16.dp,
                        bottom = 18.dp
                    )
            )
        }
    }
}

private fun buildDiagnosticsQuery(
    vehicleLabel: String?,
    vehicleId: String?,
    method: ObdConnectionMethod? = null
): String {
    val parts = mutableListOf<String>()
    if (!vehicleLabel.isNullOrBlank()) parts += "vehicleLabel=${android.net.Uri.encode(vehicleLabel)}"
    if (!vehicleId.isNullOrBlank()) parts += "vehicleId=${android.net.Uri.encode(vehicleId)}"
    if (method != null) parts += "method=${method.name}"
    return if (parts.isEmpty()) "" else "?${parts.joinToString("&")}"
}
