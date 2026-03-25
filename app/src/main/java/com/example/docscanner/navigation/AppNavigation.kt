package com.example.docscanner.navigation

import android.annotation.SuppressLint
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.docscanner.domain.model.ApplicationSession
import com.example.docscanner.domain.model.ApplicationType
import com.example.docscanner.domain.model.Document
import com.example.docscanner.domain.model.FolderExportType
import com.example.docscanner.presentation.alldocuments.AllDocumentsScreen
import com.example.docscanner.presentation.alldocuments.AllDocumentsViewModel
import com.example.docscanner.presentation.alldocuments.SidebarDragState
import com.example.docscanner.presentation.apphome.AppHomeScreen
import com.example.docscanner.presentation.apphome.SessionContextViewModel
import com.example.docscanner.presentation.camera.CameraPermissionHandler
import com.example.docscanner.presentation.camera.CameraScreen
import com.example.docscanner.presentation.folder.FolderDetailScreen
import com.example.docscanner.presentation.home.HomeViewModel
import com.example.docscanner.presentation.login.LoginScreen
import com.example.docscanner.presentation.merge.MergeViewModel
import com.example.docscanner.presentation.merge.ReorderScreen
import com.example.docscanner.presentation.profile.ProfileScreen
import com.example.docscanner.presentation.shared.ScannerViewModel
import com.example.docscanner.presentation.viewer.DocumentType
import com.example.docscanner.presentation.viewer.DocumentViewerScreen

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object AppHome : Screen("app_home")
    data object AllDocuments : Screen("all_documents")
    data object Profile : Screen("profile")
    data object FolderDetail : Screen("folder/{folderId}/{folderName}/{folderIcon}/{exportType}") {
        fun createRoute(
            folderId: String,
            folderName: String,
            folderIcon: String,
            exportType: FolderExportType
        ) = "folder/$folderId/${folderName.enc()}/${folderIcon.enc()}/${exportType.name}"
    }
    data object Camera : Screen("camera")
    data object Reorder : Screen("reorder")
    data object Viewer : Screen("viewer/{docName}/{docType}/{docUri}") {
        fun createRoute(name: String, type: DocumentType, uri: String) =
            "viewer/${name.enc()}/${type.name}/${uri.enc()}"
    }
}

private fun String.enc() = java.net.URLEncoder.encode(this, "UTF-8")

// AppHome removed — it's a standalone screen, not inside MainLayout
private val mainLayoutRoutes = setOf(
    Screen.AllDocuments.route,
    Screen.FolderDetail.route,
)

