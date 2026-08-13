package com.example.sls_assistant_pro

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.sls_assistant_pro.data.local.AppDatabase
import com.example.sls_assistant_pro.data.local.TokenStore
import com.example.sls_assistant_pro.data.model.TaskItem
import com.example.sls_assistant_pro.data.remote.SlsApiService
import com.example.sls_assistant_pro.data.repository.SlsRepository
import com.example.sls_assistant_pro.ui.components.CollectionWalletDialog
import com.example.sls_assistant_pro.ui.components.ShipmentStatusDialog
import com.example.sls_assistant_pro.ui.components.TaskDetailsDialog
import com.example.sls_assistant_pro.ui.screens.*
import com.example.sls_assistant_pro.ui.theme.SlsAssistantTheme
import com.example.sls_assistant_pro.ui.viewmodel.AuthState
import com.example.sls_assistant_pro.ui.viewmodel.AuthViewModel
import com.example.sls_assistant_pro.ui.viewmodel.ScannerViewModel
import com.example.sls_assistant_pro.ui.viewmodel.TasksViewModel

class MainActivity : ComponentActivity() {

    private lateinit var repository: SlsRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request runtime permissions for Camera and Location
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        // Initialize dependencies
        val tokenStore = TokenStore(applicationContext)
        val database = AppDatabase.getInstance(applicationContext)
        val apiService = SlsApiService.create()
        repository = SlsRepository(apiService, tokenStore, database)

        setContent {
            SlsAssistantTheme {
                MainAppEntry(repository = repository)
            }
        }
    }
}

class RepositoryViewModelFactory(private val repository: SlsRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(repository) as T
            modelClass.isAssignableFrom(TasksViewModel::class.java) -> TasksViewModel(repository) as T
            modelClass.isAssignableFrom(ScannerViewModel::class.java) -> ScannerViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppEntry(repository: SlsRepository) {
    val factory = remember { RepositoryViewModelFactory(repository) }
    val authViewModel: AuthViewModel = viewModel(factory = factory)
    val tasksViewModel: TasksViewModel = viewModel(factory = factory)
    val scannerViewModel: ScannerViewModel = viewModel(factory = factory)

    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsState()
    val tasksState by tasksViewModel.uiState.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) }
    var selectedTaskForDetails by remember { mutableStateOf<TaskItem?>(null) }
    var selectedTaskForStatusUpdate by remember { mutableStateOf<TaskItem?>(null) }
    var taskForCallConfirmation by remember { mutableStateOf<TaskItem?>(null) }
    var showCollectionWalletDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        tasksViewModel.loadTasks()
    }

    NavHost(
        navController = navController,
        startDestination = "session_gate"
    ) {
        composable("session_gate") {
            SessionGateScreen(
                authViewModel = authViewModel,
                onNavigateToHome = {
                    navController.navigate("main") {
                        popUpTo("session_gate") { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate("login") {
                        popUpTo("session_gate") { inclusive = true }
                    }
                }
            )
        }

        composable("login") {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = {
                    tasksViewModel.loadTasks()
                    navController.navigate("main") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("main") {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "SLS Assistant Pro",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "مساعد الكابتن - SLS Express",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        actions = {
                            // Collection Wallet Quick Access
                            IconButton(onClick = { showCollectionWalletDialog = true }) {
                                Icon(
                                    Icons.Default.AccountBalanceWallet,
                                    contentDescription = "محفظة التحصيل",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Refresh Tasks
                            IconButton(onClick = { tasksViewModel.loadTasks() }) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "تحديث البيانات"
                                )
                            }

                            // Logout
                            IconButton(onClick = {
                                authViewModel.logout()
                                navController.navigate("login") {
                                    popUpTo("main") { inclusive = true }
                                }
                            }) {
                                Icon(
                                    Icons.Default.Logout,
                                    contentDescription = "تسجيل الخروج",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                            label = { Text("الرئيسية") }
                        )
                        NavigationBarItem(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            icon = { Icon(Icons.Default.ListAlt, contentDescription = null) },
                            label = { Text("المهام (${tasksState.tasks.size})") }
                        )
                        NavigationBarItem(
                            selected = activeTab == 2,
                            onClick = { activeTab = 2 },
                            icon = { Icon(Icons.Default.Map, contentDescription = null) },
                            label = { Text("الخريطة") }
                        )
                        NavigationBarItem(
                            selected = activeTab == 3,
                            onClick = { activeTab = 3 },
                            icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                            label = { Text("الماسح") }
                        )
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    when (activeTab) {
                        0 -> DashboardTab(
                            tasksState = tasksState,
                            onNavigateToScan = { activeTab = 3 },
                            onNavigateToMap = { activeTab = 2 },
                            onNavigateToTasks = { activeTab = 1 }
                        )
                        1 -> TasksTab(
                            tasksViewModel = tasksViewModel,
                            tasksState = tasksState,
                            onOpenTaskDetails = { task -> selectedTaskForDetails = task },
                            onOpenStatusUpdate = { task -> selectedTaskForStatusUpdate = task },
                            onCallRequested = { task -> taskForCallConfirmation = task }
                        )
                        2 -> MapTab(
                            tasksState = tasksState,
                            onDriverLocationUpdated = { lat, lng -> tasksViewModel.updateDriverLocation(lat, lng) },
                            onSelectTaskDetails = { task -> selectedTaskForDetails = task },
                            onOpenStatusUpdate = { task -> selectedTaskForStatusUpdate = task },
                            onCallRequested = { task -> taskForCallConfirmation = task },
                            onNavigateToScan = { activeTab = 3 }
                        )
                        3 -> ScannerTab(
                            scannerViewModel = scannerViewModel,
                            onOpenTaskDetails = { task -> selectedTaskForDetails = task },
                            onShowOnMap = { groupTasks ->
                                tasksViewModel.addExtraTasks(groupTasks)
                                activeTab = 0 // Navigate to Map tab
                            }
                        )
                    }
                }
            }
        }
    }

    // Global Task Details Dialog
    selectedTaskForDetails?.let { task ->
        TaskDetailsDialog(
            task = task,
            onDismiss = { selectedTaskForDetails = null },
            onOpenStatusUpdate = {
                val target = selectedTaskForDetails
                selectedTaskForDetails = null
                selectedTaskForStatusUpdate = target
            },
            onCallRequested = { calledTask ->
                taskForCallConfirmation = calledTask
            }
        )
    }

    // Global Post Call Confirmation Dialog
    taskForCallConfirmation?.let { task ->
        com.example.sls_assistant_pro.ui.components.PostCallConfirmationDialog(
            task = task,
            onDismiss = { taskForCallConfirmation = null },
            onResultSelected = { resultStatus ->
                tasksViewModel.setLocalContactStatus(task.displayReference, resultStatus)
            }
        )
    }

    // Global Shipment Status Update Dialog
    selectedTaskForStatusUpdate?.let { task ->
        ShipmentStatusDialog(
            task = task,
            repository = repository,
            driverLat = tasksState.driverLat,
            driverLng = tasksState.driverLng,
            onDismiss = { selectedTaskForStatusUpdate = null },
            onStatusUpdated = {
                tasksViewModel.loadTasks()
            }
        )
    }

    // Global Collection Wallet Dialog
    if (showCollectionWalletDialog) {
        CollectionWalletDialog(
            repository = repository,
            currentTasks = tasksState.tasks,
            onDismiss = { showCollectionWalletDialog = false }
        )
    }
}
