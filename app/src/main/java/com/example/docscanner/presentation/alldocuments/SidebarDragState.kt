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

    /** Absolute window-space X where the drag began */
    var startX by mutableStateOf(0f)
        private set

    /** Absolute window-space Y where the drag began */
    var startY by mutableStateOf(0f)
        private set

    // ── Info needed by the root-level ghost card ──────────────────────────────
    var draggingThumbnailPath by mutableStateOf<String?>(null)
        private set

    var draggingDocumentName by mutableStateOf("")
        private set

    var draggingPageCount by mutableStateOf(0)
        private set

    // ── Hovered folder ────────────────────────────────────────────────────────
    var hoveredFolderId by mutableStateOf<String?>(null)
        private set

    private val folderBounds = mutableStateMapOf<String, Pair<Float, Float>>()

    fun updateFolderBounds(folderId: String, top: Float, bottom: Float) {
        folderBounds[folderId] = Pair(top, bottom)
    }

    // ── Source screens call these ─────────────────────────────────────────────

    fun onDragStart(
        documentId   : String,
        windowStartX : Float,
        windowStartY : Float,
        thumbnailPath: String?,
        documentName : String,
        pageCount    : Int
    ) {
        isDragging            = true
        draggingDocumentId    = documentId
        startX                = windowStartX
        startY                = windowStartY
        dragOffsetX           = 0f
        dragOffsetY           = 0f
        hoveredFolderId       = null
        draggingThumbnailPath = thumbnailPath
        draggingDocumentName  = documentName
        draggingPageCount     = pageCount
    }

    fun onDrag(dx: Float, dy: Float) {
        dragOffsetX += dx
        dragOffsetY += dy
        recalculateHover()
    }

    fun onDragEnd(): String? {
        val target = hoveredFolderId
        reset()
        return target
    }

    fun onDragCancel() = reset()

    fun updateHoveredFolder(folderId: String?) {
        if (isDragging) hoveredFolderId = folderId
    }

    /**
     * Right edge of the sidebar in window-space pixels.
     * Populated by the sidebar's onGloballyPositioned.
     * Defaults to Float.MAX_VALUE so hover works before first measurement.
     */
    var sidebarRightEdge by mutableStateOf(Float.MAX_VALUE)

    private fun recalculateHover() {
        val currentX = startX + dragOffsetX
        val currentY = startY + dragOffsetY

        // Only gate on X once the sidebar has been measured
        if (sidebarRightEdge < Float.MAX_VALUE && currentX > sidebarRightEdge) {
            hoveredFolderId = null
            return
        }

        hoveredFolderId = folderBounds.entries
            .firstOrNull { (_, b) -> currentY in b.first..b.second }
            ?.key
    }

    private fun reset() {
        isDragging            = false
        draggingDocumentId    = null
        dragOffsetX           = 0f
        dragOffsetY           = 0f
        hoveredFolderId       = null
        draggingThumbnailPath = null
        draggingDocumentName  = ""
        draggingPageCount     = 0
    }
}