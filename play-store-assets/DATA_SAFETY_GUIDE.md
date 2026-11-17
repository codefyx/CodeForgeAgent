# Data Safety Form Guide

- Data collected: None by the app developer. All data stays on device.
- Data shared: Prompts and code sent directly to user-selected LLM provider endpoints.
- Security: Data encrypted in transit (HTTPS/TLS). API keys encrypted at rest via Android Keystore.
- Account creation: Not required.
- Foreground Service declaration: Used for long-running AI agent loops and terminal sessions (dataSync/specialUse). User-initiated, with persistent notification and stop control.