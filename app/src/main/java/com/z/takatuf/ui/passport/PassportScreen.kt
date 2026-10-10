package com.z.takatuf.ui.passport

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.toRoute
import com.z.takatuf.R
import com.z.takatuf.data.model.EvidenceLink
import com.z.takatuf.data.model.Experience
import com.z.takatuf.data.model.Passport
import com.z.takatuf.data.model.PassportAccess
import com.z.takatuf.data.model.Profile
import com.z.takatuf.domain.Validators
import com.z.takatuf.ui.appViewModel
import com.z.takatuf.ui.common.ErrorState
import com.z.takatuf.ui.common.FullScreenLoading
import com.z.takatuf.ui.common.MessageEffect
import com.z.takatuf.ui.common.PrimaryButton
import com.z.takatuf.ui.navigation.PersonRoute
import java.util.UUID

private enum class Sheet { Basics, Visibility, Skills, Interests, Availability, Experience, Links }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassportScreen() {
    val viewModel = appViewModel { c, _ -> MyPassportViewModel(c.session, c.profiles, c.authRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::messageShown)
    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.savedAt) { if (state.savedAt != 0L && sheet != Sheet.Visibility) sheet = null }

    val profile = state.profile
    val passport = state.passport
    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (profile == null || passport == null) {
            FullScreenLoading(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.passport_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_change_password)) },
                            leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                            onClick = { menu = false; viewModel.sendPasswordReset() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_sign_out)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null) },
                            onClick = { menu = false; confirmSignOut = true },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            IdentityHeader(profile) {
                TextButton(onClick = { sheet = Sheet.Basics }) { Text(stringResource(R.string.action_edit)) }
            }
            Spacer(Modifier.height(16.dp))
            VisibilityRow(profile, onChange = { sheet = Sheet.Visibility })
            NextStep(passport, onAct = { sheet = it })
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            PassportSections(
                passport = passport,
                onEdit = { section ->
                    sheet = when (section) {
                        PassportSection.Skills -> Sheet.Skills
                        PassportSection.Interests -> Sheet.Interests
                        PassportSection.Availability -> Sheet.Availability
                        PassportSection.Experience -> Sheet.Experience
                        PassportSection.Links -> Sheet.Links
                    }
                },
            )
            Spacer(Modifier.height(24.dp))
            state.email?.let {
                Text(
                    stringResource(R.string.signed_in_as, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (profile != null && passport != null) {
        when (sheet) {
            Sheet.Basics -> BasicsSheet(profile, state.saving, onSave = viewModel::saveBasics, onDismiss = { sheet = null })
            Sheet.Visibility -> EditSheet(stringResource(R.string.visibility_question), onDismiss = { sheet = null }) {
                VisibilityPicker(selected = profile.visibility, onSelect = viewModel::setVisibility)
                Spacer(Modifier.height(16.dp))
                PrimaryButton(stringResource(R.string.action_done), onClick = { sheet = null })
            }
            Sheet.Skills -> {
                var skills by remember { mutableStateOf(passport.skills) }
                EditSheet(stringResource(R.string.passport_skills), onDismiss = { sheet = null }) {
                    SkillsEditor(skills = skills, onChange = { skills = it }, major = profile.major)
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton(stringResource(R.string.action_save), onClick = { viewModel.saveSkills(skills) }, loading = state.saving)
                }
            }
            Sheet.Interests -> {
                var interests by remember { mutableStateOf(passport.interests) }
                EditSheet(stringResource(R.string.passport_interests), onDismiss = { sheet = null }) {
                    InterestsEditor(selected = interests, onChange = { interests = it })
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton(stringResource(R.string.action_save), onClick = { viewModel.saveInterests(interests) }, loading = state.saving)
                }
            }
            Sheet.Availability -> {
                var availability by remember { mutableStateOf(passport.availability) }
                EditSheet(stringResource(R.string.passport_availability), onDismiss = { sheet = null }) {
                    AvailabilityEditor(availability = availability, onChange = { availability = it })
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton(stringResource(R.string.action_save), onClick = { viewModel.saveAvailability(availability) }, loading = state.saving)
                }
            }
            Sheet.Experience -> ExperienceSheet(passport.experience, state.saving, onSave = viewModel::saveExperience, onDismiss = { sheet = null })
            Sheet.Links -> LinksSheet(passport.links, state.saving, onSave = viewModel::saveLinks, onDismiss = { sheet = null })
            null -> Unit
        }
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.sign_out_title)) },
            text = { Text(stringResource(R.string.sign_out_body)) },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; viewModel.signOut() }) { Text(stringResource(R.string.action_sign_out)) } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun VisibilityRow(profile: Profile, onChange: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onChange),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(visibilityIcon(profile.visibility), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.visibility_current, visibilityTitle(profile.visibility)), style = MaterialTheme.typography.titleSmall)
                Text(visibilityBody(profile.visibility), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onChange) { Text(stringResource(R.string.action_change)) }
        }
    }
}

