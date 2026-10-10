package com.z.takatuf.ui.navigation

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.z.takatuf.R
import com.z.takatuf.TakatufApplication
import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.ui.activity.ActivityScreen
import com.z.takatuf.ui.activity.newApplicantCount
import com.z.takatuf.ui.applicants.ApplicantDetailScreen
import com.z.takatuf.ui.applicants.ApplicantsScreen
import com.z.takatuf.ui.discover.DiscoverScreen
import com.z.takatuf.ui.editor.EditorScreen
import com.z.takatuf.ui.listing.ListingDetailScreen
import com.z.takatuf.ui.passport.PassportScreen
import com.z.takatuf.ui.passport.PersonScreen
import com.z.takatuf.ui.theme.TakatufTheme
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

private enum class TopLevel(
    val route: Any,
    val routeClass: KClass<*>,
    val label: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    Discover(DiscoverRoute, DiscoverRoute::class, R.string.tab_discover, Icons.Rounded.Explore, Icons.Outlined.Explore),
    Activity(ActivityRoute, ActivityRoute::class, R.string.tab_activity, Icons.Rounded.Inbox, Icons.Outlined.Inbox),
    Passport(PassportRoute, PassportRoute::class, R.string.tab_passport, Icons.Rounded.Badge, Icons.Outlined.Badge),
}

@Composable
fun TakatufNavHost(newIntents: Flow<Intent>) {
    val container = (LocalContext.current.applicationContext as TakatufApplication).container
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val showBar = destination == null || TopLevel.entries.any { destination.hasRoute(it.routeClass) }
    val uid = container.session.uid.orEmpty()
    val newApplicantsFlow = remember(uid) { container.applications.newApplicantCount(uid) }
    val newApplicants by newApplicantsFlow.collectAsStateWithLifecycle(0)

    LaunchedEffect(navController) {
        newIntents.collect { navController.handleDeepLink(it) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(visible = showBar, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        TopLevel.entries.forEach { tab ->
                            val selected = destination?.hasRoute(tab.routeClass) == true
                            val badge = if (tab == TopLevel.Activity) newApplicants else 0
                            val badgeText = if (badge > 0) pluralStringResource(R.plurals.new_applicants_badge_a11y, badge, badge) else null
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navController.navigateToTab(tab.route) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (badge > 0) {
                                                Badge(
                                                    containerColor = TakatufTheme.colors.attention,
                                                    contentColor = TakatufTheme.colors.onAttention,
                                                    modifier = Modifier.semantics { contentDescription = badgeText.orEmpty() },
                                                ) { Text(badge.toString()) }
                                            }
                                        },
                                    ) {
                                        Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null)
                                    }
                                },
                                label = { Text(stringResource(tab.label)) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = DiscoverRoute,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable<DiscoverRoute> {
                DiscoverScreen(
                    onOpenListing = { navController.navigate(ListingRoute(it)) },
                    onPost = { kind -> navController.navigate(EditorRoute(kind.key)) },
                )
            }
            composable<ActivityRoute> {
                ActivityScreen(
                    onOpenListing = { navController.navigate(ListingRoute(it)) },
                    onOpenApplicants = { navController.navigate(ApplicantsRoute(it)) },
                    onDiscover = { navController.navigateToTab(DiscoverRoute) },
                    onPost = { kind -> navController.navigate(EditorRoute(kind.key)) },
                )
            }
            composable<PassportRoute> { PassportScreen() }
            composable<ListingRoute>(
                deepLinks = listOf(navDeepLink<ListingRoute>(basePath = "${DeepLinkScheme}listing")),
            ) {
                ListingDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenListing = { navController.navigate(ListingRoute(it)) },
                    onOpenPerson = { person ->
                        if (person == uid) navController.navigateToTab(PassportRoute) else navController.navigate(PersonRoute(person))
                    },
                    onReviewApplicants = { navController.navigate(ApplicantsRoute(it)) },
                    onEdit = { listing -> navController.navigate(EditorRoute(listing.kind.key, listingId = listing.id)) },
                    onFindTeammates = { eventId -> navController.navigate(EditorRoute(ListingKind.Team.key, eventId = eventId)) },
                )
            }
            composable<EditorRoute> {
                EditorScreen(
                    onClose = { navController.popBackStack() },
                    onSaved = { id, wasEditing ->
                        if (wasEditing) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(ListingRoute(id)) {
                                popUpTo<EditorRoute> { inclusive = true }
                            }
                        }
                    },
                    onShareEventInstead = {
                        navController.navigate(EditorRoute(ListingKind.Event.key)) {
                            popUpTo<EditorRoute> { inclusive = true }
                        }
                    },
                )
            }
            composable<ApplicantsRoute>(
                deepLinks = listOf(navDeepLink<ApplicantsRoute>(basePath = "${DeepLinkScheme}applicants")),
            ) {
                ApplicantsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenApplicant = { listingId, applicantId -> navController.navigate(ApplicantRoute(listingId, applicantId)) },
                    onOpenListing = { navController.navigate(ListingRoute(it)) },
                )
            }
            composable<ApplicantRoute> {
                ApplicantDetailScreen(onBack = { navController.popBackStack() })
            }
            composable<PersonRoute>(
                deepLinks = listOf(navDeepLink<PersonRoute>(basePath = "${DeepLinkScheme}person")),
            ) {
                PersonScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
