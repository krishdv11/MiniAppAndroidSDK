# React Native Android Usage

This is a minimal usage example for the Android bridge `MiniAppSdkModule`.

## 1) JS/TS module wrapper

```typescript
import { NativeModules } from "react-native";

const { MiniAppSdkModule } = NativeModules;

export async function initMiniAppSDK() {
  await MiniAppSdkModule.initMiniAppSDK(
    "YOUR_APP_ID",
    "YOUR_SECRET_KEY",
    "https://YOUR_BASE_URL/"
  );
}

export async function fetchMiniApps() {
  return MiniAppSdkModule.fetchMiniApps();
}

export async function openMiniApp(miniAppId: string) {
  await MiniAppSdkModule.openMiniApp(miniAppId);
}
```

## 2) Example call flow

```typescript
await initMiniAppSDK();
const miniApps = await fetchMiniApps();
if (miniApps.length > 0) {
  await openMiniApp(miniApps[0].id);
}
```
