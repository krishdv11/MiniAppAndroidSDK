package com.example.miniappsampleapp

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.digitral.miniappsdk.MiniAppSDK
import com.digitral.miniappsdk.domain.model.MiniAppService

class MainActivity : AppCompatActivity() {

    private lateinit var initButton: Button
    private lateinit var cacheButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var listView: ListView

    private var cachedMiniApps: List<MiniAppService> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initButton = findViewById(R.id.btnInitSdk)
        cacheButton = findViewById(R.id.btnLoadCache)
        progress = findViewById(R.id.progressBar)
        status = findViewById(R.id.tvStatus)
        listView = findViewById(R.id.listMiniApps)

        initButton.setOnClickListener { initializeSdk() }
        cacheButton.setOnClickListener { loadCachedMiniApps() }
        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = cachedMiniApps[position]
            loadMiniApp(selected.id)
        }

        // Initialize SDK when app launches so sync starts immediately.
        initializeSdk()
    }

    private fun initializeSdk() {
        setLoading(true)
        status.text = "Initializing SDK..."
        try {
            MiniAppSDK.initWith(
                context = applicationContext,
                appId = "p_a1b2c3d4",
                secretKey = "partner-signature",
                domainUrl = "https://csdpdev-api.d21.co.in/"
            )
            setLoading(false)
            status.text = "SDK initialized. Syncing cache in background."
        } catch (e: Exception) {
            setLoading(false)
            status.text = "Init failed: ${e.message}"
            Log.e("SampleApp", "SDK init failed", e)
            Toast.makeText(this, "Init failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadCachedMiniApps() {
        setLoading(true)
        status.text = "Loading cached mini apps..."
        MiniAppSDK.fetchMiniApps { result ->
            result.onSuccess { list ->
                runOnUiThread {
                    cachedMiniApps = list
                    setLoading(false)
                    status.text = "Cached mini apps: ${list.size}"
                    listView.adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_list_item_1,
                        list.map { "${it.title} (${it.id})" }
                    )
                }
            }.onFailure { error ->
                runOnUiThread {
                    setLoading(false)
                    status.text = "Cache load failed: ${error.message}"
                    Log.e("SampleApp", "Cache load failed", error)
                    Toast.makeText(this, "Cache load failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadMiniApp(miniAppId: String) {
        setLoading(true)
        status.text = "Opening mini app..."
        MiniAppSDK.openMiniApp(miniAppId) { result ->
            runOnUiThread {
                setLoading(false)
                result.onSuccess {
                    status.text = "Mini app opened"
                }.onFailure { error ->
                    status.text = "Mini app open failed: ${error.message}"
                    Toast.makeText(
                        this,
                        "Mini app open failed: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
    }

}
