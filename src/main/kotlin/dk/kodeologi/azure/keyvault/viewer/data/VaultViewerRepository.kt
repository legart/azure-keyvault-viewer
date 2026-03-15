package dk.kodeologi.azure.keyvault.viewer.data

import dk.kodeologi.azure.keyvault.viewer.model.KeyVault
import dk.kodeologi.azure.keyvault.viewer.model.SecretRef
import dk.kodeologi.azure.keyvault.viewer.model.SecretValue
import dk.kodeologi.azure.keyvault.viewer.model.Subscription

class VaultViewerRepository(
    private val client: AzureRestClient,
    private val selectionPreferences: SelectionPreferences
) {
    suspend fun verifyAuthentication() {
        client.verifyAuthentication()
    }

    suspend fun listSubscriptions(cacheTtlMillis: Long): List<Subscription> {
        return selectionPreferences.getCachedSubscriptions(cacheTtlMillis)
            ?: client.listSubscriptions().also { selectionPreferences.setCachedSubscriptions(it) }
    }

    suspend fun listVaults(subscriptionId: String, cacheTtlMillis: Long): List<KeyVault> {
        return selectionPreferences.getCachedVaults(subscriptionId, cacheTtlMillis)
            ?: client.listVaults(subscriptionId).also { selectionPreferences.setCachedVaults(subscriptionId, it) }
    }

    fun getCachedSecrets(vaultUri: String, cacheTtlMillis: Long): List<SecretRef>? {
        return selectionPreferences.getCachedSecrets(vaultUri, cacheTtlMillis)
    }

    suspend fun refreshSecrets(vaultUri: String): List<SecretRef> {
        return client.listSecrets(vaultUri).also { selectionPreferences.setCachedSecrets(vaultUri, it) }
    }

    suspend fun getSecret(secret: SecretRef): SecretValue = client.getSecret(secret)

    fun getLastSubscriptionId(): String? = selectionPreferences.getLastSubscriptionId()

    fun setLastSubscriptionId(subscriptionId: String) {
        selectionPreferences.setLastSubscriptionId(subscriptionId)
    }

    fun getLastVaultName(subscriptionId: String): String? = selectionPreferences.getLastVaultName(subscriptionId)

    fun setLastVaultName(subscriptionId: String, vaultName: String) {
        selectionPreferences.setLastVaultName(subscriptionId, vaultName)
    }

    fun getHiddenVaultKeys(): Set<String> = selectionPreferences.getHiddenVaultKeys()

    fun setHiddenVaultKeys(hiddenVaultKeys: Set<String>) {
        selectionPreferences.setHiddenVaultKeys(hiddenVaultKeys)
    }
}
