package com.example.miniappsampleapp

import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.digitral.miniappsdk.MiniAppSDK
import com.digitral.miniappsdk.domain.model.MiniAppService

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var uiOptionGroup: RadioGroup
    private lateinit var rbNameOnly: RadioButton
    private lateinit var rbNameIcon: RadioButton
    private lateinit var rbBanner: RadioButton
    private lateinit var miniAppUiContainer: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.tvStatus)
        uiOptionGroup = findViewById(R.id.uiOptionGroup)
        rbNameOnly = findViewById(R.id.rbNameOnly)
        rbNameIcon = findViewById(R.id.rbNameIcon)
        rbBanner = findViewById(R.id.rbBanner)
        miniAppUiContainer = findViewById(R.id.miniAppUiContainer)

        uiOptionGroup.setOnCheckedChangeListener { _, _ ->
            renderSdkUiForSelectedOption()
        }

        // Initialize SDK when app launches so sync starts immediately.
        initializeSdk()
    }

    private fun initializeSdk() {
        status.text = "Initializing SDK..."
        try {
            MiniAppSDK.initWith(
                context = applicationContext,
                appId = "p_a1b2c3d4",
                secretKey = "partner-signature",
                domainUrl = "https://csdpdev-api.d21.co.in/"
            )
            status.text = "SDK initialized. Syncing cache in background."
            renderSdkUiForSelectedOption()
        } catch (e: Exception) {
            status.text = "Init failed: ${e.message}"
            Log.e("SampleApp", "SDK init failed", e)
            Toast.makeText(this, "Init failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderSdkUiForSelectedOption() {
        val option = when {
            rbNameOnly.isChecked -> MiniAppSDK.MiniAppUIOption.NAME_ONLY
            rbNameIcon.isChecked -> MiniAppSDK.MiniAppUIOption.NAME_WITH_ICON
            else -> MiniAppSDK.MiniAppUIOption.BANNER
        }
        MiniAppSDK.fetchMiniAppsWithUI(
            option = option,
            uiCallback = object : MiniAppSDK.MiniAppUICallback {
                override fun onMiniAppClicked(service: MiniAppService) {
                    runOnUiThread { status.text = "Opening: ${service.title}" }
                }

                override fun onMiniAppOpenSuccess(service: MiniAppService) {
                    runOnUiThread { status.text = "Opened: ${service.title}" }
                }

                override fun onMiniAppOpenFailure(service: MiniAppService, error: Throwable) {
                    runOnUiThread {
                        status.text = "Open failed: ${service.title} - ${error.message}"
                    }
                }
            }
        ) { result ->
            runOnUiThread {
                result.onSuccess { uiView ->
                    miniAppUiContainer.removeAllViews()
                    miniAppUiContainer.addView(uiView)
                }.onFailure { error ->
                    status.text = "UI load failed: ${error.message}"
                    Toast.makeText(this, "UI load failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
