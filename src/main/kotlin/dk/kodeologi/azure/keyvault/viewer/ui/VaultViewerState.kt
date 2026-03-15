package dk.kodeologi.azure.keyvault.viewer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dk.kodeologi.azure.keyvault.viewer.auth.AzureAuthenticationException
import dk.kodeologi.azure.keyvault.viewer.data.ExportCommandBuilder
import dk.kodeologi.azure.keyvault.viewer.data.VaultViewerRepository
import dk.kodeologi.azure.keyvault.viewer.model.ExportTargetVault
import dk.kodeologi.azure.keyvault.viewer.model.KeyVault
import dk.kodeologi.azure.keyvault.viewer.model.SecretRef
import dk.kodeologi.azure.keyvault.viewer.model.SecretValue
import dk.kodeologi.azure.keyvault.viewer.model.Subscription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException

class VaultViewerState(
    private val repository: VaultViewerRepository,
    private val exportCommandBuilder: ExportCommandBuilder,
    private val scope: CoroutineScope
) {
    private var allSubscriptions by mutableStateOf(emptyList<Subscription>())
    var selectedSubscription by mutableStateOf<Subscription?>(null)

    private var allVaults by mutableStateOf(emptyList<KeyVault>())
    var selectedVault by mutableStateOf<KeyVault?>(null)
    private var hiddenVaultKeys by mutableStateOf(repository.getHiddenVaultKeys())

    var secrets by mutableStateOf(emptyList<SecretRef>())
    var filter by mutableStateOf("")
    var selectedSecretIds by mutableStateOf(setOf<String>())

    private var allExportTargets by mutableStateOf(emptyList<ExportTargetVault>())
    private var selectedExportTargetKeys by mutableStateOf(setOf<String>())
    var isExportTargetDialogOpen by mutableStateOf(false)
    var exportCommands by mutableStateOf("")

    var dialogSecret by mutableStateOf<SecretValue?>(null)
    var isLoggedIn by mutableStateOf(false)
    var hasAuthenticationError by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    private var activeLoadCount = 0
    private var subscriptionLoadVersion = 0
    private var vaultLoadVersion = 0
    private var secretListLoadVersion = 0
    private var secretValueLoadVersion = 0
    private var exportCommandLoadVersion = 0

    val subscriptions: List<Subscription>
        get() {
            val visibleSubscriptionIds = exportTargets
                .asSequence()
                .map { it.subscriptionId }
                .toSet()
            return allSubscriptions.filter { it.id in visibleSubscriptionIds }
        }

    val vaults: List<KeyVault>
        get() {
            val subscriptionId = selectedSubscription?.id ?: return emptyList()
            return allVaults.filter { isVaultVisible(subscriptionId, it) }
        }

    val exportTargets: List<ExportTargetVault>
        get() = allExportTargets.filter { isVaultVisible(it.subscriptionId, it.vault) }

    val selectedExportTargets: List<ExportTargetVault>
        get() = exportTargets.filter { exportTargetKey(it) in selectedExportTargetKeys }

    val configurableVaults: List<ExportTargetVault>
        get() = allExportTargets

    val filteredSecrets: List<SecretRef>
        get() {
            val term = filter.trim().lowercase()
            return if (term.isBlank()) secrets else secrets.filter { it.name.lowercase().contains(term) }
        }

    val selectedSecretCount: Int
        get() = selectedSecretIds.size

    val selectedExportTargetCount: Int
        get() = selectedExportTargets.size

    fun isVaultVisible(target: ExportTargetVault): Boolean = isVaultVisible(target.subscriptionId, target.vault)

    fun onVaultVisibilityChanged(target: ExportTargetVault, visible: Boolean) {
        hiddenVaultKeys = if (visible) {
            hiddenVaultKeys - vaultVisibilityKey(target.subscriptionId, target.vault)
        } else {
            hiddenVaultKeys + vaultVisibilityKey(target.subscriptionId, target.vault)
        }
        repository.setHiddenVaultKeys(hiddenVaultKeys)
        applyVaultVisibility()
    }

    fun showAllVaults() {
        hiddenVaultKeys = emptySet()
        repository.setHiddenVaultKeys(hiddenVaultKeys)
        applyVaultVisibility()
    }

    fun hideAllVaults() {
        hiddenVaultKeys = configurableVaults
            .mapTo(mutableSetOf()) { vaultVisibilityKey(it.subscriptionId, it.vault) }
        repository.setHiddenVaultKeys(hiddenVaultKeys)
        applyVaultVisibility()
    }

    fun loadSubscriptions() {
        val requestVersion = ++subscriptionLoadVersion
        scope.launch {
            beginLoading()
            errorMessage = null
            hasAuthenticationError = false
            try {
                repository.verifyAuthentication()
                if (!isCurrentSubscriptionLoad(requestVersion)) {
                    return@launch
                }
                isLoggedIn = true
                hasAuthenticationError = false
                val loadedSubscriptions = repository.listSubscriptions(CACHE_TTL_MILLIS)
                if (!isCurrentSubscriptionLoad(requestVersion)) {
                    return@launch
                }
                allSubscriptions = loadedSubscriptions
                loadExportTargets(loadedSubscriptions, requestVersion)
                if (!isCurrentSubscriptionLoad(requestVersion)) {
                    return@launch
                }
                val lastSubscriptionId = repository.getLastSubscriptionId()
                val restoredSubscription = subscriptions.firstOrNull { it.id == lastSubscriptionId }
                if (restoredSubscription != null) {
                    selectedSubscription = restoredSubscription
                    allVaults = emptyList()
                    loadVaults(restoredSubscription)
                }
            } catch (e: AzureAuthenticationException) {
                if (!isCurrentSubscriptionLoad(requestVersion)) {
                    return@launch
                }
                presentAuthenticationError("Authentication failed. Run 'az login'", e)
            } catch (e: IOException) {
                if (!isCurrentSubscriptionLoad(requestVersion)) {
                    return@launch
                }
                presentError("Network error while loading subscriptions", e)
            } catch (e: IllegalStateException) {
                if (!isCurrentSubscriptionLoad(requestVersion)) {
                    return@launch
                }
                presentError("Unable to load subscriptions", e)
            } finally {
                endLoading()
            }
        }
    }

    fun onSubscriptionSelected(subscription: Subscription) {
        selectedSubscription = subscription
        repository.setLastSubscriptionId(subscription.id)
        allVaults = emptyList()
        loadVaults(subscription)
    }

    fun onVaultSelected(vault: KeyVault) {
        selectedVault = vault
        selectedSecretIds = emptySet()
        exportCommands = ""
        selectedSubscription?.let { subscription ->
            repository.setLastVaultName(subscription.id, vault.name)
        }
        loadSecrets(vault)
    }

    fun isSecretSelected(secret: SecretRef): Boolean = secret.id in selectedSecretIds

    fun onSecretSelectionChanged(secret: SecretRef, selected: Boolean) {
        selectedSecretIds = if (selected) {
            selectedSecretIds + secret.id
        } else {
            selectedSecretIds - secret.id
        }
        exportCommands = ""
    }

    fun selectAllFilteredSecrets() {
        selectedSecretIds = selectedSecretIds + filteredSecrets.map { it.id }
        exportCommands = ""
    }

    fun clearSecretSelection() {
        selectedSecretIds = emptySet()
        exportCommands = ""
    }

    fun openExportTargetDialog() {
        isExportTargetDialogOpen = true
        exportCommands = ""
    }

    fun dismissExportTargetDialog() {
        isExportTargetDialogOpen = false
    }

    fun isExportTargetSelected(target: ExportTargetVault): Boolean = exportTargetKey(target) in selectedExportTargetKeys

    fun onExportTargetSelectionChanged(target: ExportTargetVault, selected: Boolean) {
        val targetKey = exportTargetKey(target)
        selectedExportTargetKeys = if (selected) {
            selectedExportTargetKeys + targetKey
        } else {
            selectedExportTargetKeys - targetKey
        }
        exportCommands = ""
    }

    fun dismissExportCommandsDialog() {
        exportCommands = ""
    }

    fun generateExportCommands() {
        val targets = selectedExportTargets
        if (targets.isEmpty()) {
            errorMessage = "Select at least one target vault for export."
            return
        }
        if (selectedSecretIds.isEmpty()) {
            errorMessage = "Select at least one secret to export."
            return
        }

        isExportTargetDialogOpen = false
        val requestVersion = ++exportCommandLoadVersion
        scope.launch {
            beginLoading()
            errorMessage = null
            try {
                val selectedSecrets = secrets.filter { it.id in selectedSecretIds }.sortedBy { it.name.lowercase() }
                val selectedSecretValues = selectedSecrets.map { repository.getSecret(it) }
                if (!isCurrentExportCommandLoad(requestVersion)) {
                    return@launch
                }
                exportCommands = exportCommandBuilder.build(targets, selectedSecretValues)
            } catch (e: AzureAuthenticationException) {
                if (!isCurrentExportCommandLoad(requestVersion)) {
                    return@launch
                }
                presentAuthenticationError("Authentication failed. Run 'az login'", e)
            } catch (e: IOException) {
                if (!isCurrentExportCommandLoad(requestVersion)) {
                    return@launch
                }
                presentError("Network error while generating export commands", e)
            } catch (e: IllegalStateException) {
                if (!isCurrentExportCommandLoad(requestVersion)) {
                    return@launch
                }
                presentError("Unable to generate export commands", e)
            } finally {
                endLoading()
            }
        }
    }

    fun loadSecretValue(secret: SecretRef) {
        val requestVersion = ++secretValueLoadVersion
        scope.launch {
            beginLoading()
            errorMessage = null
            try {
                val loadedSecret = repository.getSecret(secret)
                if (!isCurrentSecretValueLoad(requestVersion)) {
                    return@launch
                }
                dialogSecret = loadedSecret
            } catch (e: AzureAuthenticationException) {
                if (!isCurrentSecretValueLoad(requestVersion)) {
                    return@launch
                }
                presentAuthenticationError("Authentication failed. Run 'az login'", e)
            } catch (e: IOException) {
                if (!isCurrentSecretValueLoad(requestVersion)) {
                    return@launch
                }
                presentError("Network error while loading secret value", e)
            } catch (e: IllegalStateException) {
                if (!isCurrentSecretValueLoad(requestVersion)) {
                    return@launch
                }
                presentError("Unable to load secret value", e)
            } finally {
                endLoading()
            }
        }
    }

    fun dismissSecretDialog() {
        dialogSecret = null
    }

    private fun loadVaults(subscription: Subscription) {
        val requestVersion = ++vaultLoadVersion
        scope.launch {
            beginLoading()
            errorMessage = null
            selectedVault = null
            allVaults = emptyList()
            secrets = emptyList()
            selectedSecretIds = emptySet()
            exportCommands = ""
            try {
                val loadedVaults = getVaultsForSubscription(subscription.id)
                if (!isCurrentVaultLoad(requestVersion)) {
                    return@launch
                }
                allVaults = loadedVaults
                val lastVaultName = repository.getLastVaultName(subscription.id)
                val restoredVault = vaults.firstOrNull { it.name == lastVaultName }
                if (restoredVault != null) {
                    selectedVault = restoredVault
                    loadSecrets(restoredVault)
                }
            } catch (e: AzureAuthenticationException) {
                if (!isCurrentVaultLoad(requestVersion)) {
                    return@launch
                }
                presentAuthenticationError("Authentication failed. Run 'az login'", e)
            } catch (e: IOException) {
                if (!isCurrentVaultLoad(requestVersion)) {
                    return@launch
                }
                presentError("Network error while loading key vaults", e)
            } catch (e: IllegalStateException) {
                if (!isCurrentVaultLoad(requestVersion)) {
                    return@launch
                }
                presentError("Unable to load key vaults", e)
            } finally {
                endLoading()
            }
        }
    }

    private fun presentError(prefix: String, throwable: Throwable) {
        errorMessage = "$prefix: ${throwable.message ?: throwable::class.simpleName}"
    }

    private fun presentAuthenticationError(prefix: String, throwable: Throwable) {
        isLoggedIn = false
        hasAuthenticationError = true
        presentError(prefix, throwable)
    }

    private suspend fun loadExportTargets(allSubscriptions: List<Subscription>, requestVersion: Int) {
        val loadedTargets = mutableListOf<ExportTargetVault>()
        for (subscription in allSubscriptions) {
            val subscriptionVaults = getVaultsForSubscription(subscription.id)
            if (!isCurrentSubscriptionLoad(requestVersion)) {
                return
            }
            loadedTargets += subscriptionVaults.map { vault ->
                ExportTargetVault(
                    subscriptionId = subscription.id,
                    subscriptionName = subscription.name,
                    vault = vault
                )
            }
        }
        val sortedTargets = loadedTargets.sortedWith(
            compareBy<ExportTargetVault> { it.vault.name.lowercase() }
                .thenBy { it.subscriptionName.lowercase() }
        )
        if (!isCurrentSubscriptionLoad(requestVersion)) {
            return
        }
        allExportTargets = sortedTargets
        applyVaultVisibility()
    }

    private suspend fun getVaultsForSubscription(subscriptionId: String): List<KeyVault> =
        repository.listVaults(subscriptionId, CACHE_TTL_MILLIS)

    private fun loadSecrets(vault: KeyVault) {
        val requestVersion = ++secretListLoadVersion
        val cachedSecrets = repository.getCachedSecrets(vault.vaultUri, CACHE_TTL_MILLIS)
        if (cachedSecrets != null) {
            secrets = cachedSecrets
            selectedSecretIds = selectedSecretIds.intersect(cachedSecrets.mapTo(mutableSetOf()) { it.id })
            exportCommands = ""
        } else {
            secrets = emptyList()
            selectedSecretIds = emptySet()
            exportCommands = ""
        }

        scope.launch {
            beginLoading()
            errorMessage = null
            try {
                val loadedSecrets = repository.refreshSecrets(vault.vaultUri)
                if (!isCurrentSecretListLoad(requestVersion)) {
                    return@launch
                }
                secrets = loadedSecrets
                selectedSecretIds = selectedSecretIds.intersect(loadedSecrets.mapTo(mutableSetOf()) { it.id })
                exportCommands = ""
            } catch (e: AzureAuthenticationException) {
                if (!isCurrentSecretListLoad(requestVersion)) {
                    return@launch
                }
                presentAuthenticationError("Authentication failed. Run 'az login'", e)
            } catch (e: IOException) {
                if (!isCurrentSecretListLoad(requestVersion)) {
                    return@launch
                }
                presentError("Network error while loading secrets", e)
            } catch (e: IllegalStateException) {
                if (!isCurrentSecretListLoad(requestVersion)) {
                    return@launch
                }
                presentError("Unable to load secrets", e)
            } finally {
                endLoading()
            }
        }
    }

    private fun applyVaultVisibility() {
        val visibleSubscriptions = subscriptions
        if (selectedSubscription !in visibleSubscriptions) {
            selectedSubscription = null
            selectedVault = null
            allVaults = emptyList()
            secrets = emptyList()
            selectedSecretIds = emptySet()
            exportCommands = ""
        } else {
            val subscriptionId = selectedSubscription?.id
            if (subscriptionId != null && selectedVault?.let { !isVaultVisible(subscriptionId, it) } == true) {
                selectedVault = null
                secrets = emptyList()
                selectedSecretIds = emptySet()
                exportCommands = ""
            }
        }

        val visibleTargets = exportTargets
        selectedExportTargetKeys = selectedExportTargetKeys.intersect(visibleTargets.mapTo(mutableSetOf()) { exportTargetKey(it) })
    }

    private fun isVaultVisible(subscriptionId: String, vault: KeyVault): Boolean =
        vaultVisibilityKey(subscriptionId, vault) !in hiddenVaultKeys

    private fun vaultVisibilityKey(subscriptionId: String, vault: KeyVault): String =
        "$subscriptionId|${vault.vaultUri}"

    private fun exportTargetKey(target: ExportTargetVault): String = vaultVisibilityKey(target.subscriptionId, target.vault)

    private fun beginLoading() {
        activeLoadCount += 1
        isLoading = true
    }

    private fun endLoading() {
        activeLoadCount = (activeLoadCount - 1).coerceAtLeast(0)
        isLoading = activeLoadCount > 0
    }

    private fun isCurrentSubscriptionLoad(requestVersion: Int): Boolean = requestVersion == subscriptionLoadVersion

    private fun isCurrentVaultLoad(requestVersion: Int): Boolean = requestVersion == vaultLoadVersion

    private fun isCurrentSecretListLoad(requestVersion: Int): Boolean = requestVersion == secretListLoadVersion

    private fun isCurrentSecretValueLoad(requestVersion: Int): Boolean = requestVersion == secretValueLoadVersion

    private fun isCurrentExportCommandLoad(requestVersion: Int): Boolean = requestVersion == exportCommandLoadVersion

    companion object {
        private const val CACHE_TTL_MILLIS = 7L * 24L * 60L * 60L * 1000L
    }
}
