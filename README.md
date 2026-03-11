# MiniApp Android SDK

`MiniApp Android SDK` is a lightweight, self-contained Android library from `digitral` for fetching MiniApp services and rendering a ready-to-use banner UI component.

The SDK is host-app friendly:

- no DI framework requirement
- no Hilt requirement
- coroutine-based async API with safe callbacks
- retry-on-IO-failure strategy

## Installation (JitPack)

1) Add JitPack to your root repositories:

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

2) Add dependency:

```kotlin
dependencies {
    implementation("com.github.krishdv11:MiniAppAndroidSDK:v1.0.0")
}
```

## Initialization

```kotlin
MiniAppSDK.initWithAppID(
    context = applicationContext,
    appId = "partner-app-id",
    secretKey = "partner-secret-key",
    domainUrl = "https://csdpdev-api.d21.co.in/"
)
```

## Host App Requirement

Ensure the host app manifest includes internet access:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## Get Cached Mini Apps

```kotlin
MiniAppSDK.getCachedMiniApps { result ->
    result.onSuccess { services ->
        // Render mini app list in host UI
    }.onFailure { throwable ->
        // Handle error
    }
}
```

## Load Mini App in WebView

```kotlin
MiniAppSDK.loadMiniAppInWebView(
    miniAppId = "com.gamma.finance",
    webView = webView
) { result ->
    result.onFailure { error ->
        // Show fallback UI
    }
}
```

## Architecture Overview

The SDK follows a clean, layered structure:

- `api/` Retrofit service definitions
- `data/` repository implementation + retry strategy
- `domain/` public models + internal contracts
- `ui/` internal `ViewPager2` banner adapter
- `analytics/` internal tracker
- `state/` internal runtime SDK state
- `MiniAppSDK.kt` public entrypoint

### Internal runtime flow

On `initWithAppID(...)`, SDK performs:

1) partner auth  
2) runtime mini app list fetch  
3) list cache write  
4) zip download + unzip for each mini app  
5) zip download metrics recording  

## Versioning Strategy

The SDK uses Semantic Versioning:

- `MAJOR`: breaking API changes
- `MINOR`: backward-compatible features
- `PATCH`: backward-compatible fixes

Current version: `1.0.0`

## License

MIT License. See `LICENSE`.
