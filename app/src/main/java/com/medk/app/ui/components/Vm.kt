package com.medk.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medk.app.core.UiState
import com.medk.app.core.friendly
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Generic ViewModel for screens that load one piece of data. */
class LoadViewModel<T>(private val loader: suspend () -> T) : ViewModel() {
    var onSilentError: ((String) -> Unit)? = null
    private val _state = MutableStateFlow<UiState<T>>(UiState.Loading)
    val state: StateFlow<UiState<T>> = _state.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init { load() }

    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            if (silent) _refreshing.value = true else if (_state.value !is UiState.Success) _state.value = UiState.Loading
            _state.value = try { UiState.Success(loader()) } catch (e: Exception) {
                if (silent && _state.value is UiState.Success) { onSilentError?.invoke(e.friendly()); _state.value } else UiState.Error(e.friendly())
            }
            _refreshing.value = false
        }
    }

    fun update(transform: (T) -> T) {
        val s = _state.value
        if (s is UiState.Success) _state.value = UiState.Success(transform(s.data))
    }
}

@Composable
fun <T> loadVm(key: String, loader: suspend () -> T): LoadViewModel<T> =
    viewModel(key = key) { LoadViewModel(loader) }

/** Runs a suspending action with busy flag + error to message. */
class ActionViewModel : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun run(onError: (String) -> Unit, onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try { block(); onSuccess() } catch (e: Exception) { onError(e.friendly()) } finally { _busy.value = false }
        }
    }
}

@Composable
fun actionVm(key: String = "action"): ActionViewModel = viewModel(key = key) { ActionViewModel() }

/** App-wide snackbar sink. */
val LocalToast = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
fun <T> StateView(state: com.medk.app.core.UiState<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        is com.medk.app.core.UiState.Loading -> LoadingBox()
        is com.medk.app.core.UiState.Error -> ErrorBox(state.message, onRetry)
        is com.medk.app.core.UiState.Success -> content(state.data)
    }
}
