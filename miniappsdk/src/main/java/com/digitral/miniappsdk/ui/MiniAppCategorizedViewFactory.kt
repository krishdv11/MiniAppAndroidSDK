package com.digitral.miniappsdk.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.digitral.miniappsdk.MiniAppSDK
import com.digitral.miniappsdk.domain.model.MiniAppService

internal object MiniAppCategorizedViewFactory {

    internal fun build(
        context: Context,
        services: List<MiniAppService>,
        option: MiniAppSDK.MiniAppUIOption,
        onMiniAppTapped: (MiniAppService) -> Unit
    ): View {
        val scroll = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isVerticalScrollBarEnabled = false
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 20))
        }
        scroll.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        groupedByCategory(services).forEach { (category, items) ->
            root.addView(categoryTitle(context, category, option))
            when (option) {
                MiniAppSDK.MiniAppUIOption.NAME_ONLY -> root.addView(verticalNameList(context, items, onMiniAppTapped))
                MiniAppSDK.MiniAppUIOption.NAME_WITH_ICON -> root.addView(horizontalIconList(context, items, onMiniAppTapped))
                MiniAppSDK.MiniAppUIOption.BANNER -> root.addView(horizontalBannerList(context, items, onMiniAppTapped))
            }
        }
        return scroll
    }

    private fun groupedByCategory(services: List<MiniAppService>): LinkedHashMap<String, List<MiniAppService>> {
        val grouped = LinkedHashMap<String, MutableList<MiniAppService>>()
        services.forEach { service ->
            val category = service.category.takeIf { it.isNotBlank() } ?: "Other"
            val list = grouped.getOrPut(category) { mutableListOf() }
            list += service
        }
        return LinkedHashMap(grouped.mapValues { it.value.toList() })
    }

    private fun categoryTitle(
        context: Context,
        category: String,
        option: MiniAppSDK.MiniAppUIOption
    ): TextView {
        return TextView(context).apply {
            text = category
            setTextColor(Color.parseColor("#111111"))
            setTypeface(typeface, Typeface.BOLD)
            textSize = if (option == MiniAppSDK.MiniAppUIOption.NAME_WITH_ICON) 16f else 22f
            setPadding(0, dp(context, 8), 0, if (option == MiniAppSDK.MiniAppUIOption.NAME_WITH_ICON) dp(context, 8) else dp(context, 10))
        }
    }

    private fun verticalNameList(
        context: Context,
        items: List<MiniAppService>,
        onMiniAppTapped: (MiniAppService) -> Unit
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            items.forEach { service ->
                addView(nameOnlyRow(context, service, onMiniAppTapped))
            }
        }
    }

    private fun nameOnlyRow(
        context: Context,
        service: MiniAppService,
        onMiniAppTapped: (MiniAppService) -> Unit
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded("#F7F7FB", strokeColor = "#DCDCE3", radiusDp = 14)
            isClickable = true
            isFocusable = true
            setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14))
            setOnClickListener { onMiniAppTapped(service) }
        }
        val textWrap = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        textWrap.addView(TextView(context).apply {
            text = service.title
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#111111"))
            textSize = 16f
        })
        textWrap.addView(TextView(context).apply {
            text = service.description.ifBlank { "v-" }
            setTextColor(Color.parseColor("#7A7A85"))
            textSize = 14f
            setPadding(0, dp(context, 4), 0, 0)
        })
        container.addView(textWrap)
        container.addView(TextView(context).apply {
            text = "›"
            textSize = 22f
            setTextColor(Color.parseColor("#C3C3CC"))
        })
        return container.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(context, 10) }
        }
    }

    private fun horizontalIconList(
        context: Context,
        items: List<MiniAppService>,
        onMiniAppTapped: (MiniAppService) -> Unit
    ): HorizontalScrollView {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(context, 2), 0, dp(context, 10))
        }
        items.forEach { service -> row.addView(quickLinkItem(context, service, onMiniAppTapped)) }
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(row)
        }
    }

    private fun quickLinkItem(
        context: Context,
        service: MiniAppService,
        onMiniAppTapped: (MiniAppService) -> Unit
    ): View {
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isFocusable = true
            setOnClickListener { onMiniAppTapped(service) }
        }
        item.addView(initialsCircle(context, service))
        item.addView(TextView(context).apply {
            text = service.title
            setTextColor(Color.parseColor("#5C5C66"))
            textSize = 13f
            setPadding(0, dp(context, 10), 0, 0)
            maxLines = 1
            gravity = Gravity.CENTER
        })
        return item.apply {
            layoutParams = LinearLayout.LayoutParams(dp(context, 92), ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.rightMargin = dp(context, 16)
                it.bottomMargin = dp(context, 6)
            }
        }
    }

    private fun horizontalBannerList(
        context: Context,
        items: List<MiniAppService>,
        onMiniAppTapped: (MiniAppService) -> Unit
    ): HorizontalScrollView {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        items.forEach { service -> row.addView(bannerCard(context, service, onMiniAppTapped)) }
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(row)
        }
    }

    private fun bannerCard(
        context: Context,
        service: MiniAppService,
        onMiniAppTapped: (MiniAppService) -> Unit
    ): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded("#F7F7FB", strokeColor = "#DCDCE3", radiusDp = 14)
            isClickable = true
            isFocusable = true
            setOnClickListener { onMiniAppTapped(service) }
        }
        val imageArea = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 130)
            )
            background = rounded("#5D8EE7", radiusDp = 10)
        }
        imageArea.addView(TextView(context).apply {
            text = initials(service.title)
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 22f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        })
        card.addView(imageArea)
        card.addView(TextView(context).apply {
            text = service.title
            setTextColor(Color.parseColor("#111111"))
            setTypeface(typeface, Typeface.BOLD)
            textSize = 16f
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 4))
        })
        card.addView(TextView(context).apply {
            text = service.description.ifBlank { "v-" }
            setTextColor(Color.parseColor("#7A7A85"))
            textSize = 14f
            setPadding(dp(context, 12), 0, dp(context, 12), dp(context, 12))
        })
        return card.apply {
            layoutParams = LinearLayout.LayoutParams(dp(context, 280), ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.rightMargin = dp(context, 12)
                it.bottomMargin = dp(context, 8)
            }
        }
    }

    private fun initialsCircle(context: Context, service: MiniAppService): View {
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(context, 60), dp(context, 60))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#5D8EE7"))
            }
            clipToOutline = true
            if (service.imageUrl.isNotBlank()) {
                addView(ImageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    Glide.with(context)
                        .load(service.imageUrl)
                        .circleCrop()
                        .into(this)
                })
            }
            addView(TextView(context).apply {
                text = initials(service.title)
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                textSize = 18f
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            })
        }
    }

    private fun initials(text: String): String {
        val parts = text.split(" ").filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "MA"
            parts.size == 1 -> parts.first().take(2).uppercase()
            else -> "${parts[0].first()}${parts[1].first()}".uppercase()
        }
    }

    private fun rounded(
        fillColor: String,
        strokeColor: String? = null,
        radiusDp: Int = 12
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp.toFloat()
            setColor(Color.parseColor(fillColor))
            if (strokeColor != null) {
                setStroke(2, Color.parseColor(strokeColor))
            }
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
