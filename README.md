# MiniApp Android SDK

`MiniApp Android SDK` is a production-ready Android framework from `digitral` for:

- runtime mini app discovery
- zip download and cache management
- local mini app rendering in `WebView`

The SDK is self-contained (no host DI/Hilt requirement) and designed for reusable integration across multiple Android apps.

## Repository Structure

- `miniappsdk/` - Android library framework module (the SDK)
- `sampleapp/` - standalone Android app that consumes the SDK framework
- `docs/FLOWS.md` - end-to-end flow charts for all key SDK flows

This separation follows framework-consumer best practices (same idea as Firebase-style SDK consumption).

## End-to-End Integration Guide (Android)

### 1) Add SDK to your Android app

You can consume the framework in two common ways:

#### Option A: JitPack (recommended for external apps)

Add JitPack repository:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

Add dependency:

```kotlin
dependencies {
    implementation("com.github.krishdv11:MiniAppAndroidSDK:v1.0.0")
}
```

#### Option B: Local framework module (for mono-repo setup)

In your app repo:

```kotlin
// settings.gradle
include(":miniappsdk")
```

```kotlin
// app/build.gradle
dependencies {
    implementation(project(":miniappsdk"))
}
```

#### Option C: Prebuilt AAR integration (for QA/UAT handoff)

Use one SDK for production behavior in both host debug and host release builds:

- `release/miniappsdk-release.aar`


Example host `app/build.gradle`:

```kotlin
dependencies {
    // Single SDK for both host debug/release verification:
    implementation(files("libs/miniappsdk-release.aar"))

    // Optional: use this instead during deep debugging:
    // implementation(files("libs/miniappsdk-debug.aar"))

    // Required transitive runtime dependencies for local AAR usage:
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
}
```

### 2) Host app requirements

Add network permission in your host app `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

If your runtime API returns permissions (for example `camera`, `location`, `storage`), declare the corresponding Android permissions in the host app manifest so runtime requests can be shown when needed.

#### Supported permission keys (API -> Android)

| API permission key | Android runtime permission(s) requested by SDK |
| --- | --- |
| `camera` | `android.permission.CAMERA` |
| `location` | `android.permission.ACCESS_FINE_LOCATION` |
| `location_coarse`, `coarse_location` | `android.permission.ACCESS_COARSE_LOCATION` |
| `background_location` | `android.permission.ACCESS_BACKGROUND_LOCATION` |
| `storage`, `files` | Android 13+: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`; below 13: `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` |
| `photos`, `images` | Android 13+: `READ_MEDIA_IMAGES`; below 13: `READ_EXTERNAL_STORAGE` |
| `videos` | Android 13+: `READ_MEDIA_VIDEO`; below 13: `READ_EXTERNAL_STORAGE` |
| `audio`, `media_audio` | Android 13+: `READ_MEDIA_AUDIO`; below 13: `READ_EXTERNAL_STORAGE` |
| `microphone`, `mic`, `record_audio` | `android.permission.RECORD_AUDIO` |
| `contacts` | `android.permission.READ_CONTACTS`, `android.permission.WRITE_CONTACTS` |
| `calendar` | `android.permission.READ_CALENDAR`, `android.permission.WRITE_CALENDAR` |
| `sms` | `android.permission.SEND_SMS`, `android.permission.RECEIVE_SMS`, `android.permission.READ_SMS` |
| `phone` | `android.permission.READ_PHONE_STATE` |
| `call_log` | `android.permission.READ_CALL_LOG` |
| `bluetooth`, `nearby_devices` | Android 12+: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` |
| `notifications`, `notification` | Android 13+: `android.permission.POST_NOTIFICATIONS` |
| `network` | No runtime permission dialog (normal permission) |
| `android.permission.*` | Passed through directly and requested as-is |

### 3) Initialize SDK (once at app launch)

```kotlin
MiniAppSDK.initWith(
    context = applicationContext,
    appId = YOUR_APP_ID,
    secretKey = YOUR_SECRET_KEY,
    domainUrl = YOUR_BASE_URL
)
```

### 4) Read cached mini app list

```kotlin
MiniAppSDK.fetchMiniApps { result ->
    result.onSuccess { services ->
        // Render list in host UI
    }.onFailure { error ->
        // Show retry/error state
    }
}
```

### 5) Load a mini app in WebView

```kotlin
MiniAppSDK.openApp(
    miniAppId = "com.gamma.finance",
    webView = webView
) { result ->
    result.onSuccess {
        // Local extracted mini app loaded
    }.onFailure { error ->
        // Show fallback UI
    }
}
```

## Runtime Behavior Summary

On `initWith(...)`, SDK runs background sync:

1. partner auth (with safe fallback behavior)
2. runtime mini app list fetch
3. service list cache write
4. per-miniapp `download-token` call
5. zip download + checksum-part append + extraction
6. zip download metrics event (`success`/`failed`)

On mini app open (container path):

1. check extracted cache
2. if missing, return `Download In Progress`
3. check/request runtime permissions based on API `permissions`
4. call `POST /miniapp/v1/runtime/{appId}/session-token`
5. launch full-screen mini app container in separate task
6. load local extracted entry HTML into `WebView`
7. publish lifecycle metrics (`AppLaunched`, `AppClosed`, bridge events)

Debug AAR runtime logging:

- tag: `MiniAppSDK-Debug`
- includes request/response logs, fallback path logs, and zip download progress logs

## Framework Design Notes

- Public entrypoint is `MiniAppSDK`.
- Internal architecture uses `api`, `data`, `domain`, `state`, `analytics`, and `ui`.
- Cache writes and extraction are hardened for stability and safe file handling.

## Flow Charts

For complete diagrams of all major flows, see:

- `docs/FLOWS.md`

## iOS Reference

For cross-platform structure parity and integration style reference, see:

- https://github.com/krishdv11/MiniAppsSDKiOS/tree/develop

## React Native and Flutter Integration

SDK works in React Native and Flutter Android targets via native bridge/plugin wrappers.

- React Native skeleton wrapper:
  - `integrations/react-native/android/MiniAppSdkModule.kt`
- Flutter skeleton wrapper:
  - `integrations/flutter/android/MiniAppSdkFlutterPlugin.kt`

These wrappers expose:

- `initMiniAppSDK`
- `fetchMiniApps`
- `openMiniApp`

## Versioning

Semantic Versioning:

- `MAJOR` - breaking API changes
- `MINOR` - backward-compatible features
- `PATCH` - backward-compatible fixes

Current version: `1.0.0`

## Sample App

`sampleapp/` is the reference consumer app for validation:

1. run sample app
2. initialize SDK
3. load cached mini apps
4. open any mini app in full-screen viewer

## License

MIT License. See `LICENSE`.
