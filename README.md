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

### 2) Host app requirements

Add network permission in your host app `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### 3) Initialize SDK (once at app launch)

```kotlin
MiniAppSDK.initWithAppID(
    context = applicationContext,
    appId = YOUR_APP_ID,
    secretKey = YOUR_SECRET_KEY,
    domainUrl = YOUR_BASE_URL
)
```

### 4) Read cached mini app list

```kotlin
MiniAppSDK.getCachedMiniApps { result ->
    result.onSuccess { services ->
        // Render list in host UI
    }.onFailure { error ->
        // Show retry/error state
    }
}
```

### 5) Load a mini app in WebView

```kotlin
MiniAppSDK.loadMiniAppInWebView(
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

On `initWithAppID(...)`, SDK runs background sync:

1. partner auth (with safe fallback behavior)
2. runtime mini app list fetch
3. service list cache write
4. per-miniapp `download-token` call
5. zip download + checksum-part append + extraction
6. zip download metrics event (`success`/`failed`)

On mini app click/load:

1. check extracted cache
2. if missing, prioritize per-miniapp cache creation
3. fallback to full sync if needed
4. load local extracted entry HTML into `WebView`

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
