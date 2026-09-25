# PhoneGuard shared debug signing

PhoneGuard Child (`com.example.phoneguard`) and Parent
(`com.example.phoneguard.parent`) must be signed with the same stable debug
keystore on every development computer.

The keystore itself and its passwords must never be committed to Git.

## Current development key

The current Windows installations were signed with the Windows Android debug
keystore. Keep that exact file as the shared development key so the existing
phones can continue to receive `adb install -r` updates without reinstalling.

On Windows the default file is normally:

```text
%USERPROFILE%\.android\debug.keystore
```

For the standard Android debug keystore the usual values are:

```text
alias: androiddebugkey
store password: android
key password: android
```

Verify those values on the actual file before relying on them.

## local.properties

Add these lines to the repository's existing `local.properties` file.
Use forward slashes in Windows paths because Java properties treat backslashes
as escape characters.

Windows example:

```properties
phoneguard.keystore.file=C:/Users/YOUR_USER/.android/debug.keystore
phoneguard.keystore.password=android
phoneguard.key.alias=androiddebugkey
phoneguard.key.password=android
```

Ubuntu example, after securely copying the SAME Windows keystore file:

```properties
phoneguard.keystore.file=/home/YOUR_USER/.phoneguard/phoneguard-dev.keystore
phoneguard.keystore.password=android
phoneguard.key.alias=androiddebugkey
phoneguard.key.password=android
```

Do not generate a new keystore on Ubuntu. Copy the existing Windows keystore.

## Environment variables

Instead of `local.properties`, the build also accepts:

```text
PHONEGUARD_KEYSTORE_FILE
PHONEGUARD_KEYSTORE_PASSWORD
PHONEGUARD_KEY_ALIAS
PHONEGUARD_KEY_PASSWORD
```

Environment variables take precedence over `local.properties`.

## What the build enforces

Both Android application modules apply `gradle/phoneguard-signing.gradle`.
Debug builds fail immediately if the shared signing configuration is missing or
the configured keystore file cannot be found. This prevents Gradle from silently
falling back to a machine-specific `~/.android/debug.keystore`.

Only debug builds use this shared development key. Production/release signing
must use a separate production key before release.

## Verify the key

Windows:

```powershell
keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android
```

After copying it to Ubuntu, run `keytool -list -v` there too and confirm that
the SHA-256 certificate fingerprint is identical.
