# Flutter Android Usage

This is a minimal Dart usage example for channel `miniappsdk`.

## 1) Dart wrapper

```dart
import 'package:flutter/services.dart';

class MiniAppSdkBridge {
  static const MethodChannel _channel = MethodChannel('miniappsdk');

  static Future<void> initMiniAppSDK({
    required String appId,
    required String secretKey,
    required String domainUrl,
  }) async {
    await _channel.invokeMethod('initMiniAppSDK', {
      'appId': appId,
      'secretKey': secretKey,
      'domainUrl': domainUrl,
    });
  }

  static Future<List<dynamic>> fetchMiniApps() async {
    final result = await _channel.invokeMethod('fetchMiniApps');
    return (result as List?) ?? <dynamic>[];
  }

  static Future<void> openMiniApp(String miniAppId) async {
    await _channel.invokeMethod('openMiniApp', {'miniAppId': miniAppId});
  }
}
```

## 2) Example call flow

```dart
await MiniAppSdkBridge.initMiniAppSDK(
  appId: 'YOUR_APP_ID',
  secretKey: 'YOUR_SECRET_KEY',
  domainUrl: 'https://YOUR_BASE_URL/',
);
final apps = await MiniAppSdkBridge.fetchMiniApps();
if (apps.isNotEmpty) {
  await MiniAppSdkBridge.openMiniApp(apps.first['id'] as String);
}
```
