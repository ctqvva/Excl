# Stealth Build

Runtime disguise on Android can change the launcher icon and launcher label, but it cannot change the installed package name. The package name is the app identity and must be chosen at build time.

This project now supports build-time overrides:

- `stealthApplicationId` or `EXCLAVE_APPLICATION_ID`
- `stealthAppLabel` or `EXCLAVE_APP_LABEL`

Examples:

```sh
./gradlew :app:assembleOssDebug \
  -PstealthApplicationId=com.example.camera \
  -PstealthAppLabel=Camera
```

Or with the helper script:

```sh
EXCLAVE_APPLICATION_ID=com.example.camera \
EXCLAVE_APP_LABEL=Camera \
./scripts/build-stealth.sh
```

Notes:

- The launcher label uses the override in the manifest and launcher aliases.
- The VPN session name and quick settings tile label now use the installed app label, not the hardcoded `Exclave` string.
- Existing installs with a different package name are treated by Android as a different app and must be installed separately.
