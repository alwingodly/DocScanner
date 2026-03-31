package com.example.docscanner.navigation

import android.annotation.SuppressLint
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.docscanner.domain.model.Document
import com.example.docscanner.presentation.alldocuments.AllDocumentsScreen
import com.example.docscanner.presentation.alldocuments.AllDocumentsViewModel
import com.example.docscanner.presentation.alldocuments.DocumentTab
import com.example.docscanner.presentation.alldocuments.GroupDetailScreen
import com.example.docscanner.presentation.apphome.AppHomeScreen
import com.example.docscanner.presentation.apphome.SessionContextViewModel
import com.example.docscanner.presentation.camera.CameraPermissionHandler
import com.example.docscanner.presentation.camera.CameraScreen
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
    data object Camera : Screen("camera")
    data object Reorder : Screen("reorder")
    data object Viewer : Screen("viewer/{docName}/{docType}/{docUri}") {
        fun createRoute(name: String, type: DocumentType, uri: String) =
            "viewer/${name.enc()}/${type.name}/${uri.enc()}"
    }
    data object GroupDetail : Screen("group_detail/{groupId}") {
        fun createRoute(groupId: String) = "group_detail/${groupId.enc()}"
    }
}

private fun String.enc() = java.net.URLEncoder.encode(this, "UTF-8")

@SuppressLint("ComposableDestinationInComposeScope")
@Composable
fun DocScannerNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Login.route
) {
    val scannerViewModel: ScannerViewModel = hiltViewModel()
    val allDocsViewModel: AllDocumentsViewModel = hiltViewModel()
    val mergeViewModel: MergeViewModel = hiltViewModel()
    val sessionContext: SessionContextViewModel = hiltViewModel()

    val state by scannerViewModel.state.collectAsState()
    val mergeState by mergeViewModel.state.collectAsState()

    var viewingDocument by remember { mutableStateOf<Document?>(null) }
    var isSelectMode by remember { mutableStateOf(false) }
    var selectedCount by remember { mutableIntStateOf(0) }
    var docCount by remember { mutableIntStateOf(0) }
    var selectAllTrigger by remember { mutableIntStateOf(0) }
    var columnCount by remember { mutableIntStateOf(3) }
    var selectedTab by remember { mutableStateOf(DocumentTab.ALL) }

    fun toggleSelect() {
        isSelectMode = !isSelectMode
        if (!isSelectMode) selectAllTrigger = 0
    }

    LaunchedEffect(state.saveSuccess) {

        if (state.saveSuccess) {
            scannerViewModel.onSaveNavigated()
            navController.popBackStack(Screen.AllDocuments.route, inclusive = false)
            if (state.pendingMismatches.isEmpty()) {
                scannerViewModel.onReset()
                // Do NOT call clearAadhaarGroup() here — user may scan back card next
            }
        }
    }

    LaunchedEffect(mergeState.mergeSuccess) {
        if (mergeState.mergeSuccess) {
            mergeViewModel.onMergeNavigated()
            navController.popBackStack()
        }
    }

    fun navigateToViewer(doc: Document) {
        val type = if (doc.pdfPath != null) DocumentType.PDF else DocumentType.IMAGE
        val uri = doc.pdfPath ?: doc.thumbnailPath ?: return
        viewingDocument = doc
        navController.navigate(Screen.Viewer.createRoute(doc.name, type, uri))
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Login.route) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(Screen.AppHome.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            })
        }

        composable(Screen.AppHome.route) {
            AppHomeScreen(
                onSessionClick = { session ->
                    sessionContext.setActiveSession(
                        sessionId = session.id,
                        applicationType = session.applicationType
                    )
                    allDocsViewModel.setSessionId(session.id)
                    scannerViewModel.setSessionId(session.id)
                    navController.navigate(Screen.AllDocuments.route) { launchSingleTop = true }
                },
                onProfileClick = { navController.navigate(Screen.Profile.route) }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    sessionContext.clearActiveSession()
                    allDocsViewModel.setSessionId(null)
                    scannerViewModel.setSessionId(null)
                    navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                }
            )
        }

        composable(Screen.AllDocuments.route) {
            MainLayout(
                isSelectMode = isSelectMode,
                selectedCount = selectedCount,
                documentCount = docCount,
                hasDocuments = docCount > 0,
                columnCount = columnCount,
                selectedTab = selectedTab,
                onTabChange = { tab ->
                    if (isSelectMode) isSelectMode = false
                    selectedTab = tab
                },
                onSelectToggle = ::toggleSelect,
                onSelectAll = { selectAllTrigger++ },
                onColumnChange = { columnCount = it }
            ) {
                AllDocumentsScreen(
                    isSelectMode = isSelectMode,
                    selectAllTrigger = selectAllTrigger,
                    onSelectToggle = ::toggleSelect,
                    onEnterSelectMode = { isSelectMode = true },
                    onDocumentClick = { navigateToViewer(it) },
                    onScanClick = {
                        scannerViewModel.onReset()
                        navController.navigate(Screen.Camera.route)
                    },
                    onScanToFolder = { folder ->
                        scannerViewModel.onReset()
                        scannerViewModel.setTargetFolder(
                            folderId = folder.id,
                            folderName = folder.name,
                            exportType = folder.exportType,
                            docType = folder.docType
                        )
                        navController.navigate(Screen.Camera.route)
                    },
                    columnCount = columnCount,
                    showUnclassifiedOnly = selectedTab == DocumentTab.UNCLASSIFIED,
                    onMergeSelected = { selectedDocs ->
                        mergeViewModel.loadDocuments(selectedDocs, "")
                        navController.navigate(Screen.Reorder.route)
                    },
                    onSelectedCountChanged = { selectedCount = it },
                    onDocumentCountChanged = { docCount = it },
                    // ── Mismatch resolution ───────────────────────────────────
                    pendingMismatches = state.pendingMismatches,
                    onResolveMismatch = { docId, chosenLabel ->
                        // Find the saved doc by id and update its label
                        // changeDocumentType also triggers masking if label is Aadhaar/PAN
                        val doc = allDocsViewModel.documents.value.firstOrNull { it.id == docId }
                        if (doc != null) {
                            allDocsViewModel.changeDocumentType(doc, chosenLabel)
                        }
                    },
                    onDismissMismatches = {
                        scannerViewModel.dismissMismatches()
                        scannerViewModel.onReset()
                        // Do NOT clear Aadhaar group here either
                    },
                    viewModel = allDocsViewModel,
                    onGroupTap = { groupId ->
                        navController.navigate(Screen.GroupDetail.createRoute(groupId))
                    },
                )
            }
        }

        composable(Screen.Camera.route) {
            CameraPermissionHandler {
                CameraScreen(
                    pageCount          = state.pages.size,
                    lastCapturedBitmap = state.pages.lastOrNull()?.displayBitmap,
                    isSaving           = state.isSaving,
                    onPhotoCaptured    = { bmp, corners -> scannerViewModel.onPhotoCaptured(bmp, corners) },
                    onDone             = { scannerViewModel.onAutoSavePages() },
                    onBack = {
                        // Only clear if user didn't save anything — if pages exist they cancelled
                        if (state.pages.isNotEmpty()) {
                            scannerViewModel.clearAadhaarGroup()
                        }
                        scannerViewModel.onReset()
                        navController.popBackStack()
                    }
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
            route     = Screen.GroupDetail.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { back ->
            val groupId = back.arguments?.getString("groupId")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
            GroupDetailScreen(
                groupId    = groupId,
                onBack     = { navController.popBackStack() },
                onDocClick = { navigateToViewer(it) },
                viewModel  = allDocsViewModel
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