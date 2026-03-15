package dk.kodeologi.azure.keyvault.viewer.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState

@Composable
fun SettingsWindow(
    state: VaultViewerState,
    onCloseRequest: () -> Unit
) {
    val windowState = rememberWindowState(width = 900.dp, height = 620.dp)
    var selectedSection by remember { mutableStateOf(SettingsSection.KEY_VAULTS) }

    Window(
        onCloseRequest = onCloseRequest,
        title = "Settings",
        state = windowState
    ) {
        VaultViewerTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    SettingsSectionList(
                        selectedSection = selectedSection,
                        onSectionSelected = { selectedSection = it }
                    )
                    when (selectedSection) {
                        SettingsSection.KEY_VAULTS -> KeyVaultSettingsPane(state)
                    }
                }
            }
        }
    }
}

private enum class SettingsSection(val title: String) {
    KEY_VAULTS("Key vaults")
}

@Composable
private fun SettingsSectionList(
    selectedSection: SettingsSection,
    onSectionSelected: (SettingsSection) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(180.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsSection.entries.forEach { section ->
            val isSelected = section == selectedSection
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = section.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSectionSelected(section) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun KeyVaultSettingsPane(state: VaultViewerState) {
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Show these keyvaults", style = MaterialTheme.typography.titleLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = state::showAllVaults,
                enabled = state.configurableVaults.isNotEmpty()
            ) {
                Text("Select all")
            }

            Button(
                onClick = state::hideAllVaults,
                enabled = state.configurableVaults.isNotEmpty()
            ) {
                Text("Deselect all")
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(vertical = 8.dp, horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show", modifier = Modifier.width(72.dp))
                Text("Key vault name", modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Subscription name", modifier = Modifier.weight(1f))
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState
                ) {
                    items(state.configurableVaults) { target ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 10.dp)
                                .heightIn(min = 36.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = state.isVaultVisible(target),
                                onCheckedChange = { checked -> state.onVaultVisibilityChanged(target, checked) },
                                modifier = Modifier.width(72.dp)
                            )
                            Text(text = target.vault.name, modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = target.subscriptionName, modifier = Modifier.weight(1f))
                        }
                    }
                }

                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                )
            }
        }
    }
}
