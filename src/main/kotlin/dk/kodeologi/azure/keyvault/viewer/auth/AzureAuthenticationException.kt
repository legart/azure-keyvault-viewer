package dk.kodeologi.azure.keyvault.viewer.auth

class AzureAuthenticationException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
