package dk.kodeologi.azure.keyvault.viewer.data

import dk.kodeologi.azure.keyvault.viewer.model.ExportTargetVault
import dk.kodeologi.azure.keyvault.viewer.model.KeyVault
import dk.kodeologi.azure.keyvault.viewer.model.SecretValue
import kotlin.test.Test
import kotlin.test.assertEquals

class ExportCommandBuilderTest {
    @Test
    fun `build adds subscription comment before each target vault group`() {
        val builder = ExportCommandBuilder()

        val targets = listOf(
            ExportTargetVault(
                subscriptionId = "sub-1",
                subscriptionName = "Engineering",
                vault = KeyVault(name = "target-a", vaultUri = "https://target-a.vault.azure.net/")
            ),
            ExportTargetVault(
                subscriptionId = "sub-2",
                subscriptionName = "Operations",
                vault = KeyVault(name = "target-b", vaultUri = "https://target-b.vault.azure.net/")
            )
        )
        val secretValues = listOf(
            SecretValue(name = "db-password", value = "p@ss'word value")
        )

        assertEquals(
            """
            # subscription: Engineering
            az keyvault secret set --vault-name 'target-a' --name 'db-password' --value 'p@ss'"'"'word value'
            # subscription: Operations
            az keyvault secret set --vault-name 'target-b' --name 'db-password' --value 'p@ss'"'"'word value'
            """.trimIndent(),
            builder.build(targets, secretValues)
        )
    }
}
