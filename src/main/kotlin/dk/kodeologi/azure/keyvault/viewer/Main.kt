package dk.kodeologi.azure.keyvault.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.material3.Surface
import dk.kodeologi.azure.keyvault.viewer.auth.AzureTokenProvider
import dk.kodeologi.azure.keyvault.viewer.data.AzureRestClient
import dk.kodeologi.azure.keyvault.viewer.data.ExportCommandBuilder
import dk.kodeologi.azure.keyvault.viewer.data.SelectionPreferences
import dk.kodeologi.azure.keyvault.viewer.data.VaultViewerRepository
import dk.kodeologi.azure.keyvault.viewer.ui.SettingsWindow
import dk.kodeologi.azure.keyvault.viewer.ui.VaultViewerTheme
import dk.kodeologi.azure.keyvault.viewer.ui.VaultViewerScreen
import dk.kodeologi.azure.keyvault.viewer.ui.rememberVaultViewerState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay

fun main() = application {
    val selectionPreferences = remember { SelectionPreferences() }
    val tokenProvider = remember { AzureTokenProvider() }
    val client = remember { AzureRestClient(tokenProvider) }
    val repository = remember { VaultViewerRepository(client, selectionPreferences) }
    val exportCommandBuilder = remember { ExportCommandBuilder() }
    val state = rememberVaultViewerState(
        repository = repository,
        exportCommandBuilder = exportCommandBuilder
    )
    val restoredSize = rememberRestoredWindowSize(selectionPreferences)
    var settingsWindowOpen by remember { mutableStateOf(false) }
    val windowState = rememberWindowState(
        width = restoredSize?.first?.dp ?: 1000.dp,
        height = restoredSize?.second?.dp ?: 700.dp
    )

    LaunchedEffect(windowState) {
        snapshotFlow { windowState.size }
            .map { it.width to it.height }
            .filter { (width, height) ->
                width.value.isFinite() && height.value.isFinite() && width.value > 0f && height.value > 0f
            }
            .distinctUntilChanged()
            .collectLatest { (width, height) ->
                delay(WINDOW_SIZE_PERSIST_DEBOUNCE_MILLIS)
                selectionPreferences.setWindowSizeDp(width.value, height.value)
            }
    }

    Window(onCloseRequest = ::exitApplication, title = "Azure Key Vault Viewer", state = windowState) {
        VaultViewerTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                VaultViewerScreen(
                    state = state,
                    onOpenSettings = { settingsWindowOpen = true }
                )
            }
        }
    }

    if (settingsWindowOpen) {
        SettingsWindow(
            state = state,
            onCloseRequest = { settingsWindowOpen = false }
        )
    }
}

@androidx.compose.runtime.Composable
private fun rememberRestoredWindowSize(selectionPreferences: SelectionPreferences): Pair<Float, Float>? {
    return remember(selectionPreferences) { selectionPreferences.getWindowSizeDp() }
}

private const val WINDOW_SIZE_PERSIST_DEBOUNCE_MILLIS = 250L
