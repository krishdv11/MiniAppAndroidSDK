# Mini App SDK End-to-End Flow

**Visualizes SDK init, caching, ZIP download with resume, public APIs, and metrics.**

```mermaid
flowchart TD
    U[User opens Host App] --> H[Host App integrates SDK<br/>and calls<br/>initMiniAppSDK(appId, secretKey, domainURL)]
    H --> A{Partner auth<br/>configured?}
    A -- Yes --> PA[SDK: POST /miniapp/v1/partner/auth<br/>→ token, updatedDate]
    A -- No --> S0[Skip auth<br/>(no token needed)]
    PA --> S1[Store token + updatedDate<br/>in encrypted SDK cache]
    S0 --> S1
    S1 --> F{First init OR<br/>cache missing?}
    F -- Yes --> L1[SDK: POST /miniapp/v1/runtime/list<br/>(fetchMiniApps)]
    F -- No --> C1[Re-check /partner/auth<br/>updatedDate vs cache]
    C1 --> M1{updatedDate changed?}
    M1 -- No --> USE_CACHE[Use cached list + ZIPs]
    M1 -- Yes --> L1
    L1 --> L2[Response: active mini apps<br/>latestVersion, bridgeVersion,<br/>permissions, rolloutPercent]
    L2 --> L3[Update cached list<br/>(encrypted storage)]
    L3 --> Z1{For each app:<br/>version/bridgeVersion changed?}
    Z1 -- No --> Z_SKIP[Keep cached ZIP]
    Z1 -- Yes --> Z_NEED[Mark ZIP for update]
    Z_NEED --> DT[POST /runtime/{appId}/download-token<br/>→ downloadUrl, checksum]
    DT --> ZD{Partial download exists?}
    ZD -- Yes --> ZR[Resume from byte offset<br/>(e.g. 80%)]
    ZD -- No --> ZFULL[Full ZIP download]
    ZR --> ZV[Verify checksum/integrity]
    ZFULL --> ZV
    ZV --> ZOK{Valid?}
    ZOK -- Yes --> ZSTORE[Store encrypted ZIP<br/>+ update version cache]
    ZOK -- No --> ZRETRY[Discard & restart full download]
    ZRETRY --> ZFULL
    ZSTORE --> READY[SDK ready: list + ZIPs cached]
    USE_CACHE --> READY
    READY --> FH[Host calls fetchMiniApps()<br/>or fetchMiniAppsWithDefaultUI()]
    FH --> R1[SDK returns:<br/>-  Mini app objects array<br/>-  OR default UI banners]
    R1 --> UX[Host renders custom UI<br/>OR SDK default UI]
    UX --> TAP[User taps mini app]
    TAP --> OM[Host calls openMiniApp(appId)]
    OM --> DCHK{ZIP downloaded & valid?}
    DCHK -- Yes --> PERM[Check permissions<br/>e.g. ["network", "storage"]]
    DCHK -- No --> DSTART[Start ZIP download<br/>return "download in progress"]
    DSTART --> HLOAD[Host shows loading]
    PERM --> POK{Permissions granted?}
    POK -- Yes --> STOK[Optional: POST /runtime/{appId}/session-token]
    POK -- No --> PFAIL[Error/request permissions]
    STOK --> OPEN[Open in secure WebView]
    OPEN --> MEV[Buffer events:<br/>ZipDownloadCompleted, AppLaunched,<br/>PermissionGranted, etc.]
    MEV --> MBATCH[POST /metrics/events<br/>or /events/batch]
