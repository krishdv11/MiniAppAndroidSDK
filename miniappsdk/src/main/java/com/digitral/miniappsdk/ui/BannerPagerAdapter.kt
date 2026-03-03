package com.digitral.miniappsdk.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.digitral.miniappsdk.MiniAppSDK
import com.digitral.miniappsdk.R
import com.digitral.miniappsdk.domain.model.MiniAppService

internal class BannerPagerAdapter(
    private val services: List<MiniAppService>
) : RecyclerView.Adapter<BannerPagerAdapter.BannerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.view_banner, parent, false)
        return BannerViewHolder(view)
    }

    override fun onBindViewHolder(holder: BannerViewHolder, position: Int): Unit {
        val service = services[position]
        holder.title.text = service.title
        holder.description.text = service.description
        Glide.with(holder.itemView.context)
            .load(service.imageUrl)
            .into(holder.image)
        MiniAppSDK.trackInternalEvent("Banner_Shown")
    }

    override fun getItemCount(): Int = services.size

    internal class BannerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.bannerImage)
        val title: TextView = itemView.findViewById(R.id.bannerTitle)
        val description: TextView = itemView.findViewById(R.id.bannerDescription)
    }
}
