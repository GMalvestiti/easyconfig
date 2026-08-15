# Template

## Maven Central Publishing Guide

This repository contains the setup guide and configurations used to successfully sign and publish Minecraft mod artifacts to Maven Central using Windows, Chocolatey, GnuPG, and the `gradle-maven-publish-plugin`.

---

##  Environment Prerequisites

Instead of using WSL, GnuPG must be installed natively on the Windows host machine via Chocolatey to generate binary-compatible keyrings.

```powershell
# Run PowerShell as Administrator
choco install gnupg -y
```
*Note: Restart your PowerShell terminal after installation to update your system PATH variables.*

---

## GPG Key Management Workflow

### 1. Generate a New Keypair
Generate a robust signature key pair directly inside the Windows environment:
```powershell
gpg --full-generate-key
```
*   **Recommended Settings**: Select `(1) RSA and RSA` with `4096` bits (or option `9` for ECC/Curve25519 depending on your ecosystem requirements).

### 2. Locate Your Key ID
List your secret keys in long-hex format to grab your standard Key ID:
```powershell
gpg --list-secret-keys --keyid-format LONG
```
Look for the `sec` line. In the example below, the complete ID is `12345678ABCDEFGH`:
```text
sec   rsa4096/12345678ABCDEFGH 2026-02-29 [SC]
```

### 3. Generate a True Legacy Binary Keyring File
Gradle's publication signing engine requires an unarmored, **pure binary** `.gpg` file.

**Warning:** Avoid using the PowerShell redirection operator `>` (e.g., `gpg ... > file.gpg`), as it forces a text stream encoding format (UTF-16) that corrupts the PGP database format. Always force direct binary file input/output handling via GnuPG's native `-o` flag:

```powershell
gpg -o C:/Users/<user>/.../secring.gpg --export-secret-keys 12345678ABCDEFGH
```

### 4. Publish Your Key to Global Servers
Maven Central validation protocols will automatically reject uploaded artifacts if the corresponding verification public key cannot be downloaded from public keyservers. Sync your key with the network:

```powershell
gpg --keyserver pgp.mit.edu --send-keys 12345678ABCDEFGH
gpg --keyserver keys.openpgp.org --send-keys 12345678ABCDEFGH
gpg --keyserver keyserver.ubuntu.com --send-keys 12345678ABCDEFGH
```

**Note:** [keys.openpgp.org](https://keys.openpgp.org) requires email verification for public key association.

---

## Configuration Placeholders

### 1. User Gradle Properties (`~/.gradle/gradle.properties`)

Place your automated Sonatype developer tokens and GPG secrets securely in your local machine user home directory (`C:/Users/<user>/.gradle/gradle.properties`).

*Note: For the short-hex `signing.keyId` constraint, supply exclusively the **last 8 characters** of your complete Key ID.*

```properties
# Sonatype User Token Management Credentials
mavenCentralUsername=<your sonatype username>
mavenCentralPassword=<your sonatype password>

# GPG Signature File Configuration
signing.keyId=12345678
signing.password=<your gpg passphrase>
signing.secretKeyRingFile=C:/Users/<user>/.../secring.gpg
```

---

## Execution & Publication Commands

Run the following commands in your PowerShell terminal to test and publish your artifacts to Maven Central:

```powershell
# Step 1: Clean build directories and verify cryptographic signing steps
./gradlew clean signMavenPublication

# Step 2: Publish artifacts to your local Maven repository for testing (check `~/.m2/repository` for output)
./gradlew publishToMavenLocal

# Step 3: Publish and trigger automated release on Sonatype Central Portal
./gradlew publishToMavenCentral
```
