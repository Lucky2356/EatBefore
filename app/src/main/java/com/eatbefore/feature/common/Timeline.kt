package com.eatbefore.feature.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
 *
 * [onHeadingClick] makes the headings themselves the way to the rest of a bucket. Left out
 * on a screen that is already showing all of it.
 */
fun LazyListScope.timeline(
    groups: List<TimelineGroup>,
    itemSpacing: Dp,
    onHeadingClick: ((TimeBucket) -> Unit)? = null,
    row: @Composable (InventoryRowUi, TimeBucket, Modifier) -> Unit,
) {
    val lastBucket = groups.lastOrNull()?.bucket

    groups.forEachIndexed { groupIndex, group ->
        val isFirstGroup = groupIndex == 0

        item(key = "timeline-heading-${group.bucket.name}", contentType = "timelineHeading") {
            TimelineHeading(
                bucket = group.bucket,
                count = group.rows.size,
                onClick = onHeadingClick?.let { click -> { click(group.bucket) } },
                modifier = animatedItem()
                    .fillMaxWidth()
                    .timelineRail(
                        node = group.bucket.markerColor(),
                        // The very first line starts at its own node, not at the top edge:
                        // an axis beginning above its first mark reads as cropped.
                        startAtNode = isFirstGroup,
                        bottomExtra = itemSpacing,
                    ),
            )
        }

        itemsIndexed(
            items = group.rows,
            key = { _, item -> item.batchId },
            contentType = { _, _ -> "timelineRow" },
        ) { rowIndex, item ->
            val isLastRow = rowIndex == group.rows.lastIndex
            val isLastRowOverall = group.bucket == lastBucket && isLastRow
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

/**
 * One bucket's heading: the coloured word, and how many are in it.
 *
 * A full touch target tall even where nothing can be tapped. The separation between groups
 * used to come from padding above the heading, and padding is outside the rail's own box —
 * so the line broke for seven pixels before every group but the first. Height belongs to
 * the rail; padding did not.
 */
@Composable
private fun TimelineHeading(
    bucket: TimeBucket,
    count: Int,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = Dimens.minTouchTarget),
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
 *
 * The node is centred on the item rather than placed at a fixed offset, so it stays level
 * with its heading when the system font scale makes that heading taller.
 */
@Composable
private fun Modifier.timelineRail(
    node: Color? = null,
    startAtNode: Boolean = false,
    bottomFraction: Float = 1f,
    bottomExtra: Dp = 0.dp,
): Modifier {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    return drawBehind {
        val x = RAIL_LINE_X.toPx()
        val centerY = size.height / 2
        drawLine(
            color = lineColor,
            start = Offset(x, if (startAtNode) centerY else 0f),
            end = Offset(x, size.height * bottomFraction + bottomExtra.toPx()),
            strokeWidth = Dimens.hairline.toPx(),
        )
        if (node != null) {
            drawCircle(color = node, radius = NODE_DIAMETER.toPx() / 2, center = Offset(x, centerY))
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

private const val HALF = 0.5f
