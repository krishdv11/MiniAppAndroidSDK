package com.digitral.miniappsdk.domain.model

// Public model exposed to host applications.

public data class MiniAppService(
    public val id: String,
    public val title: String,
    public val description: String,
    public val imageUrl: String,
    public val category: String = ""
)
