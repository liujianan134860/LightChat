# LightChat architecture

## Module boundaries

- `app`: Android composition root, Compose UI, navigation, ViewModels and feature orchestration.
- `core:model`: platform-independent client domain models.
- `core:network`: shared OkHttp infrastructure and transport-level result types.
- `core:database`: Android SQLite storage, DAOs and local session persistence.
- `shared:protocol`: platform-independent wire commands, packet contract and CRC codec shared by client and server.
- `server`: Netty WebSocket and HTTP server implementation.

## Dependency direction

`app` depends on core modules and the shared protocol. `server` depends only on the
shared protocol. Core modules do not depend on `app`, and feature code must not
reach through a UI object to access storage or networking.

Hilt is the Android composition root. Process-scoped storage, repositories and IM
managers are created in `AppModule`; ViewModels should receive dependencies through
constructor injection. `LightChatApplication` remains a compatibility facade while
legacy screens are migrated away from direct global lookups.

## Data flow

UI events flow from Compose to a ViewModel. A ViewModel calls a use case or
repository. Network and WebSocket events are validated and persisted before UI
state is refreshed. SQLite remains the client-side source of truth so reconnects
and process recreation do not make rendering depend on a callback being alive.

## Migration policy

Feature modules should be extracted only after their direct `LightChatApplication`
lookups have been replaced by injected contracts. This avoids circular Gradle
dependencies and keeps every migration step buildable. The next extraction targets
are `feature:auth`, `feature:conversation`, and `feature:chat`.
