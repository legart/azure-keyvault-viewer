package dk.kodeologi.azure.keyvault.viewer.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dk.kodeologi.azure.keyvault.viewer.data.ExportCommandBuilder
import dk.kodeologi.azure.keyvault.viewer.data.VaultViewerRepository
import dk.kodeologi.azure.keyvault.viewer.model.ExportTargetVault
import dk.kodeologi.azure.keyvault.viewer.model.SecretRef
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.datatransfer.StringSelection

@Composable
fun rememberVaultViewerState(
    repository: VaultViewerRepository,
    exportCommandBuilder: ExportCommandBuilder
): VaultViewerState {
    val scope = rememberCoroutineScope()
    return remember(repository, exportCommandBuilder, scope) {
        VaultViewerState(repository, exportCommandBuilder, scope)
    }
}

@Composable
fun VaultViewerScreen(
    state: VaultViewerState,
    onOpenSettings: () -> Unit = {}
) {
    val copySensitiveTextToClipboard = rememberSensitiveClipboardCopy()

    LaunchedEffect(Unit) { state.loadSubscriptions() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onOpenSettings,
                        enabled = state.isLoggedIn,
                        modifier = Modifier.width(120.dp)
                    ) {
                        Text("Settings")
                    }

                    SelectionDropdown(
                        label = "Select subscription",
                        items = state.subscriptions,
                        selected = state.selectedSubscription,
                        itemLabel = { it.name },
                        modifier = Modifier.weight(1f),
                        buttonModifier = Modifier.fillMaxWidth(),
                        enabled = state.isLoggedIn && state.subscriptions.isNotEmpty(),
                        onSelect = state::onSubscriptionSelected
                    )

                    SelectionDropdown(
                        label = "Select key vault",
                        items = state.vaults,
                        selected = state.selectedVault,
                        itemLabel = { it.name },
                        modifier = Modifier.weight(1f),
                        buttonModifier = Modifier.fillMaxWidth(),
                        enabled = state.isLoggedIn && state.selectedSubscription != null,
                        onSelect = state::onVaultSelected
                    )
                }

                OutlinedTextField(
                    value = state.filter,
                    onValueChange = { state.filter = it },
                    label = { Text("Filter secrets") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.isLoggedIn && state.selectedVault != null
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = state::selectAllFilteredSecrets,
                        enabled = state.isLoggedIn && state.selectedVault != null && state.filteredSecrets.isNotEmpty()
                    ) {
                        Text("Select all filtered (${state.filteredSecrets.size})")
                    }
                    OutlinedButton(
                        onClick = state::clearSecretSelection,
                        enabled = state.isLoggedIn && state.selectedSecretCount > 0
                    ) {
                        Text("Clear selection (${state.selectedSecretCount})")
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = state::openExportTargetDialog,
                        enabled = state.isLoggedIn && state.selectedSecretCount > 0 && state.exportTargets.isNotEmpty()
                    ) {
                        Text("Export selected")
                    }
                }
            }
        }

        if (state.errorMessage != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = state.errorMessage!!, color = MaterialTheme.colorScheme.error)
                if (state.hasAuthenticationError) {
                    OutlinedButton(
                        onClick = state::loadSubscriptions,
                        enabled = !state.isLoading
                    ) {
                        Text("Retry")
                    }
                }
            }
        }

        if (state.isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Loading...")
            }
        }

        SecretTable(
            secrets = state.filteredSecrets,
            isSelected = state::isSecretSelected,
            onToggleSelection = state::onSecretSelectionChanged,
            onRowClick = state::loadSecretValue
        )
    }

    if (state.dialogSecret != null) {
        AlertDialog(
            onDismissRequest = state::dismissSecretDialog,
            title = { Text("Secret: ${state.dialogSecret!!.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SelectionContainer {
                        Text(state.dialogSecret!!.value)
                    }
                    ClipboardNotice()
                }
            },
            confirmButton = {
                Button(onClick = { copySensitiveTextToClipboard(state.dialogSecret!!.value) }) {
                    Text("Copy to clipboard")
                }
            },
            dismissButton = {
                Button(onClick = state::dismissSecretDialog) {
                    Text("Close")
                }
            }
        )
    }

    if (state.exportCommands.isNotBlank()) {
        AlertDialog(
            onDismissRequest = state::dismissExportCommandsDialog,
            title = { Text("Generated az commands") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SelectionContainer {
                        Text(state.exportCommands)
                    }
                    ClipboardNotice()
                }
            },
            confirmButton = {
                Button(onClick = { copySensitiveTextToClipboard(state.exportCommands) }) {
                    Text("Copy to clipboard")
                }
            },
            dismissButton = {
                Button(onClick = state::dismissExportCommandsDialog) {
                    Text("Close")
                }
            }
        )
    }

    if (state.isExportTargetDialogOpen) {
        val exportDropdownWidth = rememberExportTargetDropdownWidth(state.exportTargets)
        AlertDialog(
            onDismissRequest = state::dismissExportTargetDialog,
            title = { Text("Export selected secrets") },
            text = {
                Column(
                    modifier = Modifier.widthIn(min = exportDropdownWidth),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Select target key vaults")
                    ExportTargetMultiSelectionDropdown(
                        items = state.exportTargets,
                        isSelected = state::isExportTargetSelected,
                        onSelectionChanged = state::onExportTargetSelectionChanged,
                        enabled = state.exportTargets.isNotEmpty() && !state.isLoading,
                        menuWidth = exportDropdownWidth
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = state::generateExportCommands,
                    enabled = state.selectedExportTargetCount > 0 && !state.isLoading
                ) {
                    Text("Generate")
                }
            },
            dismissButton = {
                Button(onClick = state::dismissExportTargetDialog) {
                    Text("Close")
                }
            }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun rememberSensitiveClipboardCopy(
    clearDelayMillis: Long = SENSITIVE_CLIPBOARD_CLEAR_DELAY_MILLIS
): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var clearJob by remember { mutableStateOf<Job?>(null) }

    return remember(clipboard, scope, clearDelayMillis) {
        { value: String ->
            clearJob?.cancel()
            clearJob = scope.launch {
                val copiedEntry = ClipEntry(StringSelection(value))
                clipboard.setClipEntry(copiedEntry)
                delay(clearDelayMillis)
                val currentEntry = clipboard.getClipEntry()
                if (currentEntry?.nativeClipEntry == copiedEntry.nativeClipEntry) {
                    clipboard.setClipEntry(null)
                }
            }
        }
    }
}

@Composable
private fun ClipboardNotice() {
    Text(
        text = "Sensitive content copied here is cleared from the clipboard after 30 seconds if it has not changed.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SecretTable(
    secrets: List<SecretRef>,
    isSelected: (SecretRef) -> Boolean,
    onToggleSelection: (SecretRef, Boolean) -> Unit,
    onRowClick: (SecretRef) -> Unit
) {
    val listState = rememberLazyListState()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(vertical = 8.dp, horizontal = 10.dp)
        ) {
            Text("Select", modifier = Modifier.width(72.dp))
            Text("Secret name")
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                items(secrets) { secret ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 10.dp)
                            .heightIn(min = 36.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected(secret),
                            onCheckedChange = { checked -> onToggleSelection(secret, checked) },
                            modifier = Modifier.width(72.dp)
                        )
                        Text(
                            text = secret.name,
                            modifier = Modifier.clickable { onRowClick(secret) }
                        )
                    }
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun <T> SelectionDropdown(
    label: String,
    items: List<T>,
    selected: T?,
    itemLabel: (T) -> String,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val menuWidth = rememberDropdownWidth(
        labels = buildList {
            add(label)
            selected?.let { add(itemLabel(it)) }
            addAll(items.map(itemLabel))
        },
        minWidth = 240.dp,
        maxWidth = 700.dp,
        horizontalChrome = 64.dp
    )

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = buttonModifier
        ) {
            Text(
                text = selected?.let(itemLabel) ?: label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = menuWidth)
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = itemLabel(item),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(item)
                    }
                )
            }
        }
    }
}

