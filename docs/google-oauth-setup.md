# Google OAuth setup

## Privacy and cost boundary

- Google Drive API only; no Firebase, Firestore, Cloud Storage, server, remote
  analytics or remote AI.
- Personal cloud payloads go directly from an authorized client to the signed-in
  user's Drive `appDataFolder`.
- Scope: `https://www.googleapis.com/auth/drive.appdata` (**non-sensitive**).
- Current setup requires no billing account or paid Google Cloud service.
- While the app is in Testing, the owner's Google email must be an OAuth test
  user.

## OAuth client values

### Android debug APK currently delivered

- Package: `com.nobelle.gymhealth.debug`
- Certificate SHA-1: `32:75:42:E3:8A:6A:CE:BF:51:AD:77:27:91:30:DC:B4:D8:7E:35:3E`
- Certificate SHA-256:
  `EA:53:B4:29:D0:A2:04:65:FF:55:DC:E8:89:D3:ED:31:3C:8D:FD:D8:67:68:83:67:59:11:C1:ED:31:BB:E9:6C`

These values were read from `phone-debug.apk` using Android SDK `apksigner`,
not inferred from a local keystore. A future production/release certificate and
package `com.nobelle.gymhealth` require a separate Android OAuth client.

### Local web client

- Authorized JavaScript origin: `http://localhost:5173`
- Authorized redirect URI: **none**

The web app uses Google Identity Services `initTokenClient` in popup mode. The
OAuth response returns to the JavaScript callback rather than an application
redirect endpoint. Tokens stay in memory and are not written to local storage,
URLs, service-worker caches, logs or a server.

## Credential configuration

Copy `web/.env.example` to ignored `web/.env.local`, then set:

```text
VITE_GOOGLE_CLIENT_ID=<web-client-id>.apps.googleusercontent.com
```

No OAuth client secret belongs in the static web application or Android APK.
