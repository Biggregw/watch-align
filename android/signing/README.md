# Watch Align signing key

Every Watch Align APK should be signed with the same key. Android then treats each new build as an update of the same app (it installs over the previous one), and Play Protect only has to accept the developer once.

- **Key:** PKCS12 keystore, alias `watchalign`, RSA 4096, valid until 2056. The key password is the same as the store password.
- **Certificate SHA-256:** see `watchalign-cert-sha256.txt`. CI checks every APK against it.
- **Never committed.** `*.p12`, `*.jks` and `*.keystore` are in `.gitignore`.
- **Back it up.** Keep the keystore file and its password somewhere safe, such as a password manager. Losing them means future builds can't install over this one, and users would have to uninstall first.

## CI (GitHub Actions)

Add two repository secrets (Settings → Secrets and variables → Actions → New repository secret):

| Secret | Value |
|---|---|
| `WATCHALIGN_KEYSTORE_B64` | The keystore, base64-encoded on one line (`base64 -w0 watchalign-release.p12`) |
| `WATCHALIGN_KEYSTORE_PASSWORD` | The keystore password |

The workflow `build-android.yml` decodes the key, builds, and fails if the APK isn't signed with this certificate. Without the secrets it falls back to a throwaway debug key and shows a warning.

## Local builds

From `android/`:

```bash
export WATCHALIGN_KEYSTORE_FILE=/path/to/watchalign-release.p12
export WATCHALIGN_KEYSTORE_PASSWORD='...'
bash ./gradlew :app:assembleDebug
```

Without these variables, Gradle signs with the machine's default debug key, as before.