@Composable
private fun ExportTargetMultiSelectionDropdown(
    items: List<ExportTargetVault>,
    isSelected: (ExportTargetVault) -> Boolean,
    onSelectionChanged: (ExportTargetVault, Boolean) -> Unit,
    enabled: Boolean,
    menuWidth: Dp
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedCount = items.count(isSelected)
    val buttonLabel = when {
        selectedCount == 0 -> "Select target key vaults"
        selectedCount == 1 -> "1 target key vault selected"
        else -> "$selectedCount target key vaults selected"
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(buttonLabel)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = menuWidth)
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected(item),
                                onCheckedChange = null
                            )
                            Text(
                                text = "${item.vault.name} (${item.subscriptionName})",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    onClick = { onSelectionChanged(item, !isSelected(item)) }
                )
            }
        }
    }
}

@Composable
private fun rememberExportTargetDropdownWidth(
    items: List<ExportTargetVault>
): Dp {
    return rememberDropdownWidth(
        labels = items.map { "${it.vault.name} (${it.subscriptionName})" },
        minWidth = 420.dp,
        maxWidth = 900.dp,
        horizontalChrome = 120.dp
    )
}

@Composable
private fun rememberDropdownWidth(
    labels: List<String>,
    minWidth: Dp,
    maxWidth: Dp,
    horizontalChrome: Dp
): Dp {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val maxTextWidthPx = labels.maxOfOrNull { label ->
        textMeasurer.measure(label).size.width
    } ?: 0
    val measuredWidth = with(density) { maxTextWidthPx.toDp() + horizontalChrome }
    return measuredWidth.coerceIn(minWidth, maxWidth)
}

private const val SENSITIVE_CLIPBOARD_CLEAR_DELAY_MILLIS = 30_000L
