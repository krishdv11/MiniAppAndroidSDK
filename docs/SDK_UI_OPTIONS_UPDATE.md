# SDK UI Options Update Instructions

This document describes the UI enhancement update requested for category-wise rendering and multi-style SDK UI output.

## Goal

Enable `fetchMiniAppsWithUI` to return 3 UI styles while preserving existing SDK behavior:

1. **Name only** (`NAME_ONLY`)
2. **Name + icon cards** (`NAME_WITH_ICON`)
3. **Banner cards** (`BANNER`)

Additionally:

- Group mini apps by category.
- Keep callbacks for click/open success/open failure.
- Ensure WebView callback is based on page load result.

## API Additions

In `MiniAppSDK`:

- `MiniAppUIOption` enum:
  - `NAME_ONLY`
  - `NAME_WITH_ICON`
  - `BANNER`
- `MiniAppUICallback` interface:
  - `onMiniAppClicked(service)`
  - `onMiniAppOpenSuccess(service)`
  - `onMiniAppOpenFailure(service, error)`

`fetchMiniAppsWithUI` overloads:

- Existing: `fetchMiniAppsWithUI(callback)` (backward compatibility)
- New: `fetchMiniAppsWithUI(option, callback)`
- New: `fetchMiniAppsWithUI(option, uiCallback, callback)`

## Rendering Rules

### Category grouping

- Group services by `service.category`.
- Keep category display order based on first appearance from API result.

### Option 1: Name only

- Category title
- Vertical list (no horizontal scroll)
- Item shows title + version/description

### Option 2: Name + icon

- Category title
- Horizontal scroll quick-link items under each category
- Icon is circular
- Item shows circular icon + name

### Option 3: Banner

- Category title
- Horizontal banner cards under each category
- Card shows large image + title + version/description

## Callback Behavior

When SDK-provided UI item is tapped:

1. Trigger `onMiniAppClicked`.
2. Call `openMiniApp(miniAppId)`.
3. On result:
   - success -> `onMiniAppOpenSuccess`
   - failure -> `onMiniAppOpenFailure`

## WebView Callback Reliability

In direct WebView path (`openApp(miniAppId, webView, callback)`):

- Verify session token before load.
- Return success from `onPageFinished`.
- Return failure from main-frame `onReceivedError`.
- Prevent duplicate callback dispatch.

## Files Updated

- `miniappsdk/src/main/java/com/digitral/miniappsdk/MiniAppSDK.kt`
- `miniappsdk/src/main/java/com/digitral/miniappsdk/ui/MiniAppCategorizedViewFactory.kt`
- `miniappsdk/src/main/java/com/digitral/miniappsdk/domain/model/MiniAppService.kt`
- `miniappsdk/src/main/java/com/digitral/miniappsdk/data/MiniAppRepositoryImpl.kt`
- `sampleapp/src/main/java/com/example/miniappsampleapp/MainActivity.kt`
- `sampleapp/src/main/res/layout/activity_main.xml`
- `sampleapp/src/main/res/values/strings.xml`

## Validation Checklist

- `fetchMiniAppsWithUI(NAME_ONLY)` returns category-wise vertical lists.
- `fetchMiniAppsWithUI(NAME_WITH_ICON)` returns category-wise horizontal icon cards.
- `fetchMiniAppsWithUI(BANNER)` returns category-wise horizontal banners.
- Tapping any item opens mini app container path.
- UI callback methods are invoked correctly.
- Session-token verification still occurs before WebView load.
- Existing APIs (`initWith`, `fetchMiniApps`, `openMiniApp`) continue working.
