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
import com.example.docscanner.domain.model.Document
import com.example.docscanner.domain.model.FolderExportType
import com.example.docscanner.presentation.alldocuments.AllDocumentsScreen
import com.example.docscanner.presentation.alldocuments.AllDocumentsViewModel
import com.example.docscanner.presentation.alldocuments.SidebarDragState
import com.example.docscanner.presentation.camera.CameraPermissionHandler
import com.example.docscanner.presentation.camera.CameraScreen
import com.example.docscanner.presentation.folder.FolderDetailScreen
import com.example.docscanner.presentation.home.HomeViewModel
import com.example.docscanner.presentation.merge.MergeViewModel
import com.example.docscanner.presentation.merge.ReorderScreen
import com.example.docscanner.presentation.shared.ScannerViewModel
import com.example.docscanner.presentation.viewer.DocumentType
import com.example.docscanner.presentation.viewer.DocumentViewerScreen

// ── Routes ────────────────────────────────────────────────────────────────────

sealed class Screen(val route: String) {
    data object AllDocuments : Screen("all_documents")
    data object FolderDetail : Screen("folder/{folderId}/{folderName}/{folderIcon}/{exportType}") {
        fun createRoute(
            folderId: String, folderName: String,
            folderIcon: String, exportType: FolderExportType
        ) = "folder/$folderId/${folderName.enc()}/${folderIcon.enc()}/${exportType.name}"
    }
    data object Camera  : Screen("camera")
    data object Reorder : Screen("reorder")
    data object Profile : Screen("profile")
    data object Viewer  : Screen("viewer/{docName}/{docType}/{docUri}") {
        fun createRoute(name: String, type: DocumentType, uri: String) =
            "viewer/${name.enc()}/${type.name}/${uri.enc()}"
    }
}

private fun String.enc() = java.net.URLEncoder.encode(this, "UTF-8")

private val mainLayoutRoutes = setOf(
    Screen.AllDocuments.route,
    Screen.FolderDetail.route,
    Screen.Profile.route
)

// ── Root ──────────────────────────────────────────────────────────────────────

