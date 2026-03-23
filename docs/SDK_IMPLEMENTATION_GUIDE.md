# MiniApp SDK Implementation Guide

This guide combines implementation steps and code mapping for the MiniApp Android SDK.

Use it as the single source of truth for handoff, architecture walkthroughs, audits, and manager-level explanations of what was implemented and where.

## 1) Target Outcome

Implement an Android SDK (`AAR`) that provides:

- `initMiniAppSDK` / `initWith` for SDK bootstrapping.
- `fetchMiniApps` and `fetchMiniAppsWithUI`.
- `openMiniApp` / `openApp` for full-screen mini app launch.
- Runtime list sync, zip cache, checksum append, unzip, manifest verify.
- Session-token verification before loading mini app.
- Permission parsing from API + runtime permission request.
- Lifecycle and download metrics events.
- Separate-task mini app container behavior in Android recents.
- Release and debug artifacts:
  - release AAR for production integration
  - debug AAR with verbose logs

---

## 2) Required High-Level Architecture

Follow this package structure under `miniappsdk/src/main/java/com/digitral/miniappsdk`:

- `api/` - Retrofit interface + DTOs.
- `data/` - repository implementation, cache manager, retry helper, debug logger.
- `domain/` - internal repository contract + public model.
- `state/` - internal SDK state holder.
- `analytics/` - internal tracker.
- `ui/` - SDK-rendered UI components and adapters.
- root:
  - `MiniAppSDK.kt` (public entrypoint)
  - `MiniAppHostActivity.kt` (full-screen container)

Visibility rules:

- Public API only from `MiniAppSDK` and `MiniAppService`.
- Everything else should remain `internal` unless manifest/resource access requires otherwise.

---

## 3) Build Configuration

Implement in `miniappsdk/build.gradle`:

- Plugins:
  - `com.android.library`
  - `org.jetbrains.kotlin.android`
  - `maven-publish`
  - `signing`
- Android:
  - `compileSdk 34`
  - `minSdk 24`
  - Java/Kotlin target 17
  - `consumer-rules.pro`
  - `buildFeatures { buildConfig true }`
- Build types:
  - `debug`: `MINIAPP_VERBOSE_LOGS=true`
  - `release`: `MINIAPP_VERBOSE_LOGS=false`
- Core dependencies:
  - OkHttp + logging-interceptor
  - Retrofit + Gson converter
  - Coroutines
  - Glide
  - ViewPager2

Publishing:

- Configure release publication with `groupId`, `artifactId`, version.
- Keep local/remote repository blocks and signing placeholders.

---

## 4) Manifest and Resource Baseline

`miniappsdk/src/main/AndroidManifest.xml` must include:

- `<uses-permission android:name="android.permission.INTERNET" />`
- Runtime permission declarations needed by supported API permission keys.
- `MiniAppHostActivity` declaration with:
  - non-exported
  - separate task affinity
  - document launch mode dedupe behavior
  - dedicated theme
  - recents visibility

Required resources:

- `miniapp_activity_host.xml` (WebView + close + error state)
- banner layouts (`view_banner.xml`, `item_banner_page.xml`)
- `values/strings.xml`, `values/dimens.xml`, `values/styles.xml`

---

## 5) API Contract Layer

Define endpoints in `api/MiniAppApi.kt`:

- `POST /miniapp/v1/partner/auth`
- `POST /miniapp/v1/runtime/list`
- `POST /miniapp/v1/runtime/{appId}/download-token`
- `POST /miniapp/v1/runtime/{appId}/session-token`
- `POST /miniapp/v1/metrics/events`

Define DTOs in `api/MiniAppApiModels.kt`:

- envelope models
- auth request/response
- runtime list request/response
- download-token request/response
- session-token request/response
- metrics request

---

## 6) SDK Public API Contract

Implement in `MiniAppSDK.kt`:

- `initWith(context, appId, secretKey, domainUrl)`
- `initMiniAppSDK(...)` alias
- `fetchMiniApps(callback)`
- `fetchMiniAppsWithUI(callback)`
- `openApp(miniAppId, callback)` (container path)
- `openApp(miniAppId, webView, callback)` (direct WebView path)
- `openMiniApp(...)` aliases

