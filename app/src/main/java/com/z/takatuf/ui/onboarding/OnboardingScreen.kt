package com.z.takatuf.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.z.takatuf.R
import com.z.takatuf.data.SessionState
import com.z.takatuf.ui.appViewModel
import com.z.takatuf.ui.common.BottomActionBar
import com.z.takatuf.ui.common.PrimaryButton
import com.z.takatuf.ui.common.SectionTitle
import com.z.takatuf.ui.passport.AvailabilityEditor
import com.z.takatuf.ui.passport.BasicsEditor
import com.z.takatuf.ui.passport.InterestsEditor
import com.z.takatuf.ui.passport.SkillsEditor
import com.z.takatuf.ui.passport.VisibilityPicker
import com.z.takatuf.ui.theme.LocalReduceMotion

@Composable
fun OnboardingScreen(session: SessionState.Onboarding) {
    val viewModel = appViewModel(key = session.uid) { c, _ ->
        OnboardingViewModel(session.uid, session.profile, c.profiles, c.authRepository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(session.profile) { session.profile?.let(viewModel::prefill) }
    BackHandler(enabled = state.step > 0) { viewModel.back() }
    val reduceMotion = LocalReduceMotion.current

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.step > 0) {
                    IconButton(onClick = viewModel::back) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                } else {
                    TextButton(onClick = viewModel::signOut) { Text(stringResource(R.string.action_sign_out)) }
                }
                Spacer(Modifier.weight(1f))
                if (state.step > 0) {
                    TextButton(onClick = viewModel::finish, enabled = !state.saving) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                }
            }
        },
        bottomBar = {
            BottomActionBar(Modifier.imePadding()) {
                state.error?.let {
                    Text(stringResource(it.message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                PrimaryButton(
                    text = stringResource(if (state.isLastStep) R.string.onboarding_finish else R.string.action_continue),
                    onClick = viewModel::next,
                    loading = state.saving,
                )
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                if (reduceMotion) {
                    ContentTransform(fadeIn(), fadeOut())
                } else {
                    val forward = targetState > initialState
                    (slideInHorizontally { w -> if (forward) w / 4 else -w / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally { w -> if (forward) -w / 4 else w / 4 } + fadeOut())
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "onboarding-step",
        ) { step ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                StepIndicator(step = step, count = OnboardingUiState.LAST_STEP + 1)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(stepTitles[step]),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.widthIn(max = 520.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(stepBodies[step]),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 520.dp),
                )
                Spacer(Modifier.height(24.dp))
                when (step) {
                    0 -> BasicsEditor(
                        name = state.name,
                        onName = viewModel::onName,
                        major = state.major,
                        onMajor = viewModel::onMajor,
                        year = state.year,
                        onYear = viewModel::onYear,
                        headline = state.headline,
                        onHeadline = viewModel::onHeadline,
                        nameError = state.nameError,
                    )
                    1 -> {
                        SkillsEditor(skills = state.skills, onChange = viewModel::onSkills, major = state.major)
                        Spacer(Modifier.height(28.dp))
                        SectionTitle(stringResource(R.string.passport_interests))
                        Spacer(Modifier.height(8.dp))
                        InterestsEditor(selected = state.interests, onChange = viewModel::onInterests)
                    }
                    else -> {
                        AvailabilityEditor(availability = state.availability, onChange = viewModel::onAvailability)
                        Spacer(Modifier.height(28.dp))
                        SectionTitle(stringResource(R.string.visibility_question))
                        Spacer(Modifier.height(8.dp))
                        VisibilityPicker(selected = state.visibility, onSelect = viewModel::onVisibility)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private val stepTitles = listOf(
    R.string.onboarding_basics_title,
    R.string.onboarding_skills_title,
    R.string.onboarding_time_title,
)

private val stepBodies = listOf(
    R.string.onboarding_basics_body,
    R.string.onboarding_skills_body,
    R.string.onboarding_time_body,
)

/** Onboarding really is a sequence, so its steps are numbered. */
@Composable
private fun StepIndicator(step: Int, count: Int) {
    val label = stringResource(R.string.onboarding_step, step + 1, count)
    Column(Modifier.semantics { contentDescription = label }) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(count) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (index <= step) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                )
            }
        }
    }
}
