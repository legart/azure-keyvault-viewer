# Azure Key Vault Viewer

Azure Key Vault Viewer is a Kotlin/Compose Desktop application for browsing Azure Key Vaults and secrets, then generating `az keyvault secret set` commands to copy selected secrets into another vault.

The app is aimed at interactive inspection and export preparation. It does not directly write secrets into target vaults on your behalf; instead, it builds shell-safe Azure CLI commands from the secrets you select.

## Features

- Browse Azure subscriptions and Key Vaults from a desktop UI
- List secrets for a selected vault with client-side filtering
- Preserve selected secrets when the secret list refreshes and those secrets still exist
- Generate `az keyvault secret set` commands for one or more target vaults
- Copy secret values or generated commands to the clipboard
- Automatically clear copied sensitive clipboard content after 30 seconds if it has not changed
- Cache subscriptions, vaults, and secrets locally for faster reloads
- Remember the last selected subscription, vault, and window size

## Requirements

- Linux, macOS, or Windows supported by Compose Desktop
- Azure CLI installed
- An Azure login session available via:

```bash
az login
```

- Access to the Azure subscriptions, Key Vaults, and secrets you want to browse

## Getting started

Clone the repository and run the desktop app:

```bash
./gradlew run
```

On first use:

1. Sign in with `az login`
2. Open the app
3. Choose a subscription
4. Choose a source Key Vault
5. Select one or more secrets
6. Choose one or more target vaults
7. Generate and copy the resulting Azure CLI commands

## Build and test

Run the full build:

```bash
./gradlew build
```

Run tests only:

```bash
./gradlew test
```

Run a single test class:

```bash
./gradlew test --tests 'dk.kodeologi.azure.keyvault.viewer.ExampleTest'
```

Run a single test method:

```bash
./gradlew test --tests 'dk.kodeologi.azure.keyvault.viewer.ExampleTest.someMethod'
```

## How it works

The application authenticates using Azure CLI credentials through the Azure Identity SDK. It calls Azure Management and Key Vault REST APIs directly using OkHttp and Jackson.

The app loads subscriptions and vaults, lets you inspect secret names, and lazily fetches secret values only when needed for viewing or export command generation.

Generated commands are shell-quoted before being displayed so names and values are safe to paste into a shell command line.

## Project structure

- `src/main/kotlin/dk/kodeologi/azure/keyvault/viewer/Main.kt`
  - Desktop bootstrap and composition root
  - Creates the repository, command builder, and UI state
  - Restores and persists window size

- `src/main/kotlin/dk/kodeologi/azure/keyvault/viewer/ui/`
  - Compose desktop UI
  - `VaultViewerScreen.kt` contains the main screen and dialogs
  - `SettingsWindow.kt` contains the settings UI
  - `VaultViewerState.kt` coordinates UI state and async workflows
  - `VaultViewerTheme.kt` contains Material 3 theme setup

- `src/main/kotlin/dk/kodeologi/azure/keyvault/viewer/data/`
  - `AzureRestClient.kt` talks to Azure REST APIs
  - `VaultViewerRepository.kt` handles cached data access and preference-backed restoration
  - `SelectionPreferences.kt` stores cached data and UI preferences
  - `ExportCommandBuilder.kt` builds shell-safe Azure CLI commands

- `src/main/kotlin/dk/kodeologi/azure/keyvault/viewer/auth/`
  - Azure CLI token acquisition

- `src/main/kotlin/dk/kodeologi/azure/keyvault/viewer/model/`
  - Shared models used across the app

- `src/test/kotlin/`
  - JVM unit tests for state, preferences, and Azure client behavior

## Notes

- The UI uses Material 3.
- Data is cached in `java.util.prefs.Preferences`.
- Async UI loads are guarded so stale results do not overwrite newer selections.
- This project currently focuses on desktop usage rather than a packaged installer workflow, though Compose packaging tasks are available through Gradle.

## Security considerations

- Secret values are only fetched when necessary.
- Copied secret values and generated commands are treated as sensitive clipboard content.
- Clipboard content copied from the app is cleared after 30 seconds if the clipboard content still matches what the app copied.

## License

This project is licensed under the MIT License. See `LICENSE` for details.

## Disclaimer

**Use at your own risk.** These tools and scripts are provided "AS IS" and without any warranties. Interacting with Microsoft Azure can result in data modifications, deletions, or unexpected cloud usage charges.

The author(s) of this repository are not responsible for any data loss, downtime, or financial costs incurred as a result of using this software. Always review the code, test thoroughly in a safe/development environment, and ensure you understand what the tools are doing before running them in a production environment.

*Note: This project is an independent open-source initiative and is not affiliated with, endorsed by, or sponsored by Microsoft Corporation.*