@SuppressLint("ComposableDestinationInComposeScope")
@Composable
fun DocScannerNavHost(
    navController: NavHostController = rememberNavController()
) {
    val scannerViewModel : ScannerViewModel      = hiltViewModel()
    val homeViewModel    : HomeViewModel         = hiltViewModel()
    val allDocsViewModel : AllDocumentsViewModel = hiltViewModel()
    val mergeViewModel   : MergeViewModel        = hiltViewModel()

    val state      by scannerViewModel.state.collectAsState()
    val mergeState by mergeViewModel.state.collectAsState()
    val folders    by homeViewModel.folders.collectAsState()

    val dragState = remember { SidebarDragState() }
    var selectedTab by rememberSaveable { mutableStateOf(BottomTab.ALL_DOCS) }
    var viewingDocument by remember { mutableStateOf<Document?>(null) }

    // ── After auto-save → pop back ───────────────────────────────────────────
    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            val targetFolderId = state.targetFolderId
            scannerViewModel.onSaveNavigated()
            scannerViewModel.onReset()
            if (targetFolderId.isNotEmpty()) {
                navController.popBackStack(Screen.FolderDetail.route, inclusive = false)
            } else {
                navController.popBackStack(Screen.AllDocuments.route, inclusive = false)
                selectedTab = BottomTab.ALL_DOCS
            }
        }
    }

    // ── After merge → pop back ───────────────────────────────────────────────
    LaunchedEffect(mergeState.mergeSuccess) {
        if (mergeState.mergeSuccess) {
            mergeViewModel.onMergeNavigated()
            navController.popBackStack()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val selectedSidebarId = remember(currentRoute, navBackStackEntry) {
        when {
            currentRoute?.startsWith("folder/") == true ->
                navBackStackEntry?.arguments?.getString("folderId") ?: ALL_DOCUMENTS_ID
            else -> ALL_DOCUMENTS_ID
        }
    }

    fun navigateToViewer(doc: Document) {
        val type = if (doc.pdfPath != null) DocumentType.PDF else DocumentType.IMAGE
        val uri  = doc.pdfPath ?: doc.thumbnailPath ?: return
        viewingDocument = doc
        navController.navigate(Screen.Viewer.createRoute(doc.name, type, uri))
    }

    if (currentRoute in mainLayoutRoutes) {
        MainLayout(
            folders                = folders,
            selectedSidebarId      = selectedSidebarId,
            dragState              = dragState,
            onAllDocumentsSelected = {
                selectedTab = BottomTab.ALL_DOCS
                navController.navigate(Screen.AllDocuments.route) {
                    popUpTo(Screen.AllDocuments.route) { inclusive = true }; launchSingleTop = true
                }
            },
            onFolderSelected = { folder ->
                navController.navigate(
                    Screen.FolderDetail.createRoute(folder.id, folder.name, folder.icon, folder.exportType)
                ) { launchSingleTop = true }
            },
            onDropToFolder  = { documentId, folderId -> allDocsViewModel.moveDocumentToFolder(documentId, folderId) },
            onFolderReorder = { from, to -> homeViewModel.reorderFolder(from, to) },
            selectedTab     = selectedTab,
            onTabSelected   = { tab ->
                selectedTab = tab
                when (tab) {
                    BottomTab.ALL_DOCS -> navController.navigate(Screen.AllDocuments.route) { popUpTo(Screen.AllDocuments.route) { inclusive = true }; launchSingleTop = true }
                    BottomTab.PROFILE  -> navController.navigate(Screen.Profile.route) { launchSingleTop = true }
                }
            }
        ) {
            AppNavHost(navController, scannerViewModel, allDocsViewModel, mergeViewModel, dragState, ::navigateToViewer, viewingDocument)
        }
    } else {
        AppNavHost(navController, scannerViewModel, allDocsViewModel, mergeViewModel, dragState, ::navigateToViewer, viewingDocument)
    }
}

// ── Inner NavHost ─────────────────────────────────────────────────────────────

@SuppressLint("ComposableDestinationInComposeScope")
@Composable
private fun AppNavHost(
    navController    : NavHostController,
    scannerViewModel : ScannerViewModel,
    allDocsViewModel : AllDocumentsViewModel,
    mergeViewModel   : MergeViewModel,
    dragState        : SidebarDragState,
    navigateToViewer : (Document) -> Unit,
    viewingDocument  : Document?
) {
    val state      by scannerViewModel.state.collectAsState()
    val mergeState by mergeViewModel.state.collectAsState()

    NavHost(
        navController    = navController,
        startDestination = Screen.AllDocuments.route
    ) {
        composable(Screen.AllDocuments.route) {
            AllDocumentsScreen(
                dragState       = dragState,
                onDocumentClick = { doc -> navigateToViewer(doc) },
                onScanClick     = { scannerViewModel.onReset(); navController.navigate(Screen.Camera.route) },
                viewModel = allDocsViewModel
            )
        }

        composable(
            route     = Screen.FolderDetail.route,
            arguments = listOf(
                navArgument("folderId")   { type = NavType.StringType },
                navArgument("folderName") { type = NavType.StringType },
                navArgument("folderIcon") { type = NavType.StringType },
                navArgument("exportType") { type = NavType.StringType }
            )
        ) { back ->
            val folderId   = back.arguments?.getString("folderId")   ?: ""
            val folderName = back.arguments?.getString("folderName") ?: ""
            val folderIcon = back.arguments?.getString("folderIcon") ?: "📁"
            val exportType = back.arguments?.getString("exportType")
                ?.let { FolderExportType.valueOf(it) } ?: FolderExportType.PDF

            FolderDetailScreen(
                folderId       = folderId,
                folderName     = folderName,
                folderIcon     = folderIcon,
                showTopBar     = false,
                dragState      = dragState,
                onStartScan    = {
                    scannerViewModel.onReset()
                    scannerViewModel.setTargetFolder(folderId, folderName, exportType)
                    navController.navigate(Screen.Camera.route)
                },
                onOpenDocument  = { doc -> navigateToViewer(doc) },
                onMergeSelected = { selectedDocs, sourceFolderId ->
                    mergeViewModel.loadDocuments(selectedDocs, sourceFolderId)
                    navController.navigate(Screen.Reorder.route)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Camera.route) {
            CameraPermissionHandler {
                CameraScreen(
                    pageCount          = state.pages.size,
                    lastCapturedBitmap = state.pages.lastOrNull()?.displayBitmap,
                    isSaving           = state.isSaving,
                    onPhotoCaptured    = { bmp, corners -> scannerViewModel.onPhotoCaptured(bmp, corners) },
                    onDone             = { scannerViewModel.onAutoSavePages() },
                    onBack             = { navController.popBackStack() }
                )
            }
        }

        composable(Screen.Reorder.route) {
            ReorderScreen(
                state        = mergeState,
                onReorder    = { from, to -> mergeViewModel.onReorder(from, to) },
                onRemoveItem = { idx -> mergeViewModel.onRemoveItem(idx) },
                onMerge      = { mergeViewModel.onMerge() },
                onBack       = { navController.popBackStack() }
            )
        }

        composable(Screen.Profile.route) { ProfilePlaceholderScreen() }

        composable(
            route     = Screen.Viewer.route,
            arguments = listOf(
                navArgument("docName") { type = NavType.StringType },
                navArgument("docType") { type = NavType.StringType },
                navArgument("docUri")  { type = NavType.StringType }
            )
        ) { back ->
            val name = back.arguments?.getString("docName") ?: ""
            val type = back.arguments?.getString("docType")?.let { DocumentType.valueOf(it) } ?: DocumentType.IMAGE
            val uri  = back.arguments?.getString("docUri")?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""

            DocumentViewerScreen(
                documentName = name,
                documentUri  = uri,
                documentType = type,
                document     = viewingDocument,
                onBack       = { navController.popBackStack() },
                onRename     = { newName ->
                    viewingDocument?.let { doc -> allDocsViewModel.renameDocument(doc, newName) }
                    navController.popBackStack()
                },
                onChangeType = { label ->
                    viewingDocument?.let { doc -> allDocsViewModel.changeDocumentType(doc, label) }
                    navController.popBackStack()
                },
                onDelete = {
                    viewingDocument?.let { doc -> allDocsViewModel.deleteDocument(doc) }
                    navController.popBackStack()
                },
                onUnmerge = viewingDocument?.let { doc ->
                    if (doc.isMergedPdf) {{ mergeViewModel.unmerge(doc); navController.popBackStack() }} else null
                }
            )
        }
    }
}