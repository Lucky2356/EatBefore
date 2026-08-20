package com.eatbefore.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eatbefore.core.designsystem.component.animatedItem
import com.eatbefore.core.designsystem.theme.Dimens

/**
 * The time axis: a hairline rail down the left with a coloured node at each heading, and
 * the batches of that bucket hanging off it.
 *
 * Emitted as separate list items rather than one block so every row keeps its own key and
 * its own [animatedItem] — the rail is drawn behind each item and bridged across the gaps
 * the list's arrangement leaves between them, which is what lets a continuous line coexist
 * with an animated lazy list.
 *
 * [itemSpacing] must be the spacing the caller's `verticalArrangement` uses, or the line
 * will fall short of the next item.
 *
 * The caller draws its own [row] because the two screens using this disagree about what a
 * tap does — one opens the batch, the other ticks it — and pushing that decision in here
 * would mean teaching the axis about selection mode.
 */
fun LazyListScope.timeline(
    groups: List<TimelineGroup>,
    itemSpacing: Dp,
    row: @Composable (InventoryRowUi, TimeBucket, Modifier) -> Unit,
) {
    val lastBucket = groups.lastOrNull()?.bucket

    groups.forEachIndexed { groupIndex, group ->
        val isFirstGroup = groupIndex == 0

        item(key = "timeline-heading-${group.bucket.name}", contentType = "timelineHeading") {
            TimelineHeading(
                bucket = group.bucket,
                count = group.rows.size,
                modifier = animatedItem()
                    .fillMaxWidth()
                    // Groups need more air between them than rows do inside one, and the
                    // gap has to belong to an item so the rail can be drawn through it.
                    .padding(top = if (isFirstGroup) 0.dp else Dimens.spaceSm)
                    .timelineRail(
                        node = group.bucket.markerColor(),
                        // The very first line starts at its own node, not at the top edge:
                        // an axis beginning above its first mark reads as cropped.
                        topInset = if (isFirstGroup) NODE_CENTER_Y else 0.dp,
                        bottomExtra = itemSpacing,
                    ),
            )
        }

        itemsIndexed(
            items = group.rows,
            key = { _, item -> item.batchId },
            contentType = { _, _ -> "timelineRow" },
        ) { rowIndex, item ->
            val isLastRowOverall = group.bucket == lastBucket && rowIndex == group.rows.lastIndex
            row(
                item,
                group.bucket,
                animatedItem()
                    .timelineRail(
                        // The axis stops halfway down its last row rather than running off
                        // the bottom of the list, which would promise more below.
                        bottomFraction = if (isLastRowOverall) HALF else 1f,
                        bottomExtra = if (isLastRowOverall) 0.dp else itemSpacing,
                    ),
            )
        }
    }
}

/** One bucket's heading: the coloured word, and how many are in it. */
@Composable
private fun TimelineHeading(bucket: TimeBucket, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(bucket.labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = bucket.markerColor(),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Draws the rail behind one list item: the hairline, and the node when the item is a
 * heading. Content is inset by [RAIL_WIDTH] so nothing sits on top of the line.
 *
 * [bottomExtra] continues the line past this item's own bottom edge to bridge the gap
 * before the next one; lazy items are not clipped against each other, so the halves meet.
 */
@Composable
private fun Modifier.timelineRail(
    node: Color? = null,
    topInset: Dp = 0.dp,
    bottomFraction: Float = 1f,
    bottomExtra: Dp = 0.dp,
): Modifier {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    return drawBehind {
        val x = RAIL_LINE_X.toPx()
        drawLine(
            color = lineColor,
            start = Offset(x, topInset.toPx()),
            end = Offset(x, size.height * bottomFraction + bottomExtra.toPx()),
            strokeWidth = Dimens.hairline.toPx(),
        )
        if (node != null) {
            drawCircle(
                color = node,
                radius = NODE_DIAMETER.toPx() / 2,
                center = Offset(x, NODE_CENTER_Y.toPx()),
            )
        }
    }.padding(start = RAIL_WIDTH)
}

/**
 * Space reserved to the left of every timeline item for the rail. Kept as tight as the
 * node allows — it comes straight out of the width the rows have for their own text.
 */
private val RAIL_WIDTH = 22.dp

/** Where the line sits inside that space, and so where every node is centred. */
private val RAIL_LINE_X = 6.dp

private val NODE_DIAMETER = 12.dp

/** Node centre, level with the middle of a `labelLarge` line. */
private val NODE_CENTER_Y = 10.dp

private const val HALF = 0.5f
