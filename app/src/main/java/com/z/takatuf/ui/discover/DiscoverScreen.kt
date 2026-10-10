package com.z.takatuf.ui.discover

import com.z.takatuf.ui.common.ChoiceChip
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.z.takatuf.R
import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.ui.appViewModel
import com.z.takatuf.ui.common.BrandMark
import com.z.takatuf.ui.common.EmptyState
import com.z.takatuf.ui.common.ErrorState
import com.z.takatuf.ui.common.ListingRow
import com.z.takatuf.ui.common.LoadingRows
import java.time.Instant

@Composable
fun DiscoverScreen(
    onOpenListing: (String) -> Unit,
    onPost: (ListingKind) -> Unit,
) {
    val viewModel = appViewModel { c, _ -> DiscoverViewModel(c.session, c.listings, c.saved, c.applications) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    DiscoverContent(
        state = state,
        onQuery = viewModel::onQuery,
        onKind = viewModel::onKind,
        onSavedOnly = viewModel::onSavedOnly,
        onFilters = viewModel::onFilters,
        onSort = viewModel::onSort,
        onClearAll = viewModel::clearAll,
        onRetry = viewModel::retry,
        onToggleSave = viewModel::toggleSave,
        onOpenListing = onOpenListing,
        onPost = onPost,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiscoverContent(
    state: DiscoverUiState,
    onQuery: (String) -> Unit,
    onKind: (KindFilter) -> Unit,
    onSavedOnly: (Boolean) -> Unit,
    onFilters: (DiscoverFilters) -> Unit,
    onSort: (SortOrder) -> Unit,
    onClearAll: () -> Unit,
    onRetry: () -> Unit,
    onToggleSave: (String, Boolean) -> Unit,
    onOpenListing: (String) -> Unit,
    onPost: (ListingKind) -> Unit,
) {
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showPost by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val expandedFab by remember { derivedStateOf { listState.firstVisibleItemIndex < 3 } }
    val now = remember(state.items) { Instant.now() }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.action_post)) },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                expanded = expandedFab,
                onClick = { showPost = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "brand") { BrandHeader() }
            stickyHeader(key = "controls") {
                SearchAndFilters(
                    state = state,
                    onQuery = onQuery,
                    onKind = onKind,
                    onSavedOnly = onSavedOnly,
                    onOpenFilters = { showFilters = true },
                )
            }
            item(key = "results") { ResultsLine(state, onSort) }
            when {
                state.loading -> item(key = "loading") { LoadingRows() }
                state.error != null -> item(key = "error") { ErrorState(state.error, onRetry) }
                state.items.isEmpty() -> item(key = "empty") {
                    DiscoverEmpty(state, onPost = { showPost = true }, onShowAll = { onSavedOnly(false) }, onClearAll = onClearAll)
                }
                else -> itemsIndexed(state.items, key = { _, it -> it.id }) { index, listing ->
                    Column(Modifier.animateItem()) {
                        ListingRow(
                            listing = listing,
                            onClick = { onOpenListing(listing.id) },
                            now = now,
                            saved = listing.id in state.savedIds,
                            onToggleSave = { onToggleSave(listing.id, it) },
                            mySkills = state.mySkills,
                            myApplication = state.myApplications[listing.id],
                            isOwn = listing.ownerId == state.myUid,
                        )
                        if (index < state.items.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 84.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            state = state,
            onFilters = onFilters,
            onDismiss = { showFilters = false },
        )
    }
    if (showPost) {
        PostSheet(
            onPick = { kind -> showPost = false; onPost(kind) },
            onDismiss = { showPost = false },
        )
    }
}

@Composable
private fun BrandHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun SearchAndFilters(
    state: DiscoverUiState,
    onQuery: (String) -> Unit,
    onKind: (KindFilter) -> Unit,
    onSavedOnly: (Boolean) -> Unit,
    onOpenFilters: () -> Unit,
) {
    val focus = LocalFocusManager.current
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
            TextField(
                value = state.query,
                onValueChange = onQuery,
                placeholder = { Text(stringResource(R.string.discover_search_placeholder)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_clear_search))
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(min = 52.dp),
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(KindFilter.entries, key = { it.name }) { kind ->
                    ChoiceChip(
                        selected = state.kind == kind,
                        onClick = { onKind(kind) },
                        label = { Text(kindFilterLabel(kind)) },
                        shape = CircleShape,
                    )
                }
                item { VerticalDivider(Modifier.height(24.dp), color = MaterialTheme.colorScheme.outlineVariant) }
                item {
                    ChoiceChip(
                        selected = state.savedOnly,
                        onClick = { onSavedOnly(!state.savedOnly) },
                        label = { Text(stringResource(R.string.discover_saved)) },
                        leadingIcon = {
                            Icon(
                                if (state.savedOnly) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        },
                        shape = CircleShape,
                    )
                }
                item {
                    val active = state.filters.activeCount
                    ChoiceChip(
                        selected = active > 0,
                        onClick = onOpenFilters,
                        label = {
                            Text(
                                if (active > 0) stringResource(R.string.discover_filters_count, active)
                                else stringResource(R.string.discover_filters),
                            )
                        },
                        leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                        shape = CircleShape,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
fun kindFilterLabel(kind: KindFilter): String = stringResource(
    when (kind) {
        KindFilter.All -> R.string.filter_all
        KindFilter.Projects -> R.string.filter_projects
        KindFilter.Teams -> R.string.filter_teams
        KindFilter.Events -> R.string.filter_events
    },
)

@Composable
private fun ResultsLine(state: DiscoverUiState, onSort: (SortOrder) -> Unit) {
    if (state.loading || state.error != null || state.items.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (state.savedOnly) pluralStringResource(R.plurals.saved_count, state.items.size, state.items.size)
            else pluralStringResource(R.plurals.results_count, state.items.size, state.items.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box {
            TextButton(onClick = { open = true }) {
                Text(stringResource(R.string.sort_label, sortLabel(state.sort)))
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                SortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { Text(sortLabel(order)) },
                        onClick = { onSort(order); open = false },
                    )
                }
            }
        }
    }
}

@Composable
fun sortLabel(order: SortOrder): String = stringResource(
    when (order) {
        SortOrder.BestMatch -> R.string.sort_best_match
        SortOrder.Newest -> R.string.sort_newest
        SortOrder.ClosingSoon -> R.string.sort_closing_soon
    },
)

@Composable
private fun DiscoverEmpty(state: DiscoverUiState, onPost: () -> Unit, onShowAll: () -> Unit, onClearAll: () -> Unit) {
    when {
        state.feedEmpty && !state.savedOnly -> EmptyState(
            title = stringResource(R.string.discover_empty_feed_title),
            body = stringResource(R.string.discover_empty_feed_body),
            actionLabel = stringResource(R.string.action_post_something),
            onAction = onPost,
        )
        state.savedOnly && state.savedIds.isEmpty() -> EmptyState(
            title = stringResource(R.string.discover_empty_saved_title),
            body = stringResource(R.string.discover_empty_saved_body),
            actionLabel = stringResource(R.string.discover_show_all),
            onAction = onShowAll,
        )
        else -> EmptyState(
            title = if (state.query.isNotBlank()) stringResource(R.string.discover_no_match_query, state.query.trim())
            else stringResource(R.string.discover_no_match_title),
            body = stringResource(R.string.discover_no_match_body),
            actionLabel = stringResource(R.string.action_clear_filters),
            onAction = onClearAll,
        )
    }
}
