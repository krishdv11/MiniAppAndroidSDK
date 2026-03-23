package com.example.miniappsampleapp

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.digitral.miniappsdk.MiniAppSDK
import com.digitral.miniappsdk.domain.model.MiniAppService

class FetchMiniAppsWithUiActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var uiOptionGroup: RadioGroup
    private lateinit var rbNameOnly: RadioButton
    private lateinit var rbNameIcon: RadioButton
    private lateinit var rbBanner: RadioButton
    private lateinit var miniAppUiContainer: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fetch_miniapps_with_ui)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        status = findViewById(R.id.tvStatus)
        uiOptionGroup = findViewById(R.id.uiOptionGroup)
        rbNameOnly = findViewById(R.id.rbNameOnly)
        rbNameIcon = findViewById(R.id.rbNameIcon)
        rbBanner = findViewById(R.id.rbBanner)
        miniAppUiContainer = findViewById(R.id.miniAppUiContainer)

        supportActionBar?.title = getString(R.string.mode_fetch_miniapps_with_ui)

        uiOptionGroup.setOnCheckedChangeListener { _, _ ->
            renderSdkUiForSelectedOption()
        }

        initializeSdkAndLoad()
    }

    private fun initializeSdkAndLoad() {
        status.text = getString(R.string.status_initializing_sdk)
        SampleSdkInitializer.init(applicationContext)
            .onSuccess {
                renderSdkUiForSelectedOption()
            }
            .onFailure { error ->
                status.text = "Init failed: ${error.message}"
                Toast.makeText(this, "Init failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun renderSdkUiForSelectedOption() {
        status.text = getString(R.string.status_loading_sdk_ui)
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
                    status.text = getString(R.string.status_sdk_ui_loaded)
                }.onFailure { error ->
                    status.text = "UI load failed: ${error.message}"
                    Toast.makeText(this, "UI load failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
