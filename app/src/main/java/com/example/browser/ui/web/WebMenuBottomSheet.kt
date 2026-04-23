package com.example.browser.ui.web

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import com.example.browser.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.corekit.core.report.ReportDataManager

class WebMenuBottomSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onBack()
        fun onForward()
        fun onRefresh()
        fun onShare()
        fun onNewTab()
        fun onOpenBookmarks()
        fun onAddBookmark()
        fun onOpenHistory()
        fun onOpenDownloads()
        fun onFindInPage()
        fun onToggleDesktopSite(enabled: Boolean)
        fun onAddToHomeScreen()
    }

    private var listener: Listener? = null
    private var onDismissCallback: (() -> Unit)? = null
    private var isBookmarked: Boolean = false
    private var canGoBack: Boolean = false
    private var canGoForward: Boolean = false
    private var isDesktopMode: Boolean = false
    private var currentUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isBookmarked = arguments?.getBoolean(ARG_IS_BOOKMARKED) ?: false
        canGoBack = arguments?.getBoolean(ARG_CAN_GO_BACK) ?: false
        canGoForward = arguments?.getBoolean(ARG_CAN_GO_FORWARD) ?: false
        isDesktopMode = arguments?.getBoolean(ARG_IS_DESKTOP_MODE) ?: false
        currentUrl = arguments?.getString(ARG_CURRENT_URL) ?: ""
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), theme)
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_web_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Top navigation bar
        val backAction = view.findViewById<ImageView>(R.id.action_back)
        val forwardAction = view.findViewById<ImageView>(R.id.action_forward)
        val shareAction = view.findViewById<View>(R.id.action_share)
        val refreshAction = view.findViewById<View>(R.id.action_refresh)

        // Menu items
        val newTabAction = view.findViewById<View>(R.id.action_new_tab)
        val openBookmarks = view.findViewById<View>(R.id.action_open_bookmarks)
        val addBookmark = view.findViewById<View>(R.id.action_add_bookmark)
        val addBookmarkIcon = view.findViewById<ImageView>(R.id.iv_add_bookmark_icon)
        val addBookmarkText = view.findViewById<TextView>(R.id.tv_add_bookmark)
        val openHistory = view.findViewById<View>(R.id.action_open_history)
        val downloadsAction = view.findViewById<View>(R.id.action_downloads)
        val findInPageAction = view.findViewById<View>(R.id.action_find_in_page)
        val desktopSiteAction = view.findViewById<View>(R.id.action_desktop_site)
        val desktopSiteSwitch = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switch_desktop_site)
        val addToHomeAction = view.findViewById<View>(R.id.action_add_to_home)

        // Update navigation button states
        backAction.isEnabled = canGoBack
        backAction.alpha = if (canGoBack) 1.0f else 0.3f
        backAction.setColorFilter(
            if (canGoBack) android.graphics.Color.BLACK else android.graphics.Color.GRAY
        )

        forwardAction.isEnabled = canGoForward
        forwardAction.alpha = if (canGoForward) 1.0f else 0.3f
        forwardAction.setColorFilter(
            if (canGoForward) android.graphics.Color.BLACK else android.graphics.Color.GRAY
        )

        // Update bookmark state - use star icon
        addBookmarkText.text = if (isBookmarked) {
            getString(R.string.bookmark_menu_added_state)
        } else {
            getString(R.string.web_menu_add)
        }
        addBookmarkIcon.setImageResource(
            if (isBookmarked) {
                R.mipmap.ic_star_filled
            } else {
                R.mipmap.ic_star_outline
            }
        )

        // Update desktop mode switch
        desktopSiteSwitch.isChecked = isDesktopMode

        // Top navigation listeners
        backAction.setOnClickListener {
            listener?.onBack()
            dismissAllowingStateLoss()
        }

        forwardAction.setOnClickListener {
            listener?.onForward()
            dismissAllowingStateLoss()
        }

        shareAction.setOnClickListener {
            listener?.onShare()
            dismissAllowingStateLoss()
        }

        refreshAction.setOnClickListener {
            listener?.onRefresh()
            dismissAllowingStateLoss()
        }

        // Menu item listeners
        newTabAction.setOnClickListener {
            listener?.onNewTab()
            dismissAllowingStateLoss()
        }

        openBookmarks.setOnClickListener {
            listener?.onOpenBookmarks()
            dismissAllowingStateLoss()
        }

        addBookmark.setOnClickListener {
            if (isBookmarked) {
                // Show confirmation dialog to remove bookmark
                showRemoveBookmarkConfirmation()
            } else {
                listener?.onAddBookmark()
                dismissAllowingStateLoss()
            }
        }

        openHistory.setOnClickListener {
            listener?.onOpenHistory()
            dismissAllowingStateLoss()
        }

        downloadsAction.setOnClickListener {
            listener?.onOpenDownloads()
            dismissAllowingStateLoss()
        }

        findInPageAction.setOnClickListener {
            listener?.onFindInPage()
            dismissAllowingStateLoss()
        }

        desktopSiteAction.setOnClickListener {
            desktopSiteSwitch.toggle()
        }

        desktopSiteSwitch.setOnCheckedChangeListener { _, isChecked ->
            listener?.onToggleDesktopSite(isChecked)
        }

        addToHomeAction.setOnClickListener {
            listener?.onAddToHomeScreen()
            dismissAllowingStateLoss()
        }
        ReportDataManager.reportData("dialog_show", mapOf("dialog_name" to javaClass.simpleName))
    }

    override fun dismiss() {
        super.dismiss()
        ReportDataManager.reportData("dialog_dismiss", mapOf("dialog_name" to javaClass.simpleName))
    }

    private fun showRemoveBookmarkConfirmation() {
        val dialog = com.example.browser.view.ConfirmDialog.show(
            supportFragmentManager = childFragmentManager,
            title = getString(R.string.bookmark_confirm_delete),
            content = getString(R.string.bookmark_confirm_delete_site_message),
            button = getString(R.string.bookmark_dialog_confirm)
        )
        dialog.setOnConfirmListener {
            listener?.onAddBookmark()  // Reuse same callback, let activity handle removal
            dismissAllowingStateLoss()
        }
        dialog.setOnCancelListener {
            // Do nothing
        }
    }

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    fun setOnDismissCallback(callback: () -> Unit) {
        onDismissCallback = callback
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 清空 listener 引用，避免内存泄漏
        listener = null
        onDismissCallback = null
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissCallback?.invoke()
    }

    companion object {
        private const val ARG_IS_BOOKMARKED = "arg_is_bookmarked"
        private const val ARG_CAN_GO_BACK = "arg_can_go_back"
        private const val ARG_CAN_GO_FORWARD = "arg_can_go_forward"
        private const val ARG_IS_DESKTOP_MODE = "arg_is_desktop_mode"
        private const val ARG_CURRENT_URL = "arg_current_url"

        fun newInstance(
            isBookmarked: Boolean,
            canGoBack: Boolean = false,
            canGoForward: Boolean = false,
            isDesktopMode: Boolean = false,
            currentUrl: String = ""
        ): WebMenuBottomSheet {
            return WebMenuBottomSheet().apply {
                arguments = bundleOf(
                    ARG_IS_BOOKMARKED to isBookmarked,
                    ARG_CAN_GO_BACK to canGoBack,
                    ARG_CAN_GO_FORWARD to canGoForward,
                    ARG_IS_DESKTOP_MODE to isDesktopMode,
                    ARG_CURRENT_URL to currentUrl
                )
            }
        }
    }
}