Initialization responsibilities:

- Validate params.
- Create OkHttp/Retrofit.
- Create repository.
- Store internal state.
- Trigger background sync (`syncAndCacheMiniApps`).

---

## 7) Repository Responsibilities (Core Logic)

Implement in `data/MiniAppRepositoryImpl.kt`.

### 7.1 Sync and cache list

`syncAndCacheMiniApps()`:

1. Partner auth (if configured).
2. Compare `updatedDate` with cached sync-state.
3. If unchanged + cache exists, return cached list.
4. Else fetch runtime list.
5. Cache services list and metadata snapshots.
6. For each app, decide whether zip download is needed using version/bridge comparison.

### 7.2 Download and cache mini app

Per app flow:

1. Fetch download token.
2. Record `ZipDownloadInitiated`.
3. Download zip with resume support (`Range` header).
4. Append checksum bytes to downloaded zip.
5. Unzip safely.
6. Verify manifest exists.
7. Record success/failure metrics:
   - `ZipDownloadCompleted` / `ZipDownloadFailed`
   - `ManifestVerified` / `ManifestVerifyFailed`
   - `ZipExtracted` / `ZipExtractFailed`

### 7.3 Session token verification

Before mini app load:

1. Call `POST /runtime/{appId}/session-token`.
2. If token missing/failure, block launch with error.
3. Support auth-fallback path (retry without `Authorization` if needed).

### 7.4 Permission mapping

Read permission keys from runtime list and map to Android runtime permissions:

- camera, location, storage/media, mic, contacts, calendar, sms, phone, bluetooth, notifications, etc.
- support direct `android.permission.*` passthrough.
- ignore non-runtime keys such as `network`.

### 7.5 Metrics

Use `recordLifecycleEvent(miniAppId, eventType, message)` to record:

- permission granted/denied
- app launched/closed
- bridge connected/disconnected
- download/extraction/manifest events

---

## 8) Cache Manager Rules

Implement in `data/MiniAppCacheManager.kt`:

- Cache root under app cache directory.
- Persist:
  - service list
  - sync-state (`updatedDate`, version snapshots, permission map)
- Use atomic writes where needed.
- Zip extraction with zip-slip protection.
- Locate entry HTML (`index.html` fallback rules).

---

## 9) Container Launch (Separate App-like Experience)

Implement in `MiniAppHostActivity.kt`:

1. Receive `miniAppId`, title, and icon URL.
2. Apply task presentation for recents.
3. Check runtime permissions and request if missing.
4. On granted:
   - verify session token
   - load local cached entry in WebView
5. Track lifecycle events:
   - `AppLaunched`, `BridgeConnected`, `AppClosed`, `BridgeDisconnected`
6. Show graceful error state when any step fails.

Task behavior requirements:

- Different mini apps appear as different recent entries.
- Re-opening the same mini app dedupes to one recent entry.

---

## 10) Default SDK UI Modes

SDK can provide categorized UI rendering from cached mini apps:

- `NAME_ONLY`
- `NAME_WITH_ICON`
- `BANNER`

Primary code:

- `miniappsdk/src/main/java/com/digitral/miniappsdk/ui/MiniAppCategorizedViewFactory.kt`

---

## 11) Debug vs Release SDK Behavior

### Release AAR

- Safe for production usage.
- No verbose debug logs.
- Use: `miniappsdk-release.aar`

### Debug AAR

- Verbose logs enabled with `MiniAppSDK-Debug` tag.
- Logs include:
  - API request/response
  - auth fallback path
  - cache/download/extract events
  - download progress percentages
- Use: `miniappsdk-debug.aar`

Debug logger location:

- `miniappsdk/src/main/java/com/digitral/miniappsdk/data/MiniAppDebugLogger.kt`

---

## 12) Sample App Validation Setup

`sampleapp/build.gradle` integrates AARs as:

- `debugImplementation` -> debug AAR
- `releaseImplementation` -> release AAR
- Add runtime dependencies (OkHttp/Retrofit/Coroutines/Glide/ViewPager2) when using local AAR files.
- Ensure sample prebuild depends on SDK AAR assemble tasks.