/** One suggestion at a time for the most useful missing piece of the passport. */
@Composable
private fun NextStep(passport: Passport, onAct: (Sheet) -> Unit) {
    val (text, action, sheet) = when {
        passport.skills.isEmpty() -> Triple(R.string.nudge_skills, R.string.action_add_skills, Sheet.Skills)
        !passport.availability.isSet -> Triple(R.string.nudge_availability, R.string.action_add_availability, Sheet.Availability)
        passport.links.isEmpty() -> Triple(R.string.nudge_links, R.string.action_add_link, Sheet.Links)
        else -> return
    }
    Spacer(Modifier.height(12.dp))
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onAct(sheet) }) { Text(stringResource(action)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BasicsSheet(profile: Profile, saving: Boolean, onSave: (String, String, Int?, String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(profile.displayName) }
    var major by rememberSaveable { mutableStateOf(profile.major) }
    var year by rememberSaveable { mutableStateOf(profile.year) }
    var headline by rememberSaveable { mutableStateOf(profile.headline) }
    EditSheet(stringResource(R.string.passport_basics), onDismiss) {
        BasicsEditor(
            name = name, onName = { name = it },
            major = major, onMajor = { major = it },
            year = year, onYear = { year = it },
            headline = headline, onHeadline = { headline = it },
            nameError = name.isBlank(),
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(stringResource(R.string.action_save), onClick = { onSave(name, major, year, headline) }, enabled = name.isNotBlank(), loading = saving)
    }
}

@Composable
private fun ExperienceSheet(items: List<Experience>, saving: Boolean, onSave: (List<Experience>) -> Unit, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf(items) }
    var title by rememberSaveable { mutableStateOf("") }
    var organization by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    EditSheet(stringResource(R.string.passport_experience), onDismiss) {
        list.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                    Text(listOf(item.organization, item.period).filter { it.isNotBlank() }.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { list = list - item }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_remove_named, item.title))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (list.isNotEmpty()) Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.experience_add), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { if (it.length <= 80) title = it }, label = { Text(stringResource(R.string.experience_title)) }, placeholder = { Text(stringResource(R.string.experience_title_placeholder)) }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(organization, { if (it.length <= 80) organization = it }, label = { Text(stringResource(R.string.experience_org)) }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(period, { if (it.length <= 40) period = it }, label = { Text(stringResource(R.string.experience_period)) }, placeholder = { Text(stringResource(R.string.experience_period_placeholder)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { if (it.length <= 400) description = it }, label = { Text(stringResource(R.string.experience_description)) }, minLines = 2, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth())
            TextButton(
                onClick = {
                    list = list + Experience(UUID.randomUUID().toString().take(8), title.trim(), organization.trim(), period.trim(), description.trim())
                    title = ""; organization = ""; period = ""; description = ""
                },
                enabled = title.isNotBlank() && list.size < 10,
            ) { Text(stringResource(R.string.experience_add_action)) }
        }
        Spacer(Modifier.height(12.dp))
        val pending = title.isNotBlank()
        PrimaryButton(
            text = stringResource(R.string.action_save),
            onClick = {
                val final = if (pending) list + Experience(UUID.randomUUID().toString().take(8), title.trim(), organization.trim(), period.trim(), description.trim()) else list
                onSave(final)
            },
            loading = saving,
        )
    }
}

@Composable
private fun LinksSheet(items: List<EvidenceLink>, saving: Boolean, onSave: (List<EvidenceLink>) -> Unit, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf(items) }
    var label by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var urlError by rememberSaveable { mutableStateOf(false) }
    fun pendingLink(): EvidenceLink? {
        if (label.isBlank() && url.isBlank()) return null
        val normalized = Validators.normalizeUrl(url) ?: return null
        return EvidenceLink(UUID.randomUUID().toString().take(8), label.trim().ifBlank { normalized.removePrefix("https://") }, normalized)
    }
    EditSheet(stringResource(R.string.passport_links), onDismiss) {
        Text(stringResource(R.string.links_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        list.forEach { link ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(link.label, style = MaterialTheme.typography.titleSmall)
                    Text(link.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                IconButton(onClick = { list = list - link }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_remove_named, link.label))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (list.isNotEmpty()) Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(label, { if (it.length <= 40) label = it }, label = { Text(stringResource(R.string.link_label)) }, placeholder = { Text(stringResource(R.string.link_label_placeholder)) }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                url, { url = it; urlError = false },
                label = { Text(stringResource(R.string.link_url)) },
                placeholder = { Text("github.com/you/project") },
                isError = urlError,
                supportingText = if (urlError) ({ Text(stringResource(R.string.link_url_error)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = {
                    val link = pendingLink()
                    if (link == null) urlError = true else { list = list + link; label = ""; url = "" }
                },
                enabled = url.isNotBlank() && list.size < 8,
            ) { Text(stringResource(R.string.link_add_action)) }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = stringResource(R.string.action_save),
            onClick = {
                if (url.isNotBlank()) {
                    val link = pendingLink()
                    if (link == null) { urlError = true; return@PrimaryButton }
                    onSave(list + link)
                } else {
                    onSave(list)
                }
            },
            loading = saving,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { c, handle: SavedStateHandle -> PersonViewModel(handle.toRoute<PersonRoute>().uid, c.profiles) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.passport_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val profile = state.profile
        when {
            state.loading -> FullScreenLoading(Modifier.padding(padding))
            state.error != null || profile == null -> Column(Modifier.padding(padding)) {
                ErrorState(state.error ?: com.z.takatuf.data.AppError.NotFound, viewModel::load)
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                IdentityHeader(profile)
                Spacer(Modifier.height(20.dp))
                when (val access = state.passport) {
                    is PassportAccess.Visible ->
                        if (access.passport.isEmpty) {
                            Text(stringResource(R.string.passport_missing, profile.displayName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            PassportSections(access.passport)
                        }
                    PassportAccess.Hidden -> HiddenPassport(profile.displayName, profile.visibility)
                    else -> Text(stringResource(R.string.passport_missing, profile.displayName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
