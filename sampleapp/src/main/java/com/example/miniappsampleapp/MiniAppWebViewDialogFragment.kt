package com.example.miniappsampleapp

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.digitral.miniappsdk.MiniAppSDK

class MiniAppWebViewDialogFragment : DialogFragment() {

    private var webView: WebView? = null
    private var errorTextView: TextView? = null
    private var closeButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setCanceledOnTouchOutside(false)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_mini_app_webview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        webView = view.findViewById(R.id.webMiniApp)
        errorTextView = view.findViewById(R.id.tvLoadError)
        closeButton = view.findViewById(R.id.btnCloseMiniApp)

        closeButton?.setOnClickListener { dismissAllowingStateLoss() }
        loadMiniApp()
    }

    private fun loadMiniApp() {
        val miniAppId = arguments?.getString(ARG_MINI_APP_ID).orEmpty()
        val targetWebView = webView ?: return
        if (miniAppId.isBlank()) {
            publishLoadResult(false, "miniAppId is empty")
            showError("Invalid mini app id")
            return
        }

        MiniAppSDK.loadMiniAppInWebView(
            miniAppId = miniAppId,
            webView = targetWebView
        ) { result ->
            result.onSuccess {
                publishLoadResult(true, null)
            }.onFailure { error ->
                val message = error.message ?: "Unknown error"
                publishLoadResult(false, message)
                showError("Mini app load failed: $message")
            }
        }
    }

    private fun showError(message: String) {
        activity?.runOnUiThread {
            errorTextView?.visibility = View.VISIBLE
            errorTextView?.text = message
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun publishLoadResult(success: Boolean, errorMessage: String?) {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            bundleOf(
                KEY_SUCCESS to success,
                KEY_ERROR_MESSAGE to errorMessage
            )
        )
    }

    override fun onDestroyView() {
        webView?.stopLoading()
        webView?.loadUrl("about:blank")
        webView = null
        errorTextView = null
        closeButton = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_MINI_APP_ID = "arg_mini_app_id"
        const val TAG = "MiniAppWebViewDialog"
        const val REQUEST_KEY = "mini_app_load_request"
        const val KEY_SUCCESS = "success"
        const val KEY_ERROR_MESSAGE = "error_message"

        fun newInstance(miniAppId: String): MiniAppWebViewDialogFragment {
            return MiniAppWebViewDialogFragment().apply {
                arguments = bundleOf(ARG_MINI_APP_ID to miniAppId)
            }
        }
    }
}
