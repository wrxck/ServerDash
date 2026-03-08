# Security

ServerDash is designed with a security-first approach. All server communication happens over SSH, all sensitive data is encrypted at rest, and no data ever leaves your device except through your own SSH connections. This document describes the security features and design decisions in detail.

---

## Core Principles

- **No telemetry.** ServerDash does not collect, transmit, or share any usage data, analytics, or telemetry. There are no tracking SDKs, no crash reporting services, and no network calls to third-party servers.
- **No analytics.** There is no user behavior tracking of any kind.
- **All data stays on device.** Every piece of data -- server configurations, credentials, metrics, logs -- remains exclusively on your device. The only outbound network traffic is your SSH connections to your own servers.

---

## Database Encryption (SQLCipher)

ServerDash uses Room with SQLCipher to encrypt the entire local database.

### How It Works

- The Room database is wrapped with SQLCipher, which provides transparent AES-256 encryption of the database file.
- The encryption passphrase is derived from the Android Keystore `MasterKey`. This key is hardware-backed on devices that support it, meaning the key material never leaves the secure hardware.
- The database file on disk is fully encrypted. It cannot be read by other apps, file managers, or forensic tools without the Keystore-derived key.

### What Is Stored

- Server connection profiles (hostname, port, username)
- Cached metrics and service state
- Plugin configuration
- App preferences that require structured storage

---

## App Lock

ServerDash supports locking the app behind biometric authentication or device credentials.

### Supported Methods

- **Biometric:** Fingerprint, face recognition, or other biometric methods supported by the device.
- **Device credential:** PIN, pattern, or password as configured in the device's lock screen settings.

### Behavior

When app lock is enabled, the user must authenticate each time the app is brought to the foreground after being backgrounded. This prevents unauthorized access to server connections and credentials if the device is unlocked and unattended.

---

## Privacy Filter System

ServerDash includes a comprehensive privacy filter system with 15 distinct filter types. Privacy filters allow users to redact or mask sensitive information displayed on screen.

### Use Cases

- Hiding IP addresses and hostnames during screen sharing or recording
- Masking usernames and credentials in terminal output
- Redacting service names or configuration values in screenshots
- General-purpose content filtering for demonstrations or presentations

### Design

Each filter type targets a specific category of information. Filters can be toggled individually, giving users fine-grained control over what is visible on screen. Filters operate at the UI layer and do not modify underlying data.

---

## SSH Credential Handling

SSH credentials are treated with particular care:

### In-Memory Storage

- SSH passwords and private key passphrases are held in memory only during active use.
- Credentials are not written to disk in plaintext under any circumstances.

### EncryptedSharedPreferences

- When the user opts to save credentials for convenience, they are stored exclusively through `EncryptedSharedPreferences`.
- EncryptedSharedPreferences encrypts both the preference keys and values using the Android Keystore.
- The encryption scheme uses AES-256-SIV for keys and AES-256-GCM for values.

### Private Keys

- SSH private keys provided by the user are stored encrypted via the same EncryptedSharedPreferences mechanism.
- Keys are decrypted into memory only when needed to establish a connection.

### No Credential Leakage

- Credentials are never logged, included in crash reports (there are none), or transmitted anywhere other than the target SSH server.
- The terminal history does not capture or store password input.

---

## SSH Transport Security

All server communication uses SSH (Secure Shell), which provides:

- **Encryption:** All traffic between the app and the server is encrypted using strong ciphers negotiated during the SSH handshake.
- **Host key verification:** ServerDash verifies the server's host key to protect against man-in-the-middle attacks. Unknown host keys prompt the user for confirmation.
- **Authentication:** Supports both password and public key authentication methods.

SSH connections are managed by the SSHJ library, which implements the SSH-2 protocol.

---

## What ServerDash Does NOT Do

For clarity, here is what ServerDash explicitly avoids:

- **No internet access beyond SSH:** The app makes no HTTP/HTTPS requests to any server other than your configured SSH endpoints.
- **No third-party SDKs:** No Firebase, no Google Analytics, no Crashlytics, no ad networks.
- **No cloud sync:** Server configurations and credentials are never synced to any cloud service.
- **No background data collection:** The app does not run background services that collect or transmit data.
- **No clipboard monitoring:** The app does not read or monitor the system clipboard.
