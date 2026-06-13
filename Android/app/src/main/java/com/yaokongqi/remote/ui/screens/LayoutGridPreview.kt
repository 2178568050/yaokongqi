package com.yaokongqi.remote.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.yaokongqi.remote.model.GridLayoutHelper
import com.yaokongqi.remote.model.GridPlacement
import com.yaokongqi.remote.model.RemoteButton
import com.yaokongqi.remote.ui.theme.Primary
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private data class DragSession(
    val button: RemoteButton,
    val anchorOriginPx: Offset,
    val touchOffsetInButtonPx: Offset,
)

@Composable
fun LayoutGridPreview(
    buttons: List<RemoteButton>,
    columns: Int,
    rows: Int,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    selectedButtonId: String? = null,
    onCellClick: ((row: Int, col: Int, occupiedButton: RemoteButton?) -> Unit)? = null,
    onButtonClick: ((RemoteButton) -> Unit)? = null,
    onButtonMove: ((buttonId: String, row: Int, col: Int) -> Unit)? = null,
    minHeight: Dp = 180.dp,
) {
    val placements = remember(buttons, columns, rows) {
        GridLayoutHelper.computePlacements(buttons, columns, rows)
    }
    val placementByCell = remember(placements) {
        buildMap<Pair<Int, Int>, GridPlacement> {
            placements.forEach { placement ->
                for (r in placement.row until placement.row + placement.button.rowSpan) {
                    for (c in placement.col until placement.col + placement.button.colSpan) {
                        put(r to c, placement)
                    }
                }
            }
        }
    }

    var dragSession by remember { mutableStateOf<DragSession?>(null) }
    var dragPointerInGrid by remember { mutableStateOf<Offset?>(null) }
    var dropTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var dropValid by remember { mutableStateOf(false) }

    val buttonsRef = rememberUpdatedState(buttons)
    val scheme = MaterialTheme.colorScheme

    Column(modifier = modifier) {
        Text(
            "布局预览 · ${columns}×${rows}",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .height(minHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(scheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(6.dp),
        ) {
            val gap = 4.dp
            val cellWidth = (maxWidth - gap * (columns - 1).coerceAtLeast(0)) / columns.coerceAtLeast(1)
            val cellHeight = (maxHeight - gap * (rows - 1).coerceAtLeast(0)) / rows.coerceAtLeast(1)
            val density = LocalDensity.current
            val gapPx = with(density) { gap.toPx() }
            val cellWidthPx = with(density) { cellWidth.toPx() }
            val cellHeightPx = with(density) { cellHeight.toPx() }

            fun cellTopLeft(row: Int, col: Int): Pair<Dp, Dp> {
                val x = (cellWidth + gap) * col
                val y = (cellHeight + gap) * row
                return x to y
            }

            fun cellFromOffset(offset: Offset): Pair<Int, Int> {
                val col = (offset.x / (cellWidthPx + gapPx)).toInt().coerceIn(0, columns - 1)
                val row = (offset.y / (cellHeightPx + gapPx)).toInt().coerceIn(0, rows - 1)
                return row to col
            }

            fun updateDropTarget(pointer: Offset, button: RemoteButton) {
                val (row, col) = cellFromOffset(pointer)
                val (targetRow, targetCol) = GridLayoutHelper.clampTopLeft(
                    row,
                    col,
                    button.colSpan,
                    button.rowSpan,
                    columns,
                    rows,
                )
                val nextTarget = targetRow to targetCol
                if (dropTarget != nextTarget) {
                    dropTarget = nextTarget
                    dropValid = GridLayoutHelper.canPlaceButtonAt(
                        buttonsRef.value,
                        button,
                        targetRow,
                        targetCol,
                        columns,
                        rows,
                    )
                }
            }

            fun clearDrag() {
                dragSession = null
                dragPointerInGrid = null
                dropTarget = null
                dropValid = false
            }

            val draggingId = dragSession?.button?.id

            if (editable) {
                for (row in 0 until rows) {
                    for (col in 0 until columns) {
                        val (x, y) = cellTopLeft(row, col)
                        val occupied = placementByCell[row to col]?.button?.takeUnless { it.id == draggingId }
                        Box(
                            modifier = Modifier
                                .offset(x = x, y = y)
                                .width(cellWidth)
                                .height(cellHeight)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, scheme.outline.copy(alpha = 0.45f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (occupied == null) {
                                Text(
                                    "+",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant.copy(alpha = 0.35f),
                                )
                            }
                        }
                    }
                }
            }

            if (dragSession != null && dropTarget != null) {
                val dragging = dragSession!!.button
                val (targetRow, targetCol) = dropTarget!!
                val (x, y) = cellTopLeft(targetRow, targetCol)
                val w = cellWidth * dragging.colSpan + gap * (dragging.colSpan - 1)
                val h = cellHeight * dragging.rowSpan + gap * (dragging.rowSpan - 1)
                Box(
                    modifier = Modifier
                        .zIndex(1.5f)
                        .offset(x = x, y = y)
                        .width(w)
                        .height(h)
                        .clip(RoundedCornerShape(6.dp))
                        .border(
                            width = 2.dp,
                            color = if (dropValid) Primary else scheme.error,
                            shape = RoundedCornerShape(6.dp),
                        )
                        .background(
                            if (dropValid) Primary.copy(alpha = 0.16f) else scheme.error.copy(alpha = 0.10f),
                        ),
                )
            }

            placements.forEach { placement ->
                val button = placement.button
                key(button.id) {
                    val (baseX, baseY) = cellTopLeft(placement.row, placement.col)
                    val baseXPx = with(density) { baseX.toPx() }
                    val baseYPx = with(density) { baseY.toPx() }
                    val w = cellWidth * button.colSpan + gap * (button.colSpan - 1)
                    val h = cellHeight * button.rowSpan + gap * (button.rowSpan - 1)
                    val wPx = with(density) { w.toPx() }
                    val hPx = with(density) { h.toPx() }
                    val selected = button.id == selectedButtonId
                    val isDragging = draggingId == button.id

                    GridPreviewButtonAnchor(
                        button = button,
                        selected = selected,
                        isDragging = isDragging,
                        width = w,
                        height = h,
                        editable = editable,
                        dragEnabled = editable && onButtonMove != null,
                        onClick = {
                            if (dragSession == null) onButtonClick?.invoke(button)
                        },
                        onDragStart = { localPos ->
                            dragSession = DragSession(
                                button = button,
                                anchorOriginPx = Offset(baseXPx, baseYPx),
                                touchOffsetInButtonPx = localPos,
                            )
                            val gridPos = Offset(baseXPx + localPos.x, baseYPx + localPos.y)
                            dragPointerInGrid = gridPos
                            updateDropTarget(gridPos, button)
                        },
                        onDrag = { localPos ->
                            val session = dragSession ?: return@GridPreviewButtonAnchor
                            if (session.button.id != button.id) return@GridPreviewButtonAnchor
                            val gridPos = Offset(
                                session.anchorOriginPx.x + localPos.x,
                                session.anchorOriginPx.y + localPos.y,
                            )
                            dragPointerInGrid = gridPos
                            updateDropTarget(gridPos, session.button)
                        },
                        onDragEnd = {
                            val target = dropTarget
                            if (target != null && dropValid) {
                                onButtonMove?.invoke(button.id, target.first, target.second)
                            }
                            clearDrag()
                        },
                        onDragCancel = { clearDrag() },
                        modifier = Modifier
                            .offset(x = baseX, y = baseY)
                            .width(w)
                            .height(h)
                            .zIndex(1f),
                    )
                }
            }

            if (editable && dragSession == null) {
                for (row in 0 until rows) {
                    for (col in 0 until columns) {
                        if (GridLayoutHelper.isCellOccupied(buttons, row, col, columns, rows)) continue
                        val (x, y) = cellTopLeft(row, col)
                        Box(
                            modifier = Modifier
                                .zIndex(2f)
                                .offset(x = x, y = y)
                                .width(cellWidth)
                                .height(cellHeight)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    onCellClick?.invoke(row, col, null)
                                },
                        )
                    }
                }
            }

            dragSession?.let { session ->
                val pointer = dragPointerInGrid ?: return@let
                val button = session.button
                val w = cellWidth * button.colSpan + gap * (button.colSpan - 1)
                val h = cellHeight * button.rowSpan + gap * (button.rowSpan - 1)
                val wPx = with(density) { w.toPx() }
                val hPx = with(density) { h.toPx() }
                val leftPx = (pointer.x - wPx / 2f).roundToInt()
                val topPx = (pointer.y - hPx / 2f).roundToInt()

                GridButtonFace(
                    button = button,
                    selected = true,
                    dragging = true,
                    showDragHint = false,
                    modifier = Modifier
                        .zIndex(3f)
                        .graphicsLayer {
                            translationX = leftPx.toFloat()
                            translationY = topPx.toFloat()
                        }
                        .width(w)
                        .height(h),
                )
            }
        }
    }
}

/** 固定在网格原位接收手势；拖动时仅隐藏视觉，不移动布局。 */
@Composable
private fun GridPreviewButtonAnchor(
    button: RemoteButton,
    selected: Boolean,
    isDragging: Boolean,
    width: Dp,
    height: Dp,
    editable: Boolean,
    dragEnabled: Boolean,
    onClick: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inputModifier = modifier
        .alpha(if (isDragging) 0f else 1f)
        .then(
            if (dragEnabled) {
                Modifier.pointerInput(button.id) {
                    coroutineScope {
                        launch {
                            detectTapGestures(onTap = { onClick() })
                        }
                        launch {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset -> onDragStart(offset) },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragCancel() },
                                onDrag = { change, _ ->
                                    change.consume()
                                    onDrag(change.position)
                                },
                            )
                        }
                    }
                }
            } else if (editable) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            },
        )

    GridButtonFace(
        button = button,
        selected = selected,
        dragging = false,
        showDragHint = editable && dragEnabled,
        modifier = inputModifier,
    )
}

@Composable
private fun GridButtonFace(
    button: RemoteButton,
    selected: Boolean,
    dragging: Boolean,
    showDragHint: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(6.dp)

    Surface(
        modifier = modifier,
        shape = shape,
        color = when {
            dragging -> Primary.copy(alpha = 0.94f)
            selected -> Primary.copy(alpha = 0.85f)
            else -> scheme.primaryContainer
        },
        border = BorderStroke(
            width = if (selected || dragging) 2.dp else 1.dp,
            color = when {
                dragging -> Primary
                selected -> Primary
                else -> scheme.outline
            },
        ),
        shadowElevation = if (dragging) 4.dp else 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(2.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = button.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (button.colSpan > 1 || button.rowSpan > 1) {
                    Text(
                        text = "${button.colSpan}×${button.rowSpan}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.65f),
                    )
                }
                if (showDragHint) {
                    Text(
                        text = "长按拖动",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.45f),
                    )
                }
            }
        }
    }
}
