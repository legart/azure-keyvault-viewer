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
                add("# subscription: ${target.subscriptionName}")
                commandsForTarget(target, secretValues).forEach(::add)
            }
        }
        return commands.joinToString(separator = "\n")
    }

    private fun commandsForTarget(
        target: ExportTargetVault,
        secretValues: List<SecretValue>
    ): List<String> = secretValues.map { secretValue ->
        "az keyvault secret set --vault-name ${shellQuote(target.vault.name)} --name ${shellQuote(secretValue.name)} --value ${shellQuote(secretValue.value)}"
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"
}
