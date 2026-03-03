package com.digtitral.miniappsdk.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.digtitral.miniappsdk.MiniAppSDK
import com.digtitral.miniappsdk.R
import com.digtitral.miniappsdk.domain.model.MiniAppService

public class BannerView @JvmOverloads public constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val image: ImageView
    private val title: TextView
    private val description: TextView
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
    private var autoScrollRunnable: Runnable? = null

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_banner, this, true)

        image = findViewById(R.id.bannerImage)
        title = findViewById(R.id.bannerTitle)
        description = findViewById(R.id.bannerDescription)
    }

    public fun bind(service: MiniAppService): Unit {
        stopAutoScroll()
        title.text = service.title
        description.text = service.description

        Glide.with(context)
            .load(service.imageUrl)
            .into(image)

        MiniAppSDK.trackInternalEvent("Banner_Shown")
    }

    internal fun bindPager(
        items: List<MiniAppService>,
        viewPager: ViewPager2? = null,
        autoScroll: Boolean = true
    ): Unit {
        stopAutoScroll()
        removeAllViews()

        if (items.isEmpty()) {
            return
        }

        val pager = viewPager ?: ViewPager2(context).apply {
            layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            adapter = BannerPagerAdapter(items)
        }
        if (pager.adapter == null) {
            pager.adapter = BannerPagerAdapter(items)
        }
        addView(pager)
        MiniAppSDK.trackInternalEvent("Banner_Carousel_Shown")

        if (autoScroll && items.size > 1) {
            startAutoScroll(pager, items.size)
        }
    }

    private fun startAutoScroll(viewPager: ViewPager2, size: Int): Unit {
        autoScrollRunnable = object : Runnable {
            override fun run() {
                if (!isAttachedToWindow) {
                    return
                }
                val nextItem = (viewPager.currentItem + 1) % size
                viewPager.setCurrentItem(nextItem, true)
                mainHandler.postDelayed(this, 3_000)
            }
        }.also { runnable ->
            mainHandler.postDelayed(runnable, 3_000)
        }
    }

    private fun stopAutoScroll(): Unit {
        autoScrollRunnable?.let { runnable ->
            mainHandler.removeCallbacks(runnable)
        }
        autoScrollRunnable = null
    }

    override fun onDetachedFromWindow() {
        stopAutoScroll()
        super.onDetachedFromWindow()
    }
}
