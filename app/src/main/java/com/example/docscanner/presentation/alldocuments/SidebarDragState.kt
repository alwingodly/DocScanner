package com.example.docscanner.presentation.alldocuments

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SidebarDragState {

    var isDragging by mutableStateOf(false)
        private set

    var draggingDocumentId by mutableStateOf<String?>(null)
        private set

    var dragOffsetX by mutableStateOf(0f)
        private set

    var dragOffsetY by mutableStateOf(0f)
        private set

    var startX by mutableStateOf(0f)
        private set

    var startY by mutableStateOf(0f)
        private set

    var draggingThumbnailPath by mutableStateOf<String?>(null)
        private set

    var draggingDocumentName by mutableStateOf("")
        private set

    var draggingPageCount by mutableStateOf(0)
        private set

    // ── Group drag fields ─────────────────────────────────────────────────────
    var draggingGroupLabel by mutableStateOf<String?>(null)
        private set

    var draggingGroupCount by mutableStateOf(0)
        private set

    val isGroupDrag: Boolean get() = draggingGroupLabel != null

    // Hovered folder
    var hoveredFolderId by mutableStateOf<String?>(null)
        private set

    private val folderBounds = mutableStateMapOf<String, Pair<Float, Float>>()

    fun updateFolderBounds(folderId: String, top: Float, bottom: Float) {
        folderBounds[folderId] = Pair(top, bottom)
    }

    /**
     * Call this whenever the live folder list changes.
     * Removes stale entries for folder IDs that no longer exist, so
     * recalculateHover() can never return a ghost folder ID.
     */
    fun syncFolderIds(validIds: Set<String>) {
        val stale = folderBounds.keys.filter { it !in validIds }
        stale.forEach { folderBounds.remove(it) }
    }

    var isOverDeleteZone by mutableStateOf(false)
        private set

    var deleteZoneLeft   by mutableStateOf(0f)
    var deleteZoneTop    by mutableStateOf(Float.MAX_VALUE)
    var deleteZoneRight  by mutableStateOf(Float.MAX_VALUE)
    var deleteZoneBottom by mutableStateOf(Float.MAX_VALUE)

    var sidebarRightEdge by mutableStateOf(Float.MAX_VALUE)

    // ── Single document drag ──────────────────────────────────────────────────

    fun onDragStart(
        documentId: String,
        windowStartX: Float,
        windowStartY: Float,
        thumbnailPath: String?,
        documentName: String,
        pageCount: Int
    ) {
        isDragging            = true
        draggingDocumentId    = documentId
        startX                = windowStartX
        startY                = windowStartY
        dragOffsetX           = 0f
        dragOffsetY           = 0f
        hoveredFolderId       = null
        isOverDeleteZone      = false
        draggingThumbnailPath = thumbnailPath
        draggingDocumentName  = documentName
        draggingPageCount     = pageCount
        draggingGroupLabel    = null
        draggingGroupCount    = 0
    }

    // ── Group drag ────────────────────────────────────────────────────────────

    fun onGroupDragStart(
        groupLabel: String,
        groupCount: Int,
        windowStartX: Float,
        windowStartY: Float,
        thumbnailPath: String?
    ) {
        isDragging            = true
        draggingDocumentId    = null
        startX                = windowStartX
        startY                = windowStartY
        dragOffsetX           = 0f
        dragOffsetY           = 0f
        hoveredFolderId       = null
        isOverDeleteZone      = false
        draggingThumbnailPath = thumbnailPath
        draggingDocumentName  = "$groupLabel ($groupCount)"
        draggingPageCount     = groupCount
        draggingGroupLabel    = groupLabel
        draggingGroupCount    = groupCount
    }

    fun onDrag(dx: Float, dy: Float) {
        dragOffsetX += dx
        dragOffsetY += dy
        recalculateHover()
    }

    fun onDragEnd(): String? {
        val target = if (isOverDeleteZone) null else hoveredFolderId
        reset()
        return target
    }

    fun onDragCancel() = reset()

    fun updateHoveredFolder(folderId: String?) {
        if (isDragging) hoveredFolderId = folderId
    }

    private fun recalculateHover() {
        val currentX = startX + dragOffsetX
        val currentY = startY + dragOffsetY

        isOverDeleteZone = deleteZoneTop < Float.MAX_VALUE &&
                currentX in deleteZoneLeft..deleteZoneRight &&
                currentY in deleteZoneTop..deleteZoneBottom

        if (isOverDeleteZone) { hoveredFolderId = null; return }
        if (sidebarRightEdge < Float.MAX_VALUE && currentX > sidebarRightEdge) {
            hoveredFolderId = null; return
        }

        // Find the folder whose bounds tightly contain currentY.
        // Use minByOrNull on vertical distance from centre — this is stable
        // across recompositions and doesn't depend on map iteration order.
        hoveredFolderId = folderBounds.entries
            .filter { (_, b) -> currentY in b.first..b.second }
            .minByOrNull { (_, b) ->
                val centre = (b.first + b.second) / 2f
                kotlin.math.abs(currentY - centre)
            }
            ?.key
    }

    private fun reset() {
        isDragging            = false
        draggingDocumentId    = null
        dragOffsetX           = 0f
        dragOffsetY           = 0f
        hoveredFolderId       = null
        isOverDeleteZone      = false
        draggingThumbnailPath = null
        draggingDocumentName  = ""
        draggingPageCount     = 0
        draggingGroupLabel    = null
        draggingGroupCount    = 0
    }
}