@SuppressLint("ComposableDestinationInComposeScope")
@Composable
fun DocScannerNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Login.route
) {
    val scannerViewModel: ScannerViewModel = hiltViewModel()
    val homeViewModel: HomeViewModel = hiltViewModel()
    val allDocsViewModel: AllDocumentsViewModel = hiltViewModel()
    val mergeViewModel: MergeViewModel = hiltViewModel()
    val sessionContext: SessionContextViewModel = hiltViewModel()  // ← new

    val state by scannerViewModel.state.collectAsState()
    val mergeState by mergeViewModel.state.collectAsState()

    // ── Folders: session folders when in session, global otherwise ────────────
    val globalFolders by homeViewModel.folders.collectAsState()
    val sessionFolders by sessionContext.sessionFolders.collectAsState()
    val folders = if (sessionFolders.isNotEmpty()) sessionFolders else globalFolders

    val dragState = remember { SidebarDragState() }
    var selectedTab by rememberSaveable { mutableStateOf(BottomTab.ALL_DOCS) }
    var viewingDocument by remember { mutableStateOf<Document?>(null) }

    var isSelectMode by remember { mutableStateOf(false) }
    var isOrganizeMode by remember { mutableStateOf(false) }
    var selectedCount by remember { mutableIntStateOf(0) }
    var docCount by remember { mutableIntStateOf(0) }
    var selectAllTrigger by remember { mutableIntStateOf(0) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(currentRoute) {
        if (currentRoute != Screen.AllDocuments.route &&
            currentRoute?.startsWith("folder/") != true
        ) {
            isSelectMode = false
        }
    }

    fun toggleSelect() {
        isSelectMode = !isSelectMode
        if (isSelectMode) isOrganizeMode = false
    }

    fun toggleOrganize() {
        isOrganizeMode = !isOrganizeMode
        if (isOrganizeMode) isSelectMode = false
        if (!isOrganizeMode) allDocsViewModel.clearOrganize()
    }

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            val tid = state.targetFolderId
            scannerViewModel.onSaveNavigated()
            scannerViewModel.onReset()
            if (tid.isNotEmpty()) {
                navController.popBackStack(Screen.FolderDetail.route, inclusive = false)
            } else {
                navController.popBackStack(Screen.AllDocuments.route, inclusive = false)
                selectedTab = BottomTab.ALL_DOCS
            }
        }
    }

    LaunchedEffect(mergeState.mergeSuccess) {
        if (mergeState.mergeSuccess) {
            mergeViewModel.onMergeNavigated()
            navController.popBackStack()
        }
    }

    val selectedSidebarId = remember(currentRoute, navBackStackEntry) {
        when {
            currentRoute?.startsWith("folder/") == true ->
                navBackStackEntry?.arguments?.getString("folderId") ?: ALL_DOCUMENTS_ID
            else -> ALL_DOCUMENTS_ID
        }
    }

    fun navigateToViewer(doc: Document) {
        val type = if (doc.pdfPath != null) DocumentType.PDF else DocumentType.IMAGE
        val uri = doc.pdfPath ?: doc.thumbnailPath ?: return
        viewingDocument = doc
        navController.navigate(Screen.Viewer.createRoute(doc.name, type, uri))
    }

    if (currentRoute in mainLayoutRoutes) {
        MainLayout(
            folders = folders,                              // ← now session-aware
            selectedSidebarId = selectedSidebarId,
            dragState = dragState,
            onAllDocumentsSelected = {
                selectedTab = BottomTab.ALL_DOCS
                isSelectMode = false
                isOrganizeMode = false
                navController.navigate(Screen.AllDocuments.route) {
                    popUpTo(Screen.AllDocuments.route) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onFolderSelected = { folder ->
                isSelectMode = false
                isOrganizeMode = false
                navController.navigate(
                    Screen.FolderDetail.createRoute(
                        folder.id, folder.name, folder.icon, folder.exportType
                    )
                ) { launchSingleTop = true }
            },
            onDropToFolder = { documentId, folderId ->
                allDocsViewModel.moveDocumentToFolder(documentId, folderId)
            },
            onFolderReorder = { from, to -> homeViewModel.reorderFolder(from, to) },
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                selectedTab = tab
                isSelectMode = false
                isOrganizeMode = false
                when (tab) {
                    BottomTab.ALL_DOCS -> navController.navigate(Screen.AllDocuments.route) {
                        popUpTo(Screen.AllDocuments.route) { inclusive = true }
                        launchSingleTop = true
                    }
                    BottomTab.PROFILE -> navController.navigate(Screen.Profile.route) {
                        launchSingleTop = true
                    }
                }
            },
            isSelectMode = isSelectMode,
            selectedCount = selectedCount,
            hasDocuments = docCount > 0,
            isOrganizeMode = isOrganizeMode,
            onSelectToggle = ::toggleSelect,
            onSelectAll = { selectAllTrigger++ },
            onOrganizeToggle = ::toggleOrganize
        ) {
            AppNavHost(
                navController, startDestination, scannerViewModel, allDocsViewModel,
                mergeViewModel, dragState, isSelectMode, isOrganizeMode, selectAllTrigger,
                ::toggleSelect, ::toggleOrganize,
                { selectedCount = it }, { docCount = it },
                ::navigateToViewer, viewingDocument
            )
        }
    } else {
        AppNavHost(
            navController, startDestination, scannerViewModel, allDocsViewModel,
            mergeViewModel, dragState, isSelectMode, isOrganizeMode, selectAllTrigger,
            ::toggleSelect, ::toggleOrganize,
            { selectedCount = it }, { docCount = it },
            ::navigateToViewer, viewingDocument
        )
    }
}

