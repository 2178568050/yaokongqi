package com.yaokongqi.remote.model

data class GridPlacement(
    val button: RemoteButton,
    val row: Int,
    val col: Int,
)

data class GridSpanOption(
    val label: String,
    val colSpan: Int,
    val rowSpan: Int,
)

object GridLayoutHelper {
    fun normalizeButton(button: RemoteButton, columns: Int, rows: Int): RemoteButton {
        val colSpan = button.colSpan.coerceIn(1, columns)
        val rowSpan = button.rowSpan.coerceIn(1, rows)
        val gridCol = if (button.gridCol in 0 until columns) button.gridCol else -1
        val gridRow = if (button.gridRow in 0 until rows) button.gridRow else -1
        return button.copy(
            colSpan = colSpan.coerceAtMost(columns - gridCol.coerceAtLeast(0)),
            rowSpan = rowSpan.coerceAtMost(rows - gridRow.coerceAtLeast(0)),
            gridCol = gridCol,
            gridRow = gridRow,
        )
    }

    fun spanOptions(columns: Int, rows: Int): List<GridSpanOption> = buildList {
        for (rowSpan in 1..rows) {
            for (colSpan in 1..columns) {
                if (colSpan == 1 && rowSpan == 1) continue
                add(GridSpanOption("${colSpan}×${rowSpan}", colSpan, rowSpan))
            }
        }
        sortWith(compareBy({ it.colSpan * it.rowSpan }, { it.colSpan }, { it.rowSpan }))
        add(0, GridSpanOption("1×1", 1, 1))
    }

    fun computePlacements(
        buttons: List<RemoteButton>,
        columns: Int,
        rows: Int,
    ): List<GridPlacement> {
        if (columns <= 0 || rows <= 0) return emptyList()
        val occupied = Array(rows) { BooleanArray(columns) }
        val result = mutableListOf<GridPlacement>()

        for (raw in buttons) {
            val button = normalizeButton(raw, columns, rows)
            val colSpan = button.colSpan
            val rowSpan = button.rowSpan

            val fixed = if (button.gridRow >= 0 && button.gridCol >= 0) {
                val row = button.gridRow.coerceIn(0, rows - rowSpan)
                val col = button.gridCol.coerceIn(0, columns - colSpan)
                if (canPlace(occupied, row, col, colSpan, rowSpan)) row to col else null
            } else {
                null
            }

            val (row, col) = fixed ?: findFirstFit(occupied, columns, rows, colSpan, rowSpan) ?: continue
            markOccupied(occupied, row, col, colSpan, rowSpan)
            result.add(GridPlacement(button, row, col))
        }
        return result
    }

    fun canAddButton(
        buttons: List<RemoteButton>,
        newButton: RemoteButton,
        columns: Int,
        rows: Int,
    ): Boolean {
        val prepared = prepareForGrid(newButton, columns, rows)
        if (prepared.gridRow >= 0 && prepared.gridCol >= 0) {
            return canPlaceButtonAt(buttons, prepared, prepared.gridRow, prepared.gridCol, columns, rows)
        }
        val placements = computePlacements(buttons, columns, rows)
        val occupied = Array(rows) { BooleanArray(columns) }
        placements.forEach { markOccupied(occupied, it.row, it.col, it.button.colSpan, it.button.rowSpan) }
        return findFirstFit(occupied, columns, rows, prepared.colSpan, prepared.rowSpan) != null
    }

    fun canPlaceButtonAt(
        buttons: List<RemoteButton>,
        button: RemoteButton,
        targetRow: Int,
        targetCol: Int,
        columns: Int,
        rows: Int,
    ): Boolean {
        if (columns <= 0 || rows <= 0) return false
        val normalized = normalizeButton(
            button.copy(gridRow = targetRow, gridCol = targetCol),
            columns,
            rows,
        )
        val (row, col) = clampTopLeft(targetRow, targetCol, normalized.colSpan, normalized.rowSpan, columns, rows)
        val occupied = occupiedGridExcluding(buttons, columns, rows, excludeId = button.id)
        return canPlace(occupied, row, col, normalized.colSpan, normalized.rowSpan)
    }

    fun clampTopLeft(
        row: Int,
        col: Int,
        colSpan: Int,
        rowSpan: Int,
        columns: Int,
        rows: Int,
    ): Pair<Int, Int> {
        val safeColSpan = colSpan.coerceIn(1, columns)
        val safeRowSpan = rowSpan.coerceIn(1, rows)
        return row.coerceIn(0, (rows - safeRowSpan).coerceAtLeast(0)) to
            col.coerceIn(0, (columns - safeColSpan).coerceAtLeast(0))
    }

    fun occupiedGridExcluding(
        buttons: List<RemoteButton>,
        columns: Int,
        rows: Int,
        excludeId: String? = null,
    ): Array<BooleanArray> {
        val occupied = Array(rows) { BooleanArray(columns) }
        val filtered = if (excludeId == null) buttons else buttons.filter { it.id != excludeId }
        computePlacements(filtered, columns, rows).forEach {
            markOccupied(occupied, it.row, it.col, it.button.colSpan, it.button.rowSpan)
        }
        return occupied
    }

    fun occupiedCells(buttons: List<RemoteButton>, columns: Int, rows: Int): Int =
        computePlacements(buttons, columns, rows).sumOf { it.button.colSpan * it.button.rowSpan }

    fun maxCells(columns: Int, rows: Int): Int = columns * rows

    fun prepareForGrid(button: RemoteButton, columns: Int, rows: Int): RemoteButton {
        val normalized = normalizeButton(button, columns, rows)
        if (normalized.gridRow < 0 || normalized.gridCol < 0) return normalized
        val (row, col) = clampTopLeft(
            normalized.gridRow,
            normalized.gridCol,
            normalized.colSpan,
            normalized.rowSpan,
            columns,
            rows,
        )
        return normalized.copy(gridRow = row, gridCol = col)
    }

    fun isCellOccupied(
        buttons: List<RemoteButton>,
        row: Int,
        col: Int,
        columns: Int,
        rows: Int,
    ): Boolean {
        if (row !in 0 until rows || col !in 0 until columns) return true
        return computePlacements(buttons, columns, rows).any { placement ->
            row in placement.row until placement.row + placement.button.rowSpan &&
                col in placement.col until placement.col + placement.button.colSpan
        }
    }

    private fun canPlace(
        occupied: Array<BooleanArray>,
        row: Int,
        col: Int,
        colSpan: Int,
        rowSpan: Int,
    ): Boolean {
        if (row + rowSpan > occupied.size || col + colSpan > occupied[0].size) return false
        for (r in row until row + rowSpan) {
            for (c in col until col + colSpan) {
                if (occupied[r][c]) return false
            }
        }
        return true
    }

    private fun findFirstFit(
        occupied: Array<BooleanArray>,
        columns: Int,
        rows: Int,
        colSpan: Int,
        rowSpan: Int,
    ): Pair<Int, Int>? {
        for (row in 0..rows - rowSpan) {
            for (col in 0..columns - colSpan) {
                if (canPlace(occupied, row, col, colSpan, rowSpan)) return row to col
            }
        }
        return null
    }

    private fun markOccupied(
        occupied: Array<BooleanArray>,
        row: Int,
        col: Int,
        colSpan: Int,
        rowSpan: Int,
    ) {
        for (r in row until row + rowSpan) {
            for (c in col until col + colSpan) {
                occupied[r][c] = true
            }
        }
    }
}
