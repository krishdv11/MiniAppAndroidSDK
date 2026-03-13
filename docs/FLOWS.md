# MiniApp Android SDK Flows

This document captures end-to-end runtime flows for the Android SDK.

## 0) Single End-to-End Flow (Consolidated)

```mermaid
sequenceDiagram
    participant U as User
    participant A as Host App
    participant S as MiniAppSDK
    participant P as Mini App Platform API
    participant R as Artifact Storage
    participant C as Local Cache
    participant W as WebView

    U->>A: Open app
    A->>S: initWithAppID(context, appId, secretKey, domainUrl)
    S->>P: partner/auth (optional/fallback-safe)
    S->>P: runtime/list
    P-->>S: mini app list
    S->>C: save services.json

    loop For each mini app
        S->>P: runtime/{appId}/download-token
        P-->>S: downloadUrl + checksum + artifactId
        S->>R: download zip part-1
        R-->>S: zip bytes
        S->>S: append checksum bytes (part-2)
        S->>C: unzip and store extracted files
        S->>P: metrics/events (success/failure)
    end

    U->>A: Tap mini app
    A->>S: loadMiniAppInWebView(miniAppId, webView)
    S->>C: check extracted index.html
    alt cache missing
        S->>S: ensureMiniAppCached(miniAppId)
        S->>P: runtime/list (if needed)
        S->>P: runtime/{appId}/download-token
        S->>R: download + checksum append
        S->>C: unzip and store
    end
    S->>W: load file://.../index.html
    W-->>U: Mini app rendered
```

## 1) SDK Initialization and Background Sync

```mermaid
flowchart TD
    A[Host App Calls MiniAppSDK.initWithAppID] --> B[Validate Inputs]
    B --> C[Create OkHttp + Retrofit + API]
    C --> D[Initialize Repository + SDK State]
    D --> E[Launch Background syncAndCacheMiniApps]
    E --> F[Partner Auth Optional Fallback]
    F --> G[Fetch Runtime Mini App List]
    G --> H[Save Services to Cache]
    H --> I[For Each Mini App]
    I --> J[Call download-token]
    J --> K[Download Zip]
    K --> L[Append checksum bytes part]
    L --> M[Extract to Cache]
    M --> N[Record Metrics Success or Failure]
```

## 2) Cached Mini App List Read

```mermaid
flowchart TD
    A[Host Calls getCachedMiniApps] --> B[Check SDK Initialized]
    B --> C[Read services.json from Cache]
    C --> D{Cache Exists?}
    D -->|Yes| E[Return List]
    D -->|No| F[Return Empty List]
```

## 3) Mini App Click and WebView Load

```mermaid
flowchart TD
    A[Host Calls loadMiniAppInWebView appId] --> B[Check SDK Initialized]
    B --> C[Find Cached index.html]
    C --> D{Found?}
    D -->|Yes| H[Configure WebView for Local Files]
    D -->|No| E[ensureMiniAppCached appId]
    E --> F{Found After Ensure?}
    F -->|Yes| H
    F -->|No| G[syncAndCacheMiniApps Fallback]
    G --> I{Found After Sync?}
    I -->|No| J[Return Error Mini app zip not cached]
    I -->|Yes| H
    H --> K[Load file://.../index.html]
    K --> L[Return Success]
```

## 4) Per-MiniApp Cache Creation

```mermaid
flowchart TD
    A[ensureMiniAppCached appId] --> B{Already Extracted?}
    B -->|Yes| C[Return True]
    B -->|No| D[Fetch Runtime List]
    D --> E[Resolve Matching Runtime Item]
    E --> F[Call download-token]
    F --> G[Download Zip]
    G --> H[Append checksum bytes]
    H --> I[Extract Zip]
    I --> J[Record Metric]
    J --> K[Return index.html Exists]
```

## 5) Download Token Fallback Behavior

```mermaid
flowchart TD
    A[Request download-token with Authorization] --> B{Success?}
    B -->|Yes| C[Continue Download Flow]
    B -->|No| D{Bearer Present?}
    D -->|No| E[Fail Request]
    D -->|Yes| F[Retry download-token Without Authorization]
    F --> G{Success?}
    G -->|Yes| C
    G -->|No| E
```
