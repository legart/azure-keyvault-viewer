package dk.kodeologi.azure.keyvault.viewer.data

import dk.kodeologi.azure.keyvault.viewer.auth.AzureTokenProvider
import dk.kodeologi.azure.keyvault.viewer.model.KeyVault
import dk.kodeologi.azure.keyvault.viewer.model.SecretRef
import dk.kodeologi.azure.keyvault.viewer.model.Subscription
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class AzureRestClientTest {
    @Test
    fun `list subscriptions filters blank ids and sorts results`() = runTest {
        val tokenProvider = mockk<AzureTokenProvider>()
        coEvery { tokenProvider.token(any()) } returns "test-token"
        val client = AzureRestClient(
            tokenProvider = tokenProvider,
            httpClient = fakeHttpClient(
                mapOf(
                    "https://management.azure.com/subscriptions?api-version=2020-01-01" to MockResponse(
                        body = """
                            {
                              "value": [
                                { "subscriptionId": "", "displayName": "Ignored" },
                                { "subscriptionId": "sub-b", "displayName": "Beta" },
                                { "subscriptionId": "sub-a", "displayName": "" }
                              ]
                            }
                        """.trimIndent()
                    )
                )
            )
        )

        assertEquals(
            listOf(
                Subscription(id = "sub-b", name = "Beta"),
                Subscription(id = "sub-a", name = "sub-a")
            ),
            client.listSubscriptions()
        )
    }

    @Test
    fun `list vaults follows nextLink pagination and sorts results`() = runTest {
        val tokenProvider = mockk<AzureTokenProvider>()
        coEvery { tokenProvider.token(any()) } returns "test-token"
        val client = AzureRestClient(
            tokenProvider = tokenProvider,
            httpClient = fakeHttpClient(
                mapOf(
                    "https://management.azure.com/subscriptions/sub-1/providers/Microsoft.KeyVault/vaults?api-version=2023-07-01" to MockResponse(
                        body = """
                            {
                              "value": [
                                {
                                  "name": "beta-vault",
                                  "properties": { "vaultUri": "https://beta.vault.azure.net/" }
                                }
                              ],
                              "nextLink": "https://example.test/page-2"
                            }
                        """.trimIndent()
                    ),
                    "https://example.test/page-2" to MockResponse(
                        body = """
                            {
                              "value": [
                                {
                                  "name": "alpha-vault",
                                  "properties": { "vaultUri": "https://alpha.vault.azure.net/" }
                                }
                              ]
                            }
                        """.trimIndent()
                    )
                )
            )
        )

        assertEquals(
            listOf(
                KeyVault(name = "alpha-vault", vaultUri = "https://alpha.vault.azure.net/"),
                KeyVault(name = "beta-vault", vaultUri = "https://beta.vault.azure.net/")
            ),
            client.listVaults("sub-1")
        )
    }

    @Test
    fun `get secret throws for blank secret values`() = runTest {
        val tokenProvider = mockk<AzureTokenProvider>()
        coEvery { tokenProvider.token(any()) } returns "test-token"
        val secret = SecretRef(name = "db-password", id = "https://example.vault.azure.net/secrets/db-password")
        val client = AzureRestClient(
            tokenProvider = tokenProvider,
            httpClient = fakeHttpClient(
                mapOf(
                    "${secret.id}?api-version=7.4" to MockResponse(body = """{ "value": "" }""")
                )
            )
        )

        val error = assertFailsWith<IllegalStateException> { client.getSecret(secret) }

        assertEquals("No secret value returned for db-password.", error.message)
    }

    @Test
    fun `get secret surfaces http failures`() = runTest {
        val tokenProvider = mockk<AzureTokenProvider>()
        coEvery { tokenProvider.token(any()) } returns "test-token"
        val secret = SecretRef(name = "db-password", id = "https://example.vault.azure.net/secrets/db-password")
        val client = AzureRestClient(
            tokenProvider = tokenProvider,
            httpClient = fakeHttpClient(
                mapOf(
                    "${secret.id}?api-version=7.4" to MockResponse(
                        code = 500,
                        body = """{ "error": "boom" }"""
                    )
                )
            )
        )

        val error = assertFailsWith<IllegalStateException> { client.getSecret(secret) }

        assertEquals(
            "Request failed (500) for ${secret.id}?api-version=7.4: { \"error\": \"boom\" }",
            error.message
        )
    }

    private fun fakeHttpClient(responses: Map<String, MockResponse>): OkHttpClient {
        val jsonMediaType = "application/json".toMediaType()
        return OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val response = responses[request.url.toString()]
                    ?: error("Unexpected request URL: ${request.url}")
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(response.code)
                    .message(if (response.code in 200..299) "OK" else "Error")
                    .body(response.body.toResponseBody(jsonMediaType))
                    .build()
            })
            .build()
    }

    private data class MockResponse(
        val code: Int = 200,
        val body: String
    )
}