@SuppressLint("ComposableDestinationInComposeScope")
@Composable
private fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    scannerViewModel: ScannerViewModel,
    allDocsViewModel: AllDocumentsViewModel,
    mergeViewModel: MergeViewModel,
    dragState: SidebarDragState,
    isSelectMode: Boolean,
    isOrganizeMode: Boolean,
    selectAllTrigger: Int,
    onSelectToggle: () -> Unit,
    onOrganizeToggle: () -> Unit,
    onSelectedCountChanged: (Int) -> Unit,
    onDocCountChanged: (Int) -> Unit,
    navigateToViewer: (Document) -> Unit,
    viewingDocument: Document?
) {
    val state by scannerViewModel.state.collectAsState()
    val mergeState by mergeViewModel.state.collectAsState()

    // Need sessionContext here too for onSessionClick
    val sessionContext: SessionContextViewModel = hiltViewModel()

    NavHost(navController = navController, startDestination = startDestination) {

        // ── Auth ──────────────────────────────────────────────────────────────
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.AppHome.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // ── App Home (session list) ───────────────────────────────────────────
        composable(Screen.AppHome.route) {
            AppHomeScreen(
                onSessionClick = { session ->
                    // Set session context on both ViewModels
                    sessionContext.setActiveSession(
                        sessionId = session.id,
                        applicationType = session.applicationType
                    )
                    allDocsViewModel.setSessionId(session.id)      // ← add
                    scannerViewModel.setSessionId(session.id)      // ← add
                    navController.navigate(Screen.AllDocuments.route) {
                        launchSingleTop = true
                    }
                },
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }

        // ── Profile & Settings ────────────────────────────────────────────────
        composable(Screen.Profile.route) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    sessionContext.clearActiveSession()
                    allDocsViewModel.setSessionId(null)     // ← add
                    scannerViewModel.setSessionId(null)     // ← add
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── Doc Scanner section (completely untouched) ────────────────────────
        composable(Screen.AllDocuments.route) {
            AllDocumentsScreen(
                dragState = dragState,
                isSelectMode = isSelectMode,
                isOrganizeMode = isOrganizeMode,
                selectAllTrigger = selectAllTrigger,
                onSelectToggle = onSelectToggle,
                onOrganizeToggle = onOrganizeToggle,
                onDocumentClick = { navigateToViewer(it) },
                onScanClick = {
                    scannerViewModel.onReset()
                    navController.navigate(Screen.Camera.route)
                },
                onSelectedCountChanged = onSelectedCountChanged,
                onDocumentCountChanged = onDocCountChanged,
                viewModel = allDocsViewModel
            )
        }

        composable(
            route = Screen.FolderDetail.route,
            arguments = listOf(
                navArgument("folderId") { type = NavType.StringType },
                navArgument("folderName") { type = NavType.StringType },
                navArgument("folderIcon") { type = NavType.StringType },
                navArgument("exportType") { type = NavType.StringType }
            )
        ) { back ->
            val folderId = back.arguments?.getString("folderId") ?: ""
            val folderName = back.arguments?.getString("folderName") ?: ""
            val folderIcon = back.arguments?.getString("folderIcon") ?: "📁"
            val exportType = back.arguments?.getString("exportType")
                ?.let { FolderExportType.valueOf(it) } ?: FolderExportType.PDF

            FolderDetailScreen(
                folderId = folderId,
                folderName = folderName,
                folderIcon = folderIcon,
                showTopBar = false,
                dragState = dragState,
                isSelectMode = isSelectMode,
                selectAllTrigger = selectAllTrigger,
                onSelectToggle = onSelectToggle,
                onStartScan = {
                    scannerViewModel.onReset()
                    scannerViewModel.setTargetFolder(folderId, folderName, exportType)
                    navController.navigate(Screen.Camera.route)
                },
                onOpenDocument = { navigateToViewer(it) },
                onMergeSelected = { selectedDocs, sourceFolderId ->
                    mergeViewModel.loadDocuments(selectedDocs, sourceFolderId)
                    navController.navigate(Screen.Reorder.route)
                },
                onBack = { navController.popBackStack() },
                onSelectedCountChanged = onSelectedCountChanged,
                onDocumentCountChanged = onDocCountChanged
            )
        }

        composable(Screen.Camera.route) {
            CameraPermissionHandler {
                CameraScreen(
                    pageCount = state.pages.size,
                    lastCapturedBitmap = state.pages.lastOrNull()?.displayBitmap,
                    isSaving = state.isSaving,
                    onPhotoCaptured = { bmp, corners ->
                        scannerViewModel.onPhotoCaptured(bmp, corners)
                    },
                    onDone = { scannerViewModel.onAutoSavePages() },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Screen.Reorder.route) {
            ReorderScreen(
                state = mergeState,
                onReorder = { from, to -> mergeViewModel.onReorder(from, to) },
                onRemoveItem = { idx -> mergeViewModel.onRemoveItem(idx) },
                onMerge = { mergeViewModel.onMerge() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Viewer.route,
            arguments = listOf(
                navArgument("docName") { type = NavType.StringType },
                navArgument("docType") { type = NavType.StringType },
                navArgument("docUri") { type = NavType.StringType }
            )
        ) { back ->
            val name = back.arguments?.getString("docName") ?: ""
            val type = back.arguments?.getString("docType")
                ?.let { DocumentType.valueOf(it) } ?: DocumentType.IMAGE
            val uri = back.arguments?.getString("docUri")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""

            DocumentViewerScreen(
                documentName = name,
                documentUri = uri,
                documentType = type,
                document = viewingDocument,
                onBack = { navController.popBackStack() },
                onRename = { newName ->
                    viewingDocument?.let { allDocsViewModel.renameDocument(it, newName) }
                    navController.popBackStack()
                },
                onChangeType = { label ->
                    viewingDocument?.let { allDocsViewModel.changeDocumentType(it, label) }
                    navController.popBackStack()
                },
                onDelete = {
                    viewingDocument?.let { allDocsViewModel.deleteDocument(it) }
                    navController.popBackStack()
                },
                onUnmerge = viewingDocument?.let { doc ->
                    if (doc.isMergedPdf) {
                        { mergeViewModel.unmerge(doc); navController.popBackStack() }
                    } else null
                }
            )
        }
    }
}