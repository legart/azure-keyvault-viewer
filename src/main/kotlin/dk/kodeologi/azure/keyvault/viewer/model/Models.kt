package dk.kodeologi.azure.keyvault.viewer.model

data class Subscription(val id: String, val name: String)
data class KeyVault(val name: String, val vaultUri: String)
data class SecretRef(val name: String, val id: String)
data class SecretValue(val name: String, val value: String)
data class ExportTargetVault(
    val subscriptionId: String,
    val subscriptionName: String,
    val vault: KeyVault
)
