package dk.kodeologi.azure.keyvault.viewer.data

import dk.kodeologi.azure.keyvault.viewer.model.SecretRef
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.util.UUID
import java.util.prefs.Preferences

class SelectionPreferencesTest {
    private lateinit var prefsNode: Preferences
    private lateinit var selectionPreferences: SelectionPreferences

    @BeforeTest
    fun setUp() {
        prefsNode = Preferences.userRoot().node("dk/kodeologi/azure/keyvault/viewer-test/${UUID.randomUUID()}")
        selectionPreferences = SelectionPreferences(prefsNode)
    }

    @AfterTest
    fun tearDown() {
        prefsNode.removeNode()
        prefsNode.flush()
    }

    @Test
    fun `cached secrets round-trip across chunked preference storage`() {
        val now = 1_000L
        val vaultUri = "https://example.vault.azure.net/"
        val secrets = (1..80).map { index ->
            SecretRef(
                name = "secret-$index-${"x".repeat(64)}",
                id = "$vaultUri/secrets/secret-$index"
            )
        }

        selectionPreferences.setCachedSecrets(vaultUri, secrets, now)

        val cacheKeys = prefsNode.keys().filter { it.startsWith("cache.secrets.") }
        assertTrue(cacheKeys.size > 1)
        assertEquals(
            secrets,
            selectionPreferences.getCachedSecrets(vaultUri, maxAgeMillis = 10_000L, nowEpochMillis = now + 500L)
        )
    }

    @Test
    fun `cached secrets expire after ttl`() {
        val now = 2_000L
        val vaultUri = "https://example.vault.azure.net/"
        val secrets = listOf(SecretRef(name = "db-password", id = "$vaultUri/secrets/db-password"))

        selectionPreferences.setCachedSecrets(vaultUri, secrets, now)

        assertNull(selectionPreferences.getCachedSecrets(vaultUri, maxAgeMillis = 100L, nowEpochMillis = now + 101L))
    }

    @Test
    fun `hidden vault keys are normalized and removed when empty`() {
        selectionPreferences.setHiddenVaultKeys(setOf("", "sub-b|vault-b", "sub-a|vault-a", "sub-a|vault-a"))

        assertEquals(setOf("sub-a|vault-a", "sub-b|vault-b"), selectionPreferences.getHiddenVaultKeys())

        selectionPreferences.setHiddenVaultKeys(emptySet())

        assertEquals(emptySet(), selectionPreferences.getHiddenVaultKeys())
        assertFalse(prefsNode.keys().contains("hiddenVaultKeys"))
    }
}
