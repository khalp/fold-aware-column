package com.example.foldawarecolumn.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.window.layout.DisplayFeature
import androidx.window.layout.FoldingFeature

// TODO: some design questions
//  1. what should happen with the children of the layout if there's not enough space available?
//  2. (may need designer input) how will we handle swipeable/draggable/animated input?
//  3. current implementation is for horizontal folds due to original sheet application - do we want to expand to
//  vertical folds?
//  4. how do we handle blank spaces? arrange to fill space or leave as is?

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FoldAwareColumn(
    displayFeatures: List<DisplayFeature>,
    modifier: Modifier = Modifier,
    foldPadding: PaddingValues = PaddingValues(),
    content: @Composable () -> Unit,
    // TODO: would be useful if we could add "fold padding" (parameter, arrangement, or modifier attribute?) and
    //  normal column capabilities, like an AdaptiveColumnScope that can be used to express weight
) {
    // Extract folding feature if horizontal
    val fold = displayFeatures.find {
        it is FoldingFeature && it.orientation == FoldingFeature.Orientation.HORIZONTAL
    } as FoldingFeature?

    // Calculate fold bounds in pixels (including any added fold padding)
    val foldBoundsPx = with(LocalDensity.current) {
        val topPaddingPx = foldPadding.calculateTopPadding().roundToPx()
        val bottomPaddingPx = foldPadding.calculateBottomPadding().roundToPx()

        fold?.bounds?.toComposeRect()?.let {
            Rect(
                top = it.top - topPaddingPx,
                left = it.left,
                right = it.right,
                bottom = it.bottom + bottomPaddingPx
            )
        }
    }

    // Extract other folding feature properties
    val foldHeightPx = foldBoundsPx?.height ?: 0
    val foldIsSeparating = fold?.isSeparating

    Layout(
        modifier = modifier.wrapContentSize(),
        content = { content() }
    ) { measurables, constraints ->
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints)
        }

        // Calculate max placeable width and height
        val maxPlaceableWidth =
            placeables.fold(0) { acc, placeable -> if (placeable.width > acc) placeable.width else acc }
        val totalPlaceableHeight = placeables.fold(0) { acc, placeable -> acc + placeable.height }

        // TODO: accounts for placeable height and fold height, but what about when there's extra empty space due
        //  to placeables being pushed below the fold? not sure how to check for this when coordinate access is
        //  only within the placement scope
        val layoutHeight = totalPlaceableHeight + foldHeightPx.toInt()

        layout(maxPlaceableWidth, layoutHeight) {
            val layoutBounds = coordinates!!.boundsInWindow()
            var placeableY = 0

            placeables.forEach { placeable ->
                val relativeBounds = Rect(
                    left = 0f,
                    top = placeableY.toFloat(),
                    right = placeable.width.toFloat(),
                    bottom = (placeableY + placeable.height).toFloat()
                )
                val absoluteBounds = relativeBounds.translate(layoutBounds.left, layoutBounds.top)

                // If fold is separating and placeable overlaps fold, push placeable below fold
                if (foldIsSeparating == true && foldBoundsPx?.let { absoluteBounds.overlaps(it) } == true) {
                    placeableY = (foldBoundsPx.bottom - layoutBounds.top).toInt()
                }

                placeable.placeRelative(x = 0, y = placeableY)
                placeableY += placeable.height
            }
        }
    }
}
