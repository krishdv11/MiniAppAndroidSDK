package com.miniappsdk.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.miniappsdk.R
import com.miniappsdk.analytics.SDKAnalytics
import com.miniappsdk.domain.model.MiniAppService

public class BannerView @JvmOverloads public constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val image: ImageView
    private val title: TextView
    private val description: TextView

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_banner, this, true)

        image = findViewById(R.id.bannerImage)
        title = findViewById(R.id.bannerTitle)
        description = findViewById(R.id.bannerDescription)
    }

    public fun bind(service: MiniAppService): Unit {
        title.text = service.title
        description.text = service.description

        Glide.with(context)
            .load(service.imageUrl)
            .into(image)

        SDKAnalytics.trackEvent("Banner_Shown")
    }
}
