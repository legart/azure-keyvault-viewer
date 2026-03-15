package dk.kodeologi.azure.keyvault.viewer.data

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dk.kodeologi.azure.keyvault.viewer.auth.AzureTokenProvider
import dk.kodeologi.azure.keyvault.viewer.model.KeyVault
import dk.kodeologi.azure.keyvault.viewer.model.SecretRef
import dk.kodeologi.azure.keyvault.viewer.model.SecretValue
import dk.kodeologi.azure.keyvault.viewer.model.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class AzureRestClient(
    private val tokenProvider: AzureTokenProvider,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule()
) {
    suspend fun verifyAuthentication() {
        tokenProvider.token(MANAGEMENT_SCOPE)
    }

    suspend fun listSubscriptions(): List<Subscription> {
        val token = tokenProvider.token(MANAGEMENT_SCOPE)
        val body = getJson(
            url = "https://management.azure.com/subscriptions?api-version=2020-01-01",
            bearerToken = token
        )
        val result = mutableListOf<Subscription>()
        val values = objectMapper.readTree(body).path("value")
        for (item in values) {
            val id = item.path("subscriptionId").asText()
            val name = item.path("displayName").asText()
            if (id.isNotBlank()) {
                result += Subscription(id = id, name = if (name.isNotBlank()) name else id)
            }
        }
        return result.sortedBy { it.name.lowercase() }
    }

    suspend fun listVaults(subscriptionId: String): List<KeyVault> {
        val token = tokenProvider.token(MANAGEMENT_SCOPE)
        val result = mutableListOf<KeyVault>()
        var nextUrl: String? =
            "https://management.azure.com/subscriptions/$subscriptionId/providers/Microsoft.KeyVault/vaults?api-version=2023-07-01"

        while (!nextUrl.isNullOrBlank()) {
            val body = getJson(
                url = nextUrl,
                bearerToken = token
            )
            val root = objectMapper.readTree(body)
            val values = root.path("value")
            for (item in values) {
                val name = item.path("name").asText()
                val uri = item.path("properties").path("vaultUri").asText()
                if (name.isNotBlank() && uri.isNotBlank()) {
                    result += KeyVault(name = name, vaultUri = uri)
                }
            }
            val nextLinkNode = root.path("nextLink")
            nextUrl = if (nextLinkNode.isMissingNode || nextLinkNode.isNull) {
                null
            } else {
                nextLinkNode.asText().takeIf { it.isNotBlank() && it != "null" }
            }
        }
        return result.sortedBy { it.name.lowercase() }
    }

    suspend fun listSecrets(vaultUri: String): List<SecretRef> {
        val token = tokenProvider.token(VAULT_SCOPE)
        val normalizedUri = if (vaultUri.endsWith('/')) vaultUri else "$vaultUri/"
        val result = mutableListOf<SecretRef>()
        var nextUrl: String? = "${normalizedUri}secrets?api-version=7.4"

        while (!nextUrl.isNullOrBlank()) {
            val body = getJson(
                url = nextUrl,
                bearerToken = token
            )
            val root = objectMapper.readTree(body)
            val values = root.path("value")
            for (item in values) {
                val id = item.path("id").asText()
                val name = id.substringAfterLast('/').substringBefore('?')
                if (id.isNotBlank() && name.isNotBlank()) {
                    result += SecretRef(name = name, id = id)
                }
            }
            val nextLinkNode = root.path("nextLink")
            nextUrl = if (nextLinkNode.isMissingNode || nextLinkNode.isNull) {
                null
            } else {
                nextLinkNode.asText().takeIf { it.isNotBlank() && it != "null" }
            }
        }
        return result.sortedBy { it.name.lowercase() }
    }

    suspend fun getSecret(secret: SecretRef): SecretValue {
        val token = tokenProvider.token(VAULT_SCOPE)
        val body = getJson(
            url = "${secret.id}?api-version=7.4",
            bearerToken = token
        )
        val value = objectMapper.readTree(body).path("value").asText()
        if (value.isBlank()) {
            throw IllegalStateException("No secret value returned for ${secret.name}.")
        }
        return SecretValue(name = secret.name, value = value)
    }

    private suspend fun getJson(url: String, bearerToken: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $bearerToken")
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw IllegalStateException("Request failed (${response.code}) for $url: $body")
            }
            body
        }
    }

    companion object {
        private const val MANAGEMENT_SCOPE = "https://management.azure.com/.default"
        private const val VAULT_SCOPE = "https://vault.azure.net/.default"
    }
}
