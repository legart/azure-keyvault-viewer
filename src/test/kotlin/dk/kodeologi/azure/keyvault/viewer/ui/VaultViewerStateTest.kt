package dk.kodeologi.azure.keyvault.viewer.ui

import dk.kodeologi.azure.keyvault.viewer.auth.AzureAuthenticationException
import dk.kodeologi.azure.keyvault.viewer.data.AzureRestClient
import dk.kodeologi.azure.keyvault.viewer.data.ExportCommandBuilder
import dk.kodeologi.azure.keyvault.viewer.data.SelectionPreferences
import dk.kodeologi.azure.keyvault.viewer.data.VaultViewerRepository
import dk.kodeologi.azure.keyvault.viewer.model.KeyVault
import dk.kodeologi.azure.keyvault.viewer.model.SecretRef
import dk.kodeologi.azure.keyvault.viewer.model.SecretValue
import dk.kodeologi.azure.keyvault.viewer.model.Subscription
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.UUID
import java.util.prefs.Preferences

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewerStateTest {
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
    fun `loadSubscriptions surfaces authentication errors`() = runTest {
        val client = mockk<AzureRestClient>()
        coEvery { client.verifyAuthentication() } throws AzureAuthenticationException("Login required")
        val repository = VaultViewerRepository(client, selectionPreferences)
        val state = VaultViewerState(repository, ExportCommandBuilder(), this)

        state.loadSubscriptions()
        advanceUntilIdle()

        assertFalse(state.isLoggedIn)
        assertTrue(state.hasAuthenticationError)
        assertFalse(state.isLoading)
        assertEquals(
            "Authentication failed. Run 'az login': Login required",
            state.errorMessage
        )
    }

    @Test
    fun `generateExportCommands builds quoted commands for selected secrets and targets`() = runTest {
        val subscription = Subscription(id = "sub-1", name = "Engineering")
        val sourceVault = KeyVault(name = "source-vault", vaultUri = "https://source.vault.azure.net/")
        val targetVault = KeyVault(name = "target-vault", vaultUri = "https://target.vault.azure.net/")
        val secretRef = SecretRef(name = "db-password", id = "${sourceVault.vaultUri}secrets/db-password")
        val secretValue = SecretValue(name = secretRef.name, value = "p@ss'word value")
        val client = mockk<AzureRestClient>()

        selectionPreferences.setCachedSubscriptions(listOf(subscription))
        selectionPreferences.setCachedVaults(subscription.id, listOf(sourceVault, targetVault))
        selectionPreferences.setCachedSecrets(sourceVault.vaultUri, listOf(secretRef))

        coEvery { client.verifyAuthentication() } returns Unit
        coEvery { client.listSecrets(sourceVault.vaultUri) } returns listOf(secretRef)
        coEvery { client.getSecret(secretRef) } returns secretValue

        val repository = VaultViewerRepository(client, selectionPreferences)
        val state = VaultViewerState(repository, ExportCommandBuilder(), this)

        state.loadSubscriptions()
        advanceUntilIdle()
        state.onSubscriptionSelected(subscription)
        advanceUntilIdle()
        state.onVaultSelected(sourceVault)
        advanceUntilIdle()

        val exportTarget = state.exportTargets.single { it.vault == targetVault }
        state.onSecretSelectionChanged(secretRef, selected = true)
        state.openExportTargetDialog()
        state.onExportTargetSelectionChanged(exportTarget, selected = true)

        state.generateExportCommands()
        advanceUntilIdle()

        assertFalse(state.isExportTargetDialogOpen)
        assertEquals(
            """
            # subscription: Engineering
            az keyvault secret set --vault-name 'target-vault' --name 'db-password' --value 'p@ss'"'"'word value'
            """.trimIndent(),
            state.exportCommands
        )
        coVerify(exactly = 1) { client.getSecret(secretRef) }
    }

    @Test
    fun `onVaultSelected clears prior selection and export commands`() = runTest {
        val subscription = Subscription(id = "sub-1", name = "Engineering")
        val sourceVault = KeyVault(name = "source-vault", vaultUri = "https://source.vault.azure.net/")
        val otherVault = KeyVault(name = "other-vault", vaultUri = "https://other.vault.azure.net/")
        val firstSecret = SecretRef(name = "db-password", id = "${sourceVault.vaultUri}secrets/db-password")
        val client = mockk<AzureRestClient>()

        selectionPreferences.setCachedSubscriptions(listOf(subscription))
        selectionPreferences.setCachedVaults(subscription.id, listOf(sourceVault, otherVault))
        selectionPreferences.setCachedSecrets(sourceVault.vaultUri, listOf(firstSecret))
        selectionPreferences.setCachedSecrets(otherVault.vaultUri, emptyList())

        coEvery { client.verifyAuthentication() } returns Unit
        coEvery { client.listSecrets(sourceVault.vaultUri) } returns listOf(firstSecret)
        coEvery { client.listSecrets(otherVault.vaultUri) } returns emptyList()

        val repository = VaultViewerRepository(client, selectionPreferences)
        val state = VaultViewerState(repository, ExportCommandBuilder(), this)

        state.loadSubscriptions()
        advanceUntilIdle()
        state.onSubscriptionSelected(subscription)
        advanceUntilIdle()
        state.onVaultSelected(sourceVault)
        advanceUntilIdle()
        state.onSecretSelectionChanged(firstSecret, selected = true)
        state.exportCommands = "already generated"

        state.onVaultSelected(otherVault)

        assertEquals(emptySet(), state.selectedSecretIds)
        assertEquals("", state.exportCommands)
        assertEquals(otherVault, state.selectedVault)
    }

    @Test
    fun `stale secret loads do not overwrite the latest vault secrets`() = runTest {
        val subscription = Subscription(id = "sub-1", name = "Engineering")
        val firstVault = KeyVault(name = "first-vault", vaultUri = "https://first.vault.azure.net/")
        val secondVault = KeyVault(name = "second-vault", vaultUri = "https://second.vault.azure.net/")
        val firstSecret = SecretRef(name = "first-secret", id = "${firstVault.vaultUri}secrets/first-secret")
        val secondSecret = SecretRef(name = "second-secret", id = "${secondVault.vaultUri}secrets/second-secret")
        val firstResponse = CompletableDeferred<List<SecretRef>>()
        val secondResponse = CompletableDeferred<List<SecretRef>>()
        val client = mockk<AzureRestClient>()

        selectionPreferences.setCachedSubscriptions(listOf(subscription))
        selectionPreferences.setCachedVaults(subscription.id, listOf(firstVault, secondVault))

        coEvery { client.verifyAuthentication() } returns Unit
        coEvery { client.listSecrets(firstVault.vaultUri) } coAnswers { firstResponse.await() }
        coEvery { client.listSecrets(secondVault.vaultUri) } coAnswers { secondResponse.await() }

        val repository = VaultViewerRepository(client, selectionPreferences)
        val state = VaultViewerState(repository, ExportCommandBuilder(), this)

        state.loadSubscriptions()
        advanceUntilIdle()
        state.onSubscriptionSelected(subscription)
        advanceUntilIdle()

        state.onVaultSelected(firstVault)
        state.onVaultSelected(secondVault)

        secondResponse.complete(listOf(secondSecret))
        advanceUntilIdle()

        assertEquals(secondVault, state.selectedVault)
        assertEquals(listOf(secondSecret), state.secrets)

        firstResponse.complete(listOf(firstSecret))
        advanceUntilIdle()

        assertEquals(secondVault, state.selectedVault)
        assertEquals(listOf(secondSecret), state.secrets)
    }

    @Test
    fun `refresh keeps selected secrets that still exist`() = runTest {
        val subscription = Subscription(id = "sub-1", name = "Engineering")
        val vault = KeyVault(name = "source-vault", vaultUri = "https://source.vault.azure.net/")
        val selectedSecret = SecretRef(name = "keep-me", id = "${vault.vaultUri}secrets/keep-me")
        val removedSecret = SecretRef(name = "drop-me", id = "${vault.vaultUri}secrets/drop-me")
        val refreshedSecrets = listOf(selectedSecret, SecretRef(name = "new-secret", id = "${vault.vaultUri}secrets/new-secret"))
        val refreshResponse = CompletableDeferred<List<SecretRef>>()
        val client = mockk<AzureRestClient>()

        selectionPreferences.setCachedSubscriptions(listOf(subscription))
        selectionPreferences.setCachedVaults(subscription.id, listOf(vault))
        selectionPreferences.setCachedSecrets(vault.vaultUri, listOf(selectedSecret, removedSecret))

        coEvery { client.verifyAuthentication() } returns Unit
        coEvery { client.listSecrets(vault.vaultUri) } coAnswers { refreshResponse.await() }

        val repository = VaultViewerRepository(client, selectionPreferences)
        val state = VaultViewerState(repository, ExportCommandBuilder(), this)

        state.loadSubscriptions()
        runCurrent()
        state.onSubscriptionSelected(subscription)
        runCurrent()
        state.onVaultSelected(vault)
        runCurrent()

        state.onSecretSelectionChanged(selectedSecret, selected = true)
        state.onSecretSelectionChanged(removedSecret, selected = true)

        refreshResponse.complete(refreshedSecrets)
        advanceUntilIdle()

        assertEquals(setOf(selectedSecret.id), state.selectedSecretIds)
        assertEquals(refreshedSecrets, state.secrets)
    }
}
