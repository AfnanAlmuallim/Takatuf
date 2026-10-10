package com.z.takatuf.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.z.takatuf.AppContainer
import com.z.takatuf.TakatufApplication

/**
 * Creates a screen's ViewModel with the app's repositories. Inside a navigation destination
 * the [SavedStateHandle] carries that destination's arguments.
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (container: AppContainer, handle: SavedStateHandle) -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as TakatufApplication).container
    return viewModel(
        key = key,
        factory = viewModelFactory {
            initializer { create(container, createSavedStateHandle()) }
        },
    )
}
