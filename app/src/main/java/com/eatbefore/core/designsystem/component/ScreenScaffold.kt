package com.eatbefore.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.eatbefore.R

/**
 * The standard frame for a screen: one title bar, an optional back button, an optional
 * snackbar host, and the content below.
 *
 * Screens previously each built their own Scaffold and TopAppBar, which is how the back
 * button, bar colours and content padding drifted apart. Anything genuinely unusual can
 * still use Scaffold directly — this covers the common case, it is not a straitjacket.
 *
 * The bar gets out of the way when the content scrolls. It is fixed height and always
 * present otherwise, and on a phone that is a permanent tax on every screen — a quarter of
 * the home screen was spent on a title before the first product appeared. `enterAlways`
 * rather than a large collapsing header: the title here is one short word, so there is
 * nothing to collapse *through*, and bringing it straight back on the first upward scroll
 * means the actions in it are never more than a flick away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    // Created here rather than taken as a parameter. `TopAppBarScrollBehavior` is
    // experimental, and naming it in this signature would push the opt-in onto all eleven
    // screens that only ever want the ordinary frame. Screens needing to drive the bar
    // themselves build their own Scaffold around [AppTopBar].
    val behavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(behavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = title,
                onBack = onBack,
                actions = actions,
                scrollBehavior = behavior,
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        snackbarHost = {
            if (snackbarHostState != null) AnnouncedSnackbarHost(snackbarHostState)
        },
        content = content,
    )
}

/**
 * The title bar itself, extracted so the three screens that need a Scaffold of their own
 * (home, stock, one product) get the same bar rather than three near-copies of it.
 *
 * [title] can be given as arbitrary content for the one case that needs it — the home
 * screen puts the date under the word.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    navigationIcon: @Composable () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    titleContent: @Composable () -> Unit = { Text(title) },
) {
    TopAppBar(
        modifier = modifier,
        title = titleContent,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            } else {
                navigationIcon()
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            // Once content has slid underneath, the bar needs an edge of its own; without
            // it a scrolled list appears to be printed on the bar.
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        scrollBehavior = scrollBehavior,
    )
}
