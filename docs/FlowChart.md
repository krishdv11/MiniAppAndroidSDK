flowchart TD

    %% App launch and SDK init
    U[User opens Host App] --> H[Host App integrates SDK<br/>and calls<br/>initMiniAppSDK(appId, secretKey, domainURL)]

    H --> A{Partner auth<br/>configured?}

    A -- Yes --> PA[SDK/Backend: POST /miniapp/v1/partner/auth<br/>→ token, updatedDate]
    A -- No --> S0[Skip auth<br/>(no token, no updatedDate)]

    PA --> S1[Store token + updatedDate<br/>in encrypted SDK cache]
    S0 --> S1

    %% Decide if we need to refresh mini-app list
    S1 --> F{First init on device<br/>OR cache missing?}

    F -- Yes --> L1[SDK: POST /miniapp/v1/runtime/list<br/>(fetchMiniApps)]
    F -- No --> C1[Call /miniapp/v1/partner/auth again (if used)<br/>and compare updatedDate<br/>with cached value]

    C1 --> M1{updatedDate<br/>changed?}
    M1 -- No --> USE_CACHE[Use cached mini app list<br/>and cached ZIPs]
    M1 -- Yes --> L1

    %% Initial / refreshed list handling
    L1 --> L2[Response: list of active mini apps<br/>with latestVersion, bridgeVersion,<br/>permissions, rolloutPercent]
    L2 --> L3[Update cached list in SDK<br/>(encrypted storage)]

    %% Decide which ZIPs to (re)download
    L3 --> Z1{For each mini app:<br/>version/bridgeVersion<br/>changed vs cache?}

    Z1 -- No --> Z_SKIP[Keep existing ZIP in cache]
    Z1 -- Yes --> Z_NEED[Mark ZIP for update]

    %% ZIP download with resume
    Z_NEED --> DT[SDK: POST /miniapp/v1/runtime/{appId}/download-token<br/>→ downloadUrl, checksum, artifactId]
    DT --> ZD{Existing partial<br/>download?}

    ZD -- Yes --> ZR[Resume download from last byte offset<br/>(e.g. from 80% onwards)]
    ZD -- No --> ZFULL[Start full ZIP download]

    ZR --> ZV[Verify checksum / integrity]
    ZFULL --> ZV

    ZV --> ZOK{File valid?}
    ZOK -- Yes --> ZSTORE[Store ZIP in encrypted cache<br/>and update cached version/bridgeVersion]
    ZOK -- No --> ZRETRY[Restart full download<br/>(discard corrupted file)]
    ZRETRY --> ZFULL

    %% Expose fetch APIs to host app
    ZSTORE --> READY[SDK ready with mini app list<br/>and ZIP artifacts]

    USE_CACHE --> READY

    READY --> FH[Host app calls<br/>fetchMiniApps() or<br/>fetchMiniAppsWithDefaultUI()]

    FH --> R1[SDK returns:<br/>• Array of mini app objects<br/>• OR default UI (banner list<br/>with label + image view)]

    R1 --> UX[Host app renders either:<br/>• Custom UI using objects<br/>• Or SDK default UI]

    %% Open mini app
    UX --> TAP[User taps a mini app<br/>in host UI]
    TAP --> OM[Host app calls<br/>openMiniApp(appId)]

    OM --> DCHK{ZIP for appId<br/>already downloaded<br/>and valid?}

    DCHK -- Yes --> PERM[SDK checks required permissions<br/>(e.g. network, storage)]
    DCHK -- No --> DSTART[SDK starts ZIP download<br/>(same resume logic as above)<br/>and returns \"download in progress\"<br/>status to host app]

    DSTART --> HLOAD[Host app shows loading indicator]

    PERM --> POK{Permissions granted<br/>by host app / user?}

    POK -- Yes --> STOK[SDK: optional POST /miniapp/v1/runtime/{appId}/session-token<br/>for bridge/session]
    POK -- No --> PFAIL[Return error or request permission<br/>handling to host app]

    STOK --> OPEN[SDK opens mini app<br/>in secure WebView<br/>using cached ZIP]

    %% Metrics and events
    DFULL[Download/launch lifecycle] -.-> MEV[SDK buffers runtime events<br/>(ZipDownloadInitiated, ZipDownloadCompleted,<br/>AppLaunched, PermissionGranted, etc.)]
    OPEN --> MEV

    MEV --> MBATCH[SDK periodically POSTs<br/>/miniapp/v1/metrics/events or /events/batch]
