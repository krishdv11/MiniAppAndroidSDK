# MiniApp SDK Rebuild Playbook (From Scratch)

This playbook explains how to rebuild the current MiniApp Android SDK from zero and reach behavior parity with this repository.

Use this document as the implementation sequence for engineering handoff, audits, and fresh implementation.

## 1) Target Outcome

Rebuild an Android SDK (`AAR`) that provides:

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
- `ui/` - default banner UI adapter.
- root:
  - `MiniAppSDK.kt` (public entrypoint)
  - `MiniAppHostActivity.kt` (internal full-screen container)

Visibility rules:

- Public API only from `MiniAppSDK` and `MiniAppService`.
- Everything else `internal` unless Android manifest/resources require otherwise.

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

- envelopes
- auth request/response
- runtime list model
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
6. For each app: decide whether zip download is needed using version/bridge comparison.

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
2. If token missing/failure -> block launch with error.
3. Support auth-fallback path (retry without `Authorization` if needed).

### 7.4 Permission mapping

Read permission keys from runtime list and map to Android runtime permissions:

- camera, location, storage/media, mic, contacts, calendar, sms, phone, bluetooth, notifications, etc.
- support direct `android.permission.*` passthrough.
- ignore non-runtime keys like `network`.

### 7.5 Metrics

Expose `recordLifecycleEvent(miniAppId, eventType, message)` to record:

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

1. Receive `miniAppId`, title, icon URL.
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
- Same mini app re-open dedupes to one recent entry.

---

## 10) Debug vs Release SDK Behavior

### Release AAR

- Should be safe for production.
- No verbose debug logs.
- Use:
  - `miniappsdk-release.aar`

### Debug AAR

- Verbose logs enabled with `MiniAppSDK-Debug` tag.
- Log categories:
  - API request/response
  - fallback path
  - cache/download/extract events
  - download progress percentages
- Use:
  - `miniappsdk-debug.aar`

---

## 11) Sample App Validation Setup

`sampleapp/build.gradle` should integrate AARs:

- `debugImplementation` -> debug AAR
- `releaseImplementation` -> release AAR
- Add runtime dependencies (OkHttp/Retrofit/Coroutines/Glide/ViewPager2) when using local AAR files.
- Ensure sample prebuild depends on SDK AAR assemble tasks.

Core sample verification:

1. Init SDK.
2. Fetch mini apps list.
3. Tap app with permissions required.
4. Verify permission dialog.
5. Verify session-token call before load.
6. Verify full-screen container launch.
7. Verify recents behavior (same app dedupe, different app split).

---

## 12) Delivery Artifacts

Expected handoff files:

- `release/miniappsdk-release.aar`
- `release/miniappsdk-debug.aar`
- optional: `release/sampleapp-debug.apk`

---

## 13) Acceptance Checklist (Parity)

You have 100% parity when all are true:

- Public API names and signatures match.
- Flowchart in `docs/FLOWS.md` matches runtime behavior.
- Session-token is called before mini app load.
- Permissions are requested based on API `permissions`.
- Zip resume/checksum/manifest flow works.
- Metrics events are emitted for success and failure paths.
- Separate-task behavior and recents dedupe work.
- Debug AAR logs verbose; release AAR does not.
- Sample app validates all above on device.

---

## 14) Primary Code Map

For fast navigation:

- Public entry: `miniappsdk/src/main/java/com/digitral/miniappsdk/MiniAppSDK.kt`
- Container: `miniappsdk/src/main/java/com/digitral/miniappsdk/MiniAppHostActivity.kt`
- Repository: `miniappsdk/src/main/java/com/digitral/miniappsdk/data/MiniAppRepositoryImpl.kt`
- Cache: `miniappsdk/src/main/java/com/digitral/miniappsdk/data/MiniAppCacheManager.kt`
- API interface: `miniappsdk/src/main/java/com/digitral/miniappsdk/api/MiniAppApi.kt`
- API models: `miniappsdk/src/main/java/com/digitral/miniappsdk/api/MiniAppApiModels.kt`
- Flowchart: `docs/FLOWS.md`
- Implementation summary: `docs/SDK_IMPLEMENTATION_GUIDE.md`
