package dk.kodeologi.azure.keyvault.viewer.data

import dk.kodeologi.azure.keyvault.viewer.model.ExportTargetVault
import dk.kodeologi.azure.keyvault.viewer.model.SecretValue

class ExportCommandBuilder {
    fun build(
        targets: List<ExportTargetVault>,
        secretValues: List<SecretValue>
    ): String {
        val commands = buildList {
            for (target in targets) {
                for (secretValue in secretValues) {
                    add(
                        "az keyvault secret set --vault-name ${shellQuote(target.vault.name)} --name ${shellQuote(secretValue.name)} --value ${shellQuote(secretValue.value)}"
                    )
                }
            }
        }
        return commands.joinToString(separator = "\n")
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"
}
