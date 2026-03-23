package com.example.miniappsampleapp

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.digitral.miniappsdk.MiniAppSDK
import com.digitral.miniappsdk.domain.model.MiniAppService

class FetchMiniAppsActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var hostMiniAppsListContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fetch_miniapps)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        status = findViewById(R.id.tvStatus)
        hostMiniAppsListContainer = findViewById(R.id.hostMiniAppsListContainer)

        supportActionBar?.title = getString(R.string.mode_fetch_miniapps)
        initializeSdkAndLoad()
    }

    private fun initializeSdkAndLoad() {
        status.text = getString(R.string.status_initializing_sdk)
        SampleSdkInitializer.init(applicationContext)
            .onSuccess {
                renderHostUiUsingFetchMiniApps()
            }
            .onFailure { error ->
                status.text = "Init failed: ${error.message}"
                Toast.makeText(this, "Init failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun renderHostUiUsingFetchMiniApps() {
        status.text = getString(R.string.status_loading_host_ui)
        MiniAppSDK.fetchMiniApps { result ->
            runOnUiThread {
                result.onSuccess { services ->
                    hostMiniAppsListContainer.removeAllViews()
                    if (services.isEmpty()) {
                        status.text = getString(R.string.status_no_apps_found)
                        return@onSuccess
                    }
                    status.text = getString(R.string.status_host_ui_loaded, services.size)
                    services.forEach { service ->
                        hostMiniAppsListContainer.addView(buildHostMiniAppCard(service))
                    }
                }.onFailure { error ->
                    status.text = "Host UI load failed: ${error.message}"
                    Toast.makeText(this, "Host UI load failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun buildHostMiniAppCard(service: MiniAppService): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(
                this@FetchMiniAppsActivity,
                R.drawable.sample_header_card_bg
            )
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        }

        val titleView = TextView(this).apply {
            text = service.title
            textSize = 18f
            setTextColor(0xFF111111.toInt())
        }

        val descriptionView = TextView(this).apply {
            text = if (service.description.isBlank()) service.id else service.description
            textSize = 13f
            setTextColor(0xFF7A7A85.toInt())
            setPadding(0, dp(6), 0, 0)
        }

        val openButton = Button(this).apply {
            text = getString(R.string.open_mini_app)
            isAllCaps = false
            background = ContextCompat.getDrawable(
                this@FetchMiniAppsActivity,
                R.drawable.sample_mode_button_bg
            )
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
            setOnClickListener {
                status.text = "Opening: ${service.title}"
                MiniAppSDK.openMiniApp(service.id) { openResult ->
                    runOnUiThread {
                        openResult.onSuccess {
                            status.text = "Opened: ${service.title}"
                        }.onFailure { error ->
                            status.text = "Open failed: ${service.title} - ${error.message}"
                        }
                    }
                }
            }
        }

        container.addView(titleView)
        container.addView(descriptionView)
        container.addView(openButton)
        return container
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
