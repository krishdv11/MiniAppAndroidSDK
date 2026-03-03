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
    appId = "your-app-id",
    baseUrl = "https://api.yourdomain.com/"
)
```

## Host App Requirement

Ensure the host app manifest includes internet access:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## Fetch Services

```kotlin
MiniAppSDK.fetchMiniAppServices { result ->
    result.onSuccess { services ->
        // Use service list
    }.onFailure { throwable ->
        // Handle error
    }
}
```

## Fetch Services + Banner UI

```kotlin
MiniAppSDK.fetchMiniAppServicesWithUI(context = this) { result ->
    result.onSuccess { (services, bannerPager) ->
        container.addView(bannerPager)
        // bannerPager is a ViewPager2 showing service banners
    }.onFailure { throwable ->
        // Handle error
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

## Versioning Strategy

The SDK uses Semantic Versioning:

- `MAJOR`: breaking API changes
- `MINOR`: backward-compatible features
- `PATCH`: backward-compatible fixes

Current version: `1.0.0`

## License

MIT License. See `LICENSE`.
