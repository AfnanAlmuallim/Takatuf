package com.z.takatuf.ui.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Diversity3
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Diversity3
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.z.takatuf.data.model.AppNotification
import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.data.model.NotificationTarget
import com.z.takatuf.ui.activity.ActivityScreen
import com.z.takatuf.ui.activity.attentionCount
import com.z.takatuf.ui.applicants.ApplicantDetailScreen
import com.z.takatuf.ui.applicants.ApplicantsScreen
import com.z.takatuf.ui.discover.DiscoverScreen
import com.z.takatuf.ui.editor.EditorScreen
import com.z.takatuf.ui.listing.ListingDetailScreen
import com.z.takatuf.ui.moderation.ModerationScreen
import com.z.takatuf.ui.moderation.ReportDetailScreen
import com.z.takatuf.ui.notifications.NotificationsScreen
import com.z.takatuf.ui.passport.PassportScreen
import com.z.takatuf.ui.passport.PersonScreen
import com.z.takatuf.ui.records.FeedbackScreen
import com.z.takatuf.ui.team.TeamBuilderScreen
import com.z.takatuf.ui.team.TeamsScreen
import com.z.takatuf.ui.team.WorkspaceScreen
import com.z.takatuf.ui.team.leadRequestCount
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
    Teams(TeamsRoute, TeamsRoute::class, R.string.tab_teams, Icons.Rounded.Diversity3, Icons.Outlined.Diversity3),
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
    // Activity: new applicants plus offers and reconfirmations to answer. Teams: leadership handovers.
    val attentionFlow = remember(uid) { container.applications.attentionCount(uid) }
    val attention by attentionFlow.collectAsStateWithLifecycle(0)
    val leadRequestsFlow = remember(uid) { container.teams.leadRequestCount(uid) }
    val leadRequests by leadRequestsFlow.collectAsStateWithLifecycle(0)

    LaunchedEffect(navController) {
        newIntents.collect { navController.handleDeepLink(it) }
    }
    AskForNotifications()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(visible = showBar, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        TopLevel.entries.forEach { tab ->
                            val selected = destination?.hasRoute(tab.routeClass) == true
                            val badge = when (tab) {
                                TopLevel.Activity -> attention
                                TopLevel.Teams -> leadRequests
                                else -> 0
                            }
                            val badgeText = if (badge > 0) pluralStringResource(R.plurals.attention_badge_a11y, badge, badge) else null
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
                                label = { Text(stringResource(tab.label), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
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
            composable<DiscoverRoute>(
                deepLinks = listOf(navDeepLink { uriPattern = "${DeepLinkScheme}discover" }),
            ) {
                DiscoverScreen(
                    onOpenListing = { navController.navigate(ListingRoute(it)) },
                    onPost = { kind -> navController.navigate(EditorRoute(kind.key)) },
                    onInbox = { navController.navigate(NotificationsRoute) },
                )
            }
            composable<TeamsRoute>(
                deepLinks = listOf(navDeepLink { uriPattern = "${DeepLinkScheme}teams" }),
            ) {
                TeamsScreen(
                    onOpenTeam = { navController.navigate(WorkspaceRoute(it)) },
                    onDiscover = { navController.navigateToTab(DiscoverRoute) },
                    onInbox = { navController.navigate(NotificationsRoute) },
                )
            }
            composable<ActivityRoute>(
                deepLinks = listOf(navDeepLink { uriPattern = "${DeepLinkScheme}activity" }),
            ) {
                ActivityScreen(
                    onOpenListing = { navController.navigate(ListingRoute(it)) },
                    onOpenApplicants = { navController.navigate(ApplicantsRoute(it)) },
                    onDiscover = { navController.navigateToTab(DiscoverRoute) },
                    onPost = { kind -> navController.navigate(EditorRoute(kind.key)) },
                    onOpenTeam = { navController.navigate(WorkspaceRoute(it)) },
                    onInbox = { navController.navigate(NotificationsRoute) },
                )
            }
            composable<PassportRoute>(
                deepLinks = listOf(navDeepLink { uriPattern = "${DeepLinkScheme}passport" }),
            ) {
                PassportScreen(
                    onInbox = { navController.navigate(NotificationsRoute) },
                    onOpenModeration = { navController.navigate(ModerationRoute) },
                    onReviewTeammates = { navController.navigate(FeedbackRoute(it)) },
                )
            }
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
                    onOpenTeam = { navController.navigate(WorkspaceRoute(it)) },
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
                    onBuildTeam = { navController.navigate(TeamBuilderRoute(it)) },
                )
            }
            composable<ApplicantRoute>(
                deepLinks = listOf(navDeepLink<ApplicantRoute>(basePath = "${DeepLinkScheme}applicant")),
            ) {
                ApplicantDetailScreen(onBack = { navController.popBackStack() })
            }
            composable<TeamBuilderRoute> {
                TeamBuilderScreen(
                    onBack = { navController.popBackStack() },
                    onOpenApplicant = { listingId, applicantId -> navController.navigate(ApplicantRoute(listingId, applicantId)) },
                )
            }
            composable<WorkspaceRoute>(
                deepLinks = listOf(navDeepLink<WorkspaceRoute>(basePath = "${DeepLinkScheme}team")),
            ) {
                WorkspaceScreen(
                    onBack = { navController.popBackStack() },
                    onOpenListing = { navController.navigate(ListingRoute(it)) },
                    onOpenPerson = { person ->
                        if (person == uid) navController.navigateToTab(PassportRoute) else navController.navigate(PersonRoute(person))
                    },
                    onReviewApplicants = { navController.navigate(ApplicantsRoute(it)) },
                    onReviewTeammates = { navController.navigate(FeedbackRoute(it)) },
                )
            }
            composable<PersonRoute>(
                deepLinks = listOf(navDeepLink<PersonRoute>(basePath = "${DeepLinkScheme}person")),
            ) {
                PersonScreen(onBack = { navController.popBackStack() })
            }
            composable<FeedbackRoute> {
                FeedbackScreen(onBack = { navController.popBackStack() })
            }
            composable<NotificationsRoute>(
                deepLinks = listOf(navDeepLink { uriPattern = "${DeepLinkScheme}inbox" }),
            ) {
                NotificationsScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { navController.open(it, uid) },
                )
            }
            composable<ModerationRoute> {
                ModerationScreen(
                    onBack = { navController.popBackStack() },
                    onOpenReport = { navController.navigate(ReportRoute(it)) },
                )
            }
            composable<ReportRoute>(
                deepLinks = listOf(navDeepLink<ReportRoute>(basePath = "${DeepLinkScheme}report")),
            ) {
                ReportDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenListing = { navController.navigate(ListingRoute(it)) },
                    onOpenPerson = { person ->
                        if (person == uid) navController.navigateToTab(PassportRoute) else navController.navigate(PersonRoute(person))
                    },
                )
            }
        }
    }
}

