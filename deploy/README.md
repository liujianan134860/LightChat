# LightChat production deployment

## Recommended topology

- Alibaba Cloud ECS: Docker Compose, Nginx, and the LightChat server.
- Alibaba Cloud RDS MySQL: structured persistence over the VPC internal endpoint.
- Alibaba Cloud OSS: private image objects returned through signed URLs.
- Alibaba Cloud ACR: versioned server images used by deployment and rollback.

## First deployment

1. Build and push the image to ACR:

   ```bash
   docker build -t <acr-registry>/lightchat/server:<version> .
   docker push <acr-registry>/lightchat/server:<version>
   ```

2. On ECS, copy `.env.example` to `.env`, replace all `CHANGE_ME` values, and set:

   ```bash
   export ACR_IMAGE=<acr-registry>/lightchat/server:<version>
   docker compose up -d
   curl --fail http://127.0.0.1:8081/health
   ```

3. Replace `chat.example.com` in `nginx/lightchat.conf`, install the TLS certificate, then reload Nginx.

4. Build the Android client against HTTPS and WSS:

   ```powershell
   $env:LIGHTCHAT_KEYSTORE_FILE="C:\secure\lightchat-release.jks"
   $env:LIGHTCHAT_KEYSTORE_PASSWORD="<keystore-password>"
   $env:LIGHTCHAT_KEY_ALIAS="lightchat"
   $env:LIGHTCHAT_KEY_PASSWORD="<key-password>"
   .\gradlew.bat :app:assembleRelease `
     -PLIGHTCHAT_API_URL=https://chat.example.com `
     -PLIGHTCHAT_WS_URL=wss://chat.example.com/ws
   ```

## Operational constraints

The current server keeps active connection and runtime state in process memory, while persisting snapshots to MySQL. Deploy exactly one server replica for now. Horizontal scaling requires shared session/event state and cross-instance message routing, such as Redis plus a message broker.

Never commit `deploy/.env`, TLS private keys, RDS credentials, OSS access keys, or signing keys. Restrict ports 8080 and 8081 to localhost; expose only Nginx ports 80 and 443 through the ECS security group.
