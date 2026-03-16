package com.example.docscanner.navigation

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.docscanner.domain.model.FolderExportType
import com.example.docscanner.presentation.alldocuments.AllDocumentsScreen
import com.example.docscanner.presentation.alldocuments.AllDocumentsViewModel
import com.example.docscanner.presentation.alldocuments.SidebarDragState
import com.example.docscanner.presentation.camera.CameraPermissionHandler
import com.example.docscanner.presentation.camera.CameraScreen
import com.example.docscanner.presentation.edit.EditScreen
import com.example.docscanner.presentation.folder.FolderDetailScreen
import com.example.docscanner.presentation.home.HomeViewModel
import com.example.docscanner.presentation.review.ReviewScreen
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
    data object Review  : Screen("review")
    data object Edit    : Screen("edit")
    data object Preview : Screen("preview")
    data object Profile : Screen("profile")
    data object Viewer  : Screen("viewer/{docName}/{docType}/{docUri}") {
        fun createRoute(name: String, type: DocumentType, uri: String) =
            "viewer/${name.enc()}/${type.name}/${uri.enc()}"
    }
}

private fun String.enc() = java.net.URLEncoder.encode(this, "UTF-8")

/** Routes that render inside MainLayout (sidebar always visible) */
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

    val state   by scannerViewModel.state.collectAsState()
    val folders by homeViewModel.folders.collectAsState()

    val dragState = remember { SidebarDragState() }

    var selectedTab by rememberSaveable { mutableStateOf(BottomTab.ALL_DOCS) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scannerViewModel.onReset()
            navController.navigate(Screen.Camera.route)
        }
    }

    // ── After edit, pop back ──────────────────────────────────────────────────
    LaunchedEffect(state.editDone) {
        if (state.editDone) {
            navController.popBackStack()
            scannerViewModel.onEditNavigated()
        }
    }

    // ── After save → pop back to where the user scanned from ─────────────────
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

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val selectedSidebarId = remember(currentRoute, navBackStackEntry) {
        when {
            currentRoute?.startsWith("folder/") == true ->
                navBackStackEntry?.arguments?.getString("folderId") ?: ALL_DOCUMENTS_ID
            else -> ALL_DOCUMENTS_ID
        }
    }

    if (currentRoute in mainLayoutRoutes) {
        MainLayout(
            folders                = folders,
            selectedSidebarId      = selectedSidebarId,
            dragState              = dragState,
            onAllDocumentsSelected = {
                selectedTab = BottomTab.ALL_DOCS
                navController.navigate(Screen.AllDocuments.route) {
                    popUpTo(Screen.AllDocuments.route) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onFolderSelected = { folder ->
                navController.navigate(
                    Screen.FolderDetail.createRoute(
                        folderId   = folder.id,
                        folderName = folder.name,
                        folderIcon = folder.icon,
                        exportType = folder.exportType
                    )
                ) { launchSingleTop = true }
            },
            onDropToFolder = { documentId, folderId ->
                allDocsViewModel.moveDocumentToFolder(documentId, folderId)
            },
            onFolderReorder = { from, to ->
                homeViewModel.reorderFolder(from, to)
            },
            selectedTab   = selectedTab,
            onTabSelected = { tab ->
                selectedTab = tab
                when (tab) {
                    BottomTab.ALL_DOCS -> navController.navigate(Screen.AllDocuments.route) {
                        popUpTo(Screen.AllDocuments.route) { inclusive = true }
                        launchSingleTop = true
                    }
                    BottomTab.PROFILE  -> navController.navigate(Screen.Profile.route) {
                        launchSingleTop = true
                    }
                }
            }
        ) {
            AppNavHost(navController, scannerViewModel, allDocsViewModel, dragState)
        }
    } else {
        AppNavHost(navController, scannerViewModel, allDocsViewModel, dragState)
    }
}

// ── Inner NavHost ─────────────────────────────────────────────────────────────

@SuppressLint("ComposableDestinationInComposeScope")
@Composable
private fun AppNavHost(
    navController    : NavHostController,
    scannerViewModel : ScannerViewModel,
    allDocsViewModel : AllDocumentsViewModel,
    dragState        : SidebarDragState
) {
    val state by scannerViewModel.state.collectAsState()

    NavHost(
        navController    = navController,
        startDestination = Screen.AllDocuments.route
    ) {
        // ── All Documents ─────────────────────────────────────────────────────
        composable(Screen.AllDocuments.route) {
            AllDocumentsScreen(
                dragState       = dragState,
                onDocumentClick = { doc ->
                    // ── FIX: check pdfPath first, open as PDF if available ────
                    val type = if (doc.pdfPath != null) DocumentType.PDF else DocumentType.IMAGE
                    val uri  = doc.pdfPath ?: doc.thumbnailPath ?: return@AllDocumentsScreen
                    navController.navigate(
                        Screen.Viewer.createRoute(doc.name, type, uri)
                    )
                },
                onScanClick = {
                    scannerViewModel.onReset()
                    navController.navigate(Screen.Camera.route)
                },
                viewModel = allDocsViewModel
            )
        }

        // ── Folder Detail ─────────────────────────────────────────────────────
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
                onOpenDocument = { uri, type, name ->
                    navController.navigate(Screen.Viewer.createRoute(name, type, uri))
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Camera ────────────────────────────────────────────────────────────
        composable(Screen.Camera.route) {
            CameraPermissionHandler {
                CameraScreen(
                    pageCount          = state.pages.size,
                    lastCapturedBitmap = state.pages.lastOrNull()?.displayBitmap,
                    onPhotoCaptured    = { bmp, corners -> scannerViewModel.onPhotoCaptured(bmp, corners) },
                    onPreview          = { navController.navigate(Screen.Preview.route) },
                    onDone             = { navController.navigate(Screen.Review.route) },
                    onBack             = { navController.popBackStack() }
                )
            }
        }

        // ── Review ────────────────────────────────────────────────────────────
        composable(Screen.Review.route) {
            ReviewScreen(
                pages              = state.pages,
                isProcessing       = state.isProcessing || state.isSaving,
                onDeletePage       = { scannerViewModel.onDeletePage(it) },
                onReorderPage      = { from, to -> scannerViewModel.onReorderPage(from, to) },
                onEditPage         = {
                    scannerViewModel.onSelectPageForEdit(it)
                    navController.navigate(Screen.Edit.route)
                },
                onApplyFilterToAll = { scannerViewModel.onBatchFilterApply(it) },
                onAddMore          = { navController.navigate(Screen.Camera.route) },
                onSaveAsPdf        = { scannerViewModel.onSaveToFolderAsPdf() },
                onSaveAsImages     = { scannerViewModel.onSaveToFolderAsImages() },
                onBack             = { navController.popBackStack() }
            )
        }

        // ── Edit ──────────────────────────────────────────────────────────────
        composable(Screen.Edit.route) {
            val editPage = state.editingPageIndex?.let { state.pages.getOrNull(it) }
            EditScreen(
                page                        = editPage,
                currentFilter               = state.editingFilter,
                isProcessing                = state.isProcessing,
                showApplyToAllPrompt        = state.showApplyToAllPrompt,
                totalPages                  = state.pages.size,
                onFilterSelected            = { scannerViewModel.onEditFilterSelected(it) },
                onBrightnessContrastChanged = { b, c -> scannerViewModel.onEditBrightnessContrast(b, c) },
                onCornersChanged            = { scannerViewModel.onEditCornersChanged(it) },
                onDone                      = { scannerViewModel.onEditDone() },
                onApplyToAll                = { scannerViewModel.onApplyEditingFilterToAll() },
                onDismissApplyToAll         = { scannerViewModel.onDismissApplyToAll() }
            )
        }

        // ── Profile ───────────────────────────────────────────────────────────
        composable(Screen.Profile.route) {
            ProfilePlaceholderScreen()
        }

        // ── Viewer ────────────────────────────────────────────────────────────
        composable(
            route     = Screen.Viewer.route,
            arguments = listOf(
                navArgument("docName") { type = NavType.StringType },
                navArgument("docType") { type = NavType.StringType },
                navArgument("docUri")  { type = NavType.StringType }
            )
        ) { back ->
            val name = back.arguments?.getString("docName") ?: ""
            val type = back.arguments?.getString("docType")
                ?.let { DocumentType.valueOf(it) } ?: DocumentType.IMAGE
            val uri  = back.arguments?.getString("docUri")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
            DocumentViewerScreen(
                documentName = name,
                documentUri  = uri,
                documentType = type,
                onBack       = { navController.popBackStack() }
            )
        }
    }
}