package dk.kodeologi.azure.keyvault.viewer.auth

import com.azure.core.credential.TokenRequestContext
import com.azure.core.exception.ClientAuthenticationException
import com.azure.identity.AzureCliCredentialBuilder
import com.azure.identity.CredentialUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

class AzureTokenProvider {
    private val credential = AzureCliCredentialBuilder().build()

    suspend fun token(scope: String): String = withContext(Dispatchers.IO) {
        try {
            val token = credential
                .getToken(TokenRequestContext().addScopes(scope))
                .block(Duration.ofSeconds(30))
                ?.token
            token ?: throw AzureAuthenticationException("Unable to acquire Azure token. Run 'az login'.")
        } catch (e: CredentialUnavailableException) {
            throw AzureAuthenticationException("Unable to acquire Azure token. Run 'az login'.", e)
        } catch (e: ClientAuthenticationException) {
            throw AzureAuthenticationException("Unable to acquire Azure token. Run 'az login'.", e)
        }
    }
}
