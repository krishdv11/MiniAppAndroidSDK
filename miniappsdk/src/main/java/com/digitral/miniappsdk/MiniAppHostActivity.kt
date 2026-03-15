package com.digitral.miniappsdk

import android.app.ActivityManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.TextView
import android.app.Activity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

internal class MiniAppHostActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var webView: WebView
    private lateinit var closeButton: Button
    private lateinit var errorText: TextView

    private var miniAppId: String = ""
    private var miniAppTitle: String = ""
    private var miniAppIconUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.miniapp_activity_host)

        webView = findViewById(R.id.webMiniApp)
        closeButton = findViewById(R.id.btnCloseMiniApp)
        errorText = findViewById(R.id.tvMiniAppError)

        closeButton.setOnClickListener { finish() }
        handleLaunchIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: android.content.Intent?) {
        miniAppId = intent?.getStringExtra(EXTRA_MINI_APP_ID).orEmpty()
        miniAppTitle = intent?.getStringExtra(EXTRA_MINI_APP_TITLE).orEmpty().ifBlank { miniAppId }
        miniAppIconUrl = intent?.getStringExtra(EXTRA_MINI_APP_ICON_URL).orEmpty()
        if (miniAppId.isBlank()) {
            showError("Invalid mini app id")
            return
        }
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.visibility = View.GONE
        errorText.visibility = View.GONE
        applySeparateTaskPresentation()
        checkPermissionsAndLaunch()
    }

    private fun applySeparateTaskPresentation() {
        if (miniAppTitle.isNotBlank()) {
            title = miniAppTitle
            @Suppress("DEPRECATION")
            setTaskDescription(ActivityManager.TaskDescription(miniAppTitle))
            if (miniAppIconUrl.isNotBlank()) {
                scope.launch {
                    val iconBitmap = withContext(Dispatchers.IO) {
                        loadTaskIcon(miniAppIconUrl)
                    }
                    if (iconBitmap != null) {
                        @Suppress("DEPRECATION")
                        setTaskDescription(ActivityManager.TaskDescription(miniAppTitle, iconBitmap, 0))
                    }
                }
            }
        }
    }

    private fun loadTaskIcon(url: String): Bitmap? {
        return try {
            URL(url).openStream().use { input -> BitmapFactory.decodeStream(input) }
        } catch (_: Exception) {
            null
        }
    }

    private fun checkPermissionsAndLaunch() {
        val requiredPermissions = MiniAppSDK.getRequiredPermissions(miniAppId)
        val missingPermissions = requiredPermissions.filterNot { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            scope.launch { MiniAppSDK.trackLifecycleEvent(miniAppId, "PermissionGranted") }
            launchMiniApp()
            return
        }
        ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), REQUEST_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return
        val denied = grantResults.any { it != PackageManager.PERMISSION_GRANTED }
        if (denied) {
            scope.launch { MiniAppSDK.trackLifecycleEvent(miniAppId, "PermissionDenied") }
            showError("Required permissions denied")
            return
        }
        scope.launch { MiniAppSDK.trackLifecycleEvent(miniAppId, "PermissionGranted") }
        launchMiniApp()
    }

    private fun launchMiniApp() {
        scope.launch {
            try {
                MiniAppSDK.trackLifecycleEvent(miniAppId, "AppLaunched")
                MiniAppSDK.trackLifecycleEvent(miniAppId, "BridgeConnected")
                webView.visibility = View.VISIBLE
                errorText.visibility = View.GONE
                MiniAppSDK.openApp(miniAppId, webView) { result ->
                    result.onFailure { showError(it.message ?: "Failed to open mini app") }
                }
            } catch (e: Exception) {
                showError(e.message ?: "Failed to open mini app")
            }
        }
    }

    private fun showError(message: String) {
        webView.visibility = View.GONE
        errorText.visibility = View.VISIBLE
        errorText.text = message
    }

    override fun onDestroy() {
        scope.launch {
            MiniAppSDK.trackLifecycleEvent(miniAppId, "AppClosed")
            MiniAppSDK.trackLifecycleEvent(miniAppId, "BridgeDisconnected")
        }
        webView.stopLoading()
        webView.loadUrl("about:blank")
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        internal const val EXTRA_MINI_APP_ID = "extra_mini_app_id"
        internal const val EXTRA_MINI_APP_TITLE = "extra_mini_app_title"
        internal const val EXTRA_MINI_APP_ICON_URL = "extra_mini_app_icon_url"
        private const val REQUEST_PERMISSIONS = 1101
    }
}
