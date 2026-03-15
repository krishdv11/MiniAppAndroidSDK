# MiniApp SDK Implementation Guide

This document maps each SDK behavior to concrete code locations so implementation can be explained quickly and audited easily.

## 1) Public SDK Entry Points

- `MiniAppSDK.initWith(...)` / `initMiniAppSDK(...)`
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/MiniAppSDK.kt`
  - Responsibility: validate inputs, bootstrap Retrofit/OkHttp/repository, trigger background sync.

- `MiniAppSDK.fetchMiniApps(...)`
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/MiniAppSDK.kt`
  - Responsibility: return cached list, trigger sync if cache empty.

- `MiniAppSDK.fetchMiniAppsWithUI(...)`
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/MiniAppSDK.kt`
  - Responsibility: return default SDK UI (`ViewPager2` banner) backed by cached mini apps.

- `MiniAppSDK.openApp(...)` / `openMiniApp(...)`
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/MiniAppSDK.kt`
  - Responsibility:
    - container path: opens dedicated mini app host container (separate task/document).
    - webview path: loads cached local HTML into caller-provided `WebView`.

## 2) Runtime API Layer

- Retrofit interface and endpoints:
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/api/MiniAppApi.kt`
  - APIs handled:
    - partner auth
    - runtime list
    - download token
    - session token
    - metrics event

- DTOs for API request/response:
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/api/MiniAppApiModels.kt`

## 3) Core Orchestration and Caching

- Main orchestration engine:
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/data/MiniAppRepositoryImpl.kt`
  - Key flows:
    - auth + updatedDate gate
    - runtime list fetch with auth fallback
    - version/bridge comparison
    - conditional zip download
    - checksum append
    - unzip + manifest verification
    - session-token verification before webview load
    - metrics publishing
    - permission key parsing and mapping

- Cache management:
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/data/MiniAppCacheManager.kt`
  - Responsibilities:
    - service cache read/write
    - sync state read/write
    - zip and extraction directory management
    - secure unzip (zip-slip protection)
    - find entry HTML

- Retry helper:
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/data/MiniAppRetry.kt`
  - Responsibility: retry transient IO/network failures.

## 4) Permission Handling

- Permission key -> Android permission resolution:
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/data/MiniAppRepositoryImpl.kt`
  - Entry method: `getRequiredPermissions(miniAppId)`
  - Handles keys like camera/location/storage/media/audio/contacts/calendar/sms/phone/bluetooth/notifications and direct `android.permission.*`.

- Runtime permission request before launch:
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/MiniAppHostActivity.kt`
  - Methods:
    - `checkPermissionsAndLaunch()`
    - `onRequestPermissionsResult(...)`

## 5) Separate App Container Behavior

- Host container activity:
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/MiniAppHostActivity.kt`
  - Responsibilities:
    - receives mini app id/title/icon
    - applies separate-task presentation
    - requests runtime permissions
    - loads mini app in full-screen WebView
    - records app lifecycle metrics

- Activity/task configuration:
  - File: `miniappsdk/src/main/AndroidManifest.xml`
  - Behavior:
    - separate task affinity
    - document launch mode with same-app dedupe
    - recents visibility
    - dedicated container theme

## 6) Metrics & Analytics

- Lifecycle and download metrics:
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/data/MiniAppRepositoryImpl.kt`
  - Event examples:
    - `ZipDownloadInitiated`
    - `ZipDownloadCompleted` / `ZipDownloadFailed`
    - `ManifestVerified` / `ManifestVerifyFailed`
    - `ZipExtracted` / `ZipExtractFailed`
    - `PermissionGranted` / `PermissionDenied`
    - `AppLaunched` / `AppClosed`
    - `BridgeConnected` / `BridgeDisconnected`

- Internal lightweight analytics tracker:
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/analytics/MiniAppSDKAnalyticsTracker.kt`

## 7) Default SDK UI

- Banner adapter:
  - File: `miniappsdk/src/main/java/com/digitral/miniappsdk/ui/MiniAppBannerPagerAdapter.kt`
  - Layouts:
    - `miniappsdk/src/main/res/layout/view_banner.xml`
    - `miniappsdk/src/main/res/layout/item_banner_page.xml`

## 8) Debug vs Release SDK Artifacts

- Debug SDK artifact:
  - `release/miniappsdk-debug.aar`
  - Includes verbose log mode (`BuildConfig.MINIAPP_VERBOSE_LOGS=true`).

- Release SDK artifact:
  - `release/miniappsdk-release.aar`
  - Production mode (`BuildConfig.MINIAPP_VERBOSE_LOGS=false`).

- Debug logger and where logs are emitted:
  - `miniappsdk/src/main/java/com/digitral/miniappsdk/data/MiniAppDebugLogger.kt`
  - Tag: `MiniAppSDK-Debug`

## 9) Build and Delivery Files

- SDK module build config:
  - `miniappsdk/build.gradle`

- Sample app integration reference:
  - `sampleapp/build.gradle`
  - Uses debug/release AAR wiring for validation.

- Flowchart reference:
  - `docs/FLOWS.md`

## 10) Cross-Platform Bridge Skeletons

- React Native Android bridge example:
  - `integrations/react-native/android/MiniAppSdkModule.kt`

- Flutter Android plugin example:
  - `integrations/flutter/android/MiniAppSdkFlutterPlugin.kt`

Both wrappers expose native calls for:

- `initMiniAppSDK`
- `fetchMiniApps`
- `openMiniApp`
