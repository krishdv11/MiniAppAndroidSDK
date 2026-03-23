package com.example.miniappsampleapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var btnOpenFetchMiniAppsScreen: Button
    private lateinit var btnOpenFetchMiniAppsWithUiScreen: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.tvStatus)
        btnOpenFetchMiniAppsScreen = findViewById(R.id.btnOpenFetchMiniAppsScreen)
        btnOpenFetchMiniAppsWithUiScreen = findViewById(R.id.btnOpenFetchMiniAppsWithUiScreen)

        status.text = getString(R.string.status_select_flow)

        btnOpenFetchMiniAppsScreen.setOnClickListener {
            startActivity(Intent(this, FetchMiniAppsActivity::class.java))
        }
        btnOpenFetchMiniAppsWithUiScreen.setOnClickListener {
            startActivity(Intent(this, FetchMiniAppsWithUiActivity::class.java))
        }
    }
}
