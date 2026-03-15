package dk.kodeologi.azure.keyvault.viewer.data

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dk.kodeologi.azure.keyvault.viewer.model.KeyVault
import dk.kodeologi.azure.keyvault.viewer.model.SecretRef
import dk.kodeologi.azure.keyvault.viewer.model.Subscription
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.prefs.Preferences

class SelectionPreferences(
    private val prefs: Preferences = Preferences.userRoot().node("dk/kodeologi/azure/keyvault/viewer"),
    private val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule()
) {

    fun getLastSubscriptionId(): String? = prefs.get("lastSubscriptionId", null)?.takeIf { it.isNotBlank() }

    fun setLastSubscriptionId(subscriptionId: String) {
        prefs.put("lastSubscriptionId", subscriptionId)
    }

    fun getLastVaultName(subscriptionId: String): String? =
        prefs.get("lastVault.$subscriptionId", null)?.takeIf { it.isNotBlank() }

    fun setLastVaultName(subscriptionId: String, vaultName: String) {
        prefs.put("lastVault.$subscriptionId", vaultName)
    }

    fun getWindowSizeDp(): Pair<Float, Float>? {
        val width = prefs.getDouble("windowWidthDp", -1.0)
        val height = prefs.getDouble("windowHeightDp", -1.0)
        return if (width > 0.0 && height > 0.0) width.toFloat() to height.toFloat() else null
    }

    fun setWindowSizeDp(width: Float, height: Float) {
        prefs.putDouble("windowWidthDp", width.toDouble())
        prefs.putDouble("windowHeightDp", height.toDouble())
    }

    fun getCachedSubscriptions(maxAgeMillis: Long, nowEpochMillis: Long = System.currentTimeMillis()): List<Subscription>? {
        val json = prefs.get(CACHED_SUBSCRIPTIONS_KEY, null) ?: return null
        val payload = objectMapper.readValue(json, CachedSubscriptions::class.java)
        return payload.subscriptions.takeIf { nowEpochMillis - payload.cachedAtEpochMillis <= maxAgeMillis }
    }

    fun setCachedSubscriptions(subscriptions: List<Subscription>, nowEpochMillis: Long = System.currentTimeMillis()) {
        val payload = CachedSubscriptions(cachedAtEpochMillis = nowEpochMillis, subscriptions = subscriptions)
        prefs.put(CACHED_SUBSCRIPTIONS_KEY, objectMapper.writeValueAsString(payload))
    }

    fun getCachedVaults(
        subscriptionId: String,
        maxAgeMillis: Long,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): List<KeyVault>? {
        val json = prefs.get(cachedVaultsKey(subscriptionId), null) ?: return null
        val payload = objectMapper.readValue(json, CachedVaults::class.java)
        return payload.vaults.takeIf { nowEpochMillis - payload.cachedAtEpochMillis <= maxAgeMillis }
    }

    fun setCachedVaults(subscriptionId: String, vaults: List<KeyVault>, nowEpochMillis: Long = System.currentTimeMillis()) {
        val payload = CachedVaults(cachedAtEpochMillis = nowEpochMillis, vaults = vaults)
        prefs.put(cachedVaultsKey(subscriptionId), objectMapper.writeValueAsString(payload))
    }

    fun getCachedSecrets(
        vaultUri: String,
        maxAgeMillis: Long,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): List<SecretRef>? {
        val json = getChunkedPreference(cachedSecretsKey(vaultUri)) ?: return null
        val payload = objectMapper.readValue(json, CachedSecrets::class.java)
        return payload.secrets.takeIf { nowEpochMillis - payload.cachedAtEpochMillis <= maxAgeMillis }
    }

    fun setCachedSecrets(vaultUri: String, secrets: List<SecretRef>, nowEpochMillis: Long = System.currentTimeMillis()) {
        val payload = CachedSecrets(cachedAtEpochMillis = nowEpochMillis, secrets = secrets)
        setChunkedPreference(cachedSecretsKey(vaultUri), objectMapper.writeValueAsString(payload))
    }

    fun getHiddenVaultKeys(): Set<String> {
        val json = prefs.get(HIDDEN_VAULT_KEYS_KEY, null) ?: return emptySet()
        return objectMapper.readValue(json, Array<String>::class.java)
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun setHiddenVaultKeys(hiddenVaultKeys: Set<String>) {
        val normalizedKeys = hiddenVaultKeys
            .filter { it.isNotBlank() }
            .toSortedSet()
        if (normalizedKeys.isEmpty()) {
            prefs.remove(HIDDEN_VAULT_KEYS_KEY)
            return
        }
        prefs.put(HIDDEN_VAULT_KEYS_KEY, objectMapper.writeValueAsString(normalizedKeys))
    }

    private fun cachedVaultsKey(subscriptionId: String): String = "$CACHED_VAULTS_PREFIX$subscriptionId"

    private fun cachedSecretsKey(vaultUri: String): String = "$CACHED_SECRETS_PREFIX${hashedCacheKey(vaultUri)}"

    private fun hashedCacheKey(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest
            .take(HASH_KEY_BYTES)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun getChunkedPreference(baseKey: String): String? {
        val chunkCount = prefs.getInt("$baseKey.count", -1)
        if (chunkCount < 0) {
            return prefs.get(baseKey, null)
        }
        return buildString {
            repeat(chunkCount) { index ->
                val chunk = prefs.get("$baseKey.$index", null)
                    ?: throw IllegalStateException("Missing cached secret chunk $index for $baseKey")
                append(chunk)
            }
        }
    }

    private fun setChunkedPreference(baseKey: String, value: String) {
        val previousChunkCount = prefs.getInt("$baseKey.count", 0)
        val chunks = value.chunked(PREFERENCE_VALUE_CHUNK_SIZE)
        prefs.putInt("$baseKey.count", chunks.size)
        chunks.forEachIndexed { index, chunk ->
            prefs.put("$baseKey.$index", chunk)
        }
        for (index in chunks.size until previousChunkCount) {
            prefs.remove("$baseKey.$index")
        }
        prefs.remove(baseKey)
    }

    data class CachedSubscriptions(
        val cachedAtEpochMillis: Long,
        val subscriptions: List<Subscription>
    )

    data class CachedVaults(
        val cachedAtEpochMillis: Long,
        val vaults: List<KeyVault>
    )

    data class CachedSecrets(
        val cachedAtEpochMillis: Long,
        val secrets: List<SecretRef>
    )

    companion object {
        private const val CACHED_SUBSCRIPTIONS_KEY = "cache.subscriptions"
        private const val CACHED_VAULTS_PREFIX = "cache.vaults."
        private const val CACHED_SECRETS_PREFIX = "cache.secrets."
        private const val HIDDEN_VAULT_KEYS_KEY = "hiddenVaultKeys"
        private const val PREFERENCE_VALUE_CHUNK_SIZE = 3000
        private const val HASH_KEY_BYTES = 16
    }
}
