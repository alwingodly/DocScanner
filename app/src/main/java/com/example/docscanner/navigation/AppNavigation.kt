package com.example.docscanner.navigation

import android.annotation.SuppressLint
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.docscanner.domain.model.Document
import com.example.docscanner.domain.model.AadhaarGroup
import com.example.docscanner.presentation.alldocuments.AllDocumentsScreen
import com.example.docscanner.presentation.alldocuments.AllDocumentsViewModel
import com.example.docscanner.presentation.alldocuments.DocumentTab
import com.example.docscanner.presentation.alldocuments.EditDetailsScreen
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
import androidx.compose.runtime.collectAsState

// ── Transition helpers ────────────────────────────────────────────────────

private val enterFromRight = slideInHorizontally(initialOffsetX = { it }) + fadeIn()
private val exitToLeft     = slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut()
private val enterFromLeft  = slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn()
private val exitToRight    = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()

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
    data object EditDetails : Screen("edit_details/{docId}") {
        fun createRoute(docId: String) = "edit_details/${docId.enc()}"
    }
}

private fun String.enc() = java.net.URLEncoder.encode(this, "UTF-8")

@SuppressLint("ComposableDestinationInComposeScope")
@Composable
fun DocScannerNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Login.route
) {
    val scannerViewModel: ScannerViewModel          = hiltViewModel()
    val allDocsViewModel: AllDocumentsViewModel     = hiltViewModel()
    val mergeViewModel  : MergeViewModel            = hiltViewModel()
    val sessionContext  : SessionContextViewModel   = hiltViewModel()

    val state        by scannerViewModel.state.collectAsState()
    val mergeState   by mergeViewModel.state.collectAsState()
    val allDocuments by allDocsViewModel.documents.collectAsState()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute  = navBackStack?.destination?.route
    val unclassifiedCount = remember(allDocuments) {
        allDocuments.count { it.docClassLabel == null || it.docClassLabel == "Other" }
    }

    // ── Viewer anchor: only the IDs are stored; live docs come from the VM ──
    var viewingDocumentId   by remember { mutableStateOf<String?>(null) }
    var viewerDocumentIds   by remember { mutableStateOf<List<String>>(emptyList()) }
    var viewerInitialIndex  by remember { mutableIntStateOf(0) }

    var isSelectMode    by remember { mutableStateOf(false) }
    var selectedCount   by remember { mutableIntStateOf(0) }
    var docCount        by remember { mutableIntStateOf(0) }
    var selectAllTrigger by remember { mutableIntStateOf(0) }
    var columnCount     by remember { mutableIntStateOf(3) }
    var selectedTab     by remember { mutableStateOf(DocumentTab.ALL) }

    fun toggleSelect() {
        isSelectMode = !isSelectMode
        if (!isSelectMode) selectAllTrigger = 0
    }

    LaunchedEffect(state.isSaving, currentRoute) {
        if (state.isSaving && currentRoute == Screen.Camera.route) {
            navController.popBackStack(Screen.AllDocuments.route, inclusive = false)
        }
    }

    LaunchedEffect(state.saveSuccess, currentRoute) {
        if (state.saveSuccess) {
            scannerViewModel.onSaveNavigated()
            if (currentRoute != Screen.AllDocuments.route) {
                navController.popBackStack(Screen.AllDocuments.route, inclusive = false)
            }
            if (state.pendingMismatches.isEmpty()) {
                scannerViewModel.onReset(keepTarget = false, keepFeedback = true)
            }
        }
    }

    LaunchedEffect(mergeState.mergeSuccess) {
        if (mergeState.mergeSuccess) {
            mergeViewModel.onMergeNavigated()
            navController.popBackStack()
        }
    }

    fun navigateToViewer(doc: Document, docs: List<Document> = listOf(doc)) {
        val type = if (doc.pdfPath != null) DocumentType.PDF else DocumentType.IMAGE
        val uri  = doc.pdfPath ?: doc.thumbnailPath ?: return
        viewingDocumentId  = doc.id
        viewerDocumentIds  = docs.map { it.id }
        viewerInitialIndex = docs.indexOfFirst { it.id == doc.id }.takeIf { it >= 0 } ?: 0
        navController.navigate(Screen.Viewer.createRoute(doc.name, type, uri))
    }

    NavHost(
        navController    = navController,
        startDestination = startDestination
    ) {

        // ── Login ──────────────────────────────────────────────────────────
        composable(
            route            = Screen.Login.route,
            enterTransition  = { enterFromRight },
            exitTransition   = { exitToLeft },
            popEnterTransition  = { enterFromLeft },
            popExitTransition   = { exitToRight }
        ) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(Screen.AppHome.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            })
        }

        // ── AppHome ────────────────────────────────────────────────────────
        composable(
            route            = Screen.AppHome.route,
            enterTransition  = { enterFromRight },
            exitTransition   = { exitToLeft },
            popEnterTransition  = { enterFromLeft },
            popExitTransition   = { exitToRight }
        ) {
            AppHomeScreen(
                onSessionClick = { session ->
                    sessionContext.setActiveSession(
                        sessionId       = session.id,
                        applicationType = session.applicationType
                    )
                    allDocsViewModel.setSessionId(session.id)
                    scannerViewModel.setSessionId(session.id)
                    navController.navigate(Screen.AllDocuments.route) { launchSingleTop = true }
                },
                onProfileClick = { navController.navigate(Screen.Profile.route) }
            )
        }

        // ── Profile ────────────────────────────────────────────────────────
        composable(
            route            = Screen.Profile.route,
            enterTransition  = { enterFromRight },
            exitTransition   = { exitToLeft },
            popEnterTransition  = { enterFromLeft },
            popExitTransition   = { exitToRight }
        ) {
            ProfileScreen(
                onBack   = { navController.popBackStack() },
                onLogout = {
                    sessionContext.clearActiveSession()
                    allDocsViewModel.setSessionId(null)
                    scannerViewModel.setSessionId(null)
                    navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                }
            )
        }

        // ── AllDocuments ───────────────────────────────────────────────────
        composable(
            route            = Screen.AllDocuments.route,
            enterTransition  = { enterFromRight },
            exitTransition   = { exitToLeft },
            popEnterTransition  = { enterFromLeft },
            popExitTransition   = { exitToRight }
        ) {
            MainLayout(
                isSelectMode  = isSelectMode,
                selectedCount = selectedCount,
                documentCount = docCount,
                unclassifiedCount = unclassifiedCount,
                hasDocuments  = docCount > 0,
                columnCount   = columnCount,
                selectedTab   = selectedTab,
                onTabChange   = { tab ->
                    if (isSelectMode) isSelectMode = false
                    selectedTab = tab
                },
                onSelectToggle = ::toggleSelect,
                onSelectAll    = { selectAllTrigger++ },
                onColumnChange = { columnCount = it }
            ) {
                AllDocumentsScreen(
                    isSelectMode          = isSelectMode,
                    selectAllTrigger      = selectAllTrigger,
                    onSelectToggle        = ::toggleSelect,
                    onEnterSelectMode     = { isSelectMode = true },
                    onDocumentClick       = { navigateToViewer(it) },
                    onScanClick           = {
                        if (!state.isSaving) {
                            scannerViewModel.onReset(keepTarget = false)
                            navController.navigate(Screen.Camera.route)
                        }
                    },
                    onScanToFolder        = { folder ->
                        if (!state.isSaving) {
                            scannerViewModel.onReset(keepTarget = false)
                            scannerViewModel.setTargetFolder(
                                folderId   = folder.id,
                                folderName = folder.name,
                                exportType = folder.exportType,
                                docType    = folder.docType
                            )
                            navController.navigate(Screen.Camera.route)
                        }
                    },
                    columnCount           = columnCount,
                    showUnclassifiedOnly  = selectedTab == DocumentTab.UNCLASSIFIED,
                    onMergeSelected       = { selectedDocs ->
                        mergeViewModel.loadDocuments(selectedDocs, "")
                        navController.navigate(Screen.Reorder.route)
                    },
                    onSelectedCountChanged  = { selectedCount = it },
                    onDocumentCountChanged  = { docCount = it },
                    pendingMismatches       = state.pendingMismatches,
                    onResolveMismatch       = { docId, chosenLabel ->
                        val doc = allDocsViewModel.documents.value.firstOrNull { it.id == docId }
                        if (doc != null) allDocsViewModel.changeDocumentType(doc, chosenLabel)
                    },
                    onDismissMismatches     = {
                        scannerViewModel.dismissMismatches()
                        scannerViewModel.onReset()
                    },
                    isScanProcessing        = state.isSaving,
                    scanFeedback            = state.saveFeedback,
                    onScanFeedbackShown     = { scannerViewModel.clearSaveFeedback() },
                    viewModel               = allDocsViewModel,
                    onGroupTap              = { groupId ->
                        navController.navigate(Screen.GroupDetail.createRoute(groupId))
                    }
                )
            }
        }

        // ── Camera ─────────────────────────────────────────────────────────
        composable(
            route            = Screen.Camera.route,
            enterTransition  = { enterFromRight },
            exitTransition   = { exitToLeft },
            popEnterTransition  = { enterFromLeft },
            popExitTransition   = { exitToRight }
        ) {
            CameraPermissionHandler {
                CameraScreen(
                    isSaving       = state.isSaving,
                    onImportPages  = { uris -> scannerViewModel.onImportedPages(uris) },
                    onBack         = {
                        if (state.pages.isNotEmpty()) scannerViewModel.clearAadhaarGroup()
                        scannerViewModel.onReset(keepTarget = false)
                        navController.popBackStack()
                    }
                )
            }
        }

        // ── Reorder ────────────────────────────────────────────────────────
        composable(
            route            = Screen.Reorder.route,
            enterTransition  = { enterFromRight },
            exitTransition   = { exitToLeft },
            popEnterTransition  = { enterFromLeft },
            popExitTransition   = { exitToRight }
        ) {
            ReorderScreen(
                state        = mergeState,
                onReorder    = { from, to -> mergeViewModel.onReorder(from, to) },
                onRemoveItem = { idx -> mergeViewModel.onRemoveItem(idx) },
                onMerge      = { mergeViewModel.onMerge() },
                onBack       = { navController.popBackStack() }
            )
        }

        // ── GroupDetail ────────────────────────────────────────────────────
        composable(
            route            = Screen.GroupDetail.route,
            arguments        = listOf(navArgument("groupId") { type = NavType.StringType }),
            enterTransition  = { enterFromRight },
            exitTransition   = { exitToLeft },
            popEnterTransition  = { enterFromLeft },
            popExitTransition   = { exitToRight }
        ) { back ->
            val groupId = back.arguments?.getString("groupId")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
            GroupDetailScreen(
                groupId      = groupId,
                onBack       = { navController.popBackStack() },
                onDocClick   = { doc, docs -> navigateToViewer(doc, docs) },
                onEditDetails = { doc ->
                    navController.navigate(Screen.EditDetails.createRoute(doc.id))
                },
                viewModel    = allDocsViewModel
            )
        }

        // ── EditDetails ────────────────────────────────────────────────────
        composable(
            route            = Screen.EditDetails.route,
            arguments        = listOf(navArgument("docId") { type = NavType.StringType }),
            enterTransition  = { enterFromRight },
            exitTransition   = { exitToLeft },
            popEnterTransition  = { enterFromLeft },
            popExitTransition   = { exitToRight }
        ) { back ->
            val docId = back.arguments?.getString("docId")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""

            // Collect live so edits from this screen reflect immediately on pop
            val allDocs by allDocsViewModel.documents.collectAsState()
            val doc = allDocs.firstOrNull { it.id == docId }

            if (doc != null) {
                EditDetailsScreen(
                    document = doc,
                    onSave   = { details -> allDocsViewModel.updateDocumentExtractedDetails(doc, details) },
                    onBack   = { navController.popBackStack() }
                )
            }
        }

        // ── Viewer ─────────────────────────────────────────────────────────
        composable(
            route     = Screen.Viewer.route,
            arguments = listOf(
                navArgument("docName") { type = NavType.StringType },
                navArgument("docType") { type = NavType.StringType },
                navArgument("docUri")  { type = NavType.StringType }
            ),
            enterTransition  = { enterFromRight },
            exitTransition   = { exitToLeft },
            popEnterTransition  = { enterFromLeft },
            popExitTransition   = { exitToRight }
        ) { back ->
            val name = back.arguments?.getString("docName") ?: ""
            val type = back.arguments?.getString("docType")
                ?.let { DocumentType.valueOf(it) } ?: DocumentType.IMAGE
            val uri  = back.arguments?.getString("docUri")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""

            // ── Live derivation: re-look up docs from the VM on every recompose ──
            val allDocs by allDocsViewModel.documents.collectAsState()

            val liveDoc = remember(allDocs, viewingDocumentId) {
                allDocs.firstOrNull { it.id == viewingDocumentId }
            }
            val liveDocs = remember(allDocs, viewerDocumentIds) {
                val byId = allDocs.associateBy { it.id }
                viewerDocumentIds.mapNotNull { byId[it] }
                    .ifEmpty { listOfNotNull(liveDoc) }
            }

            DocumentViewerScreen(
                documentName         = name,
                documentUri          = uri,
                documentType         = type,
                document             = liveDoc,
                documents            = liveDocs,
                initialIndex         = viewerInitialIndex,
                allFolders           = allDocsViewModel.allFolders.collectAsState().value,
                onBack               = { navController.popBackStack() },
                onRename             = { doc, newName ->
                    allDocsViewModel.renameDocument(doc, newName)
                    navController.popBackStack()
                },
                onChangeType         = { doc, label ->
                    allDocsViewModel.changeDocumentType(doc, label)
                    navController.popBackStack()
                },
                onDelete             = { doc ->
                    allDocsViewModel.deleteDocument(doc)
                    navController.popBackStack()
                },
                onUnmerge            = { doc ->
                    if (doc.isMergedPdf) {
                        mergeViewModel.unmerge(doc)
                        navController.popBackStack()
                    }
                },
                onEditDetails        = { doc ->
                    navController.navigate(Screen.EditDetails.createRoute(doc.id))
                },
                onRenameAadhaarPair  = { doc, newName ->
                    val group     = allDocsViewModel.aadhaarGroups.value
                        .firstOrNull { it.groupId == doc.aadhaarGroupId }
                    val sanitized = newName.trim().replace(" ", "_")
                    group?.frontDoc?.let {
                        allDocsViewModel.renameDocument(it, "${sanitized}_Aadhaar_Front")
                    }
                    group?.backDoc?.let {
                        allDocsViewModel.renameDocument(it, "${sanitized}_Aadhaar_Back")
                    }
                },
                onUngroupAadhaarPair = { doc ->
                    val group = allDocsViewModel.aadhaarGroups.value
                        .firstOrNull { it.groupId == doc.aadhaarGroupId }
                    group?.let {
                        allDocsViewModel.ungroupAadhaar(it)
                        navController.popBackStack()
                    }
                },
                onPairPassport = { _ ->
                    // Navigate back to AllDocuments; the pending-pair toast is shown in the viewer.
                    navController.popBackStack()
                },
                onUngroupPassport = { doc ->
                    val group = allDocsViewModel.passportGroups.value
                        .firstOrNull { it.groupId == doc.passportGroupId }
                    group?.let {
                        allDocsViewModel.ungroupPassport(it)
                        navController.popBackStack()
                    }
                }
            )
        }
    }
}
