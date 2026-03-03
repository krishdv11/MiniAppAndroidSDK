# MiniApp Android SDK

`MiniApp Android SDK` is a lightweight, self-contained Android library from `digtitral` for fetching MiniApp services and rendering a ready-to-use banner UI component.

The SDK is host-app friendly:

- no DI framework requirement
- no Hilt requirement
- coroutine-based async API with safe callbacks
- pagination support
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
    implementation("com.github.krishdv11:MiniAppAndroidSDK:1.0.0")
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

## Fetch Services

```kotlin
MiniAppSDK.fetchMiniAppServices(page = 1) { result ->
    result.onSuccess { services ->
        // Use service list
    }.onFailure { throwable ->
        // Handle error
    }
}
```

## Fetch Services + Banner UI

```kotlin
MiniAppSDK.fetchMiniAppServicesWithUI(context = this, page = 1) { result ->
    result.onSuccess { (services, bannerView) ->
        container.addView(bannerView)
        // bannerView is already bound to the first service when available
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
- `ui/` reusable `BannerView`
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
