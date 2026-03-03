package com.miniappsdk

/**
 * Public initialization contract for host applications.
 *
 * @property appId unique app identifier issued by backend.
 * @property baseUrl backend API base URL. Must include trailing slash.
 */
public data class MiniAppSDKConfig(
    public val appId: String,
    public val baseUrl: String = "https://yourapi.com/"
)
