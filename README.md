# CodeForge Agent

Independent open-source AI coding client for Android. Native Kotlin + Jetpack Compose app providing an agentic coding workflow: chat with 200+ LLMs, run shell commands, edit files, manage Git, and connect to MCP/LSP servers.

Not affiliated with or endorsed by any third-party AI provider.

## Build

```
./gradlew assembleDebug
./gradlew bundleRelease
```

Release signing uses env vars: `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Tests

```
./gradlew testDebugUnitTest
./gradlew assembleDebugAndroidTest
```

## License

MIT. See THIRD_PARTY_NOTICES.md.