Core sample validation:

1. Initialize SDK.
2. Fetch mini app list.
3. Tap mini app with required permissions.
4. Verify runtime permission dialog appears.
5. Verify session-token API is called before load.
6. Verify full-screen host launches.
7. Verify recents behavior (same app dedupe, different app split).

---

## 13) Delivery Artifacts

Expected handoff:

- `release/miniappsdk-release.aar`
- `release/miniappsdk-debug.aar`
- optional: `release/sampleapp-debug.apk`

---

## 14) Acceptance Checklist (Parity)

Parity is complete when all are true:

- Public API names and signatures match expected contract.
- Flowchart in `docs/FLOWS.md` matches runtime behavior.
- Session-token is called before mini app load.
- Permissions are requested based on API `permissions`.
- Zip resume/checksum/manifest flow works.
- Metrics events are emitted for success and failure paths.
- Separate-task behavior and recents dedupe work.
- Debug AAR logs are verbose while release AAR logs are not.
- Sample app validates all core flows on device.

---

## 15) Detailed Behavior-to-Code Map

### 15.1 Public SDK entry points

- `MiniAppSDK.initWith(...)` / `initMiniAppSDK(...)`
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/MiniAppSDK.kt`
  - Responsibility: validate inputs, bootstrap Retrofit/OkHttp/repository, trigger background sync.

- `MiniAppSDK.fetchMiniApps(...)`
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/MiniAppSDK.kt`
  - Responsibility: return cached list and trigger sync when needed.

- `MiniAppSDK.fetchMiniAppsWithUI(...)`
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/MiniAppSDK.kt`
  - Responsibility: return SDK-provided UI backed by cached mini apps.

- `MiniAppSDK.openApp(...)` / `openMiniApp(...)`
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/MiniAppSDK.kt`
  - Responsibility:
    - container path: open dedicated mini app host (separate task/document).
    - webview path: load cached local HTML into caller-provided `WebView`.

### 15.2 Runtime API layer

- API interface and endpoints:
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/api/MiniAppApi.kt`

- API DTOs:
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/api/MiniAppApiModels.kt`

### 15.3 Core orchestration and cache

- Main orchestration:
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/data/MiniAppRepositoryImpl.kt`

- Cache manager:
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/data/MiniAppCacheManager.kt`

- Retry helper:
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/data/MiniAppRetry.kt`

### 15.4 Permission handling

- Permission resolution:
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/data/MiniAppRepositoryImpl.kt`
  - Entry method: `getRequiredPermissions(miniAppId)`

- Runtime request flow:
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/MiniAppHostActivity.kt`
  - Key methods:
    - `checkPermissionsAndLaunch()`
    - `onRequestPermissionsResult(...)`

### 15.5 Container/task behavior

- Host activity:
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/MiniAppHostActivity.kt`

- Task configuration:
  - File: `miniappsdk/src/main/AndroidManifest.xml`

### 15.6 Metrics and analytics

- Metrics event emission:
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/data/MiniAppRepositoryImpl.kt`

- Analytics tracker:
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/analytics/MiniAppSDKAnalyticsTracker.kt`

### 15.7 Cross-platform wrappers

- React Native bridge:
  - `integrations/react-native/android/MiniAppSdkModule.kt`

- Flutter plugin:
  - `integrations/flutter/android/MiniAppSdkFlutterPlugin.kt`

---

## 16) Primary Code Map

For fast navigation:

- Public entry: `miniappsdk/src/main/java/com/digitral/miniappsdk/MiniAppSDK.kt`
- Container: `miniappsdk/src/main/java/com/digitral/miniappsdk/MiniAppHostActivity.kt`
- Repository: `miniappsdk/src/main/java/com/digitral/miniappsdk/data/MiniAppRepositoryImpl.kt`
- Cache: `miniappsdk/src/main/java/com/digitral/miniappsdk/data/MiniAppCacheManager.kt`
- API interface: `miniappsdk/src/main/java/com/digitral/miniappsdk/api/MiniAppApi.kt`
- API models: `miniappsdk/src/main/java/com/digitral/miniappsdk/api/MiniAppApiModels.kt`
- Flowchart: `docs/FLOWS.md`