/**
 * Asks once, on Android 13 and later, whether Takatuf may show notifications. Saying no
 * leaves the in-app inbox working as before.
 */
@Composable
private fun AskForNotifications() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("permissions", Context.MODE_PRIVATE)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted && !prefs.getBoolean("asked_notifications", false)) {
            prefs.edit { putBoolean("asked_notifications", true) }
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Opens what a notification is about (US19). */
private fun NavHostController.open(notification: AppNotification, uid: String) {
    val listingId = notification.listingId
    // Notices already stored may point at a listing without naming one; never open an empty id.
    if (listingId.isBlank() && notification.target.needsListing) {
        navigateToTab(PassportRoute)
        return
    }
    when (notification.target) {
        NotificationTarget.Listing -> navigate(ListingRoute(listingId))
        NotificationTarget.Workspace -> navigate(WorkspaceRoute(listingId))
        NotificationTarget.Applicants -> navigate(ApplicantsRoute(listingId))
        NotificationTarget.Applicant -> {
            val applicantId = notification.targetId.substringAfter('/', "")
            if (applicantId.isNotBlank()) navigate(ApplicantRoute(listingId, applicantId)) else navigate(ApplicantsRoute(listingId))
        }
        NotificationTarget.Activity -> navigateToTab(ActivityRoute)
        NotificationTarget.Teams -> navigateToTab(TeamsRoute)
        NotificationTarget.Passport ->
            if (notification.targetId.isNotBlank() && notification.targetId != uid && notification.targetId != listingId) {
                navigate(PersonRoute(notification.targetId))
            } else {
                navigateToTab(PassportRoute)
            }
        NotificationTarget.Reports ->
            if (notification.targetId.isNotBlank()) navigate(ReportRoute(notification.targetId)) else navigate(ModerationRoute)
    }
}

private fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
