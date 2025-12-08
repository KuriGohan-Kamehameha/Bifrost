package com.moonbench.bifrost.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.moonbench.bifrost.R

class BifrostAlertDialog {

    fun show(
        activity: AppCompatActivity,
        title: String,
        subtitle: String?,
        body: String?,
        positiveLabelResId: Int,
        negativeLabelResId: Int?,
        cancelable: Boolean,
        onConfirm: () -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val inflater = LayoutInflater.from(activity)
        val view = inflater.inflate(R.layout.dialog_bifrost_alert, null)

        val titleView = view.findViewById<TextView>(R.id.dialogTitle)
        val subtitleView = view.findViewById<TextView>(R.id.dialogSubtitle)
        val bodyView = view.findViewById<TextView>(R.id.dialogBody)

        titleView.text = title

        if (subtitle.isNullOrEmpty()) {
            subtitleView.visibility = View.GONE
        } else {
            subtitleView.visibility = View.VISIBLE
            subtitleView.text = subtitle
        }

        if (body.isNullOrEmpty()) {
            bodyView.visibility = View.GONE
        } else {
            bodyView.visibility = View.VISIBLE
            bodyView.text = body
        }

        val builder = AlertDialog.Builder(activity)
            .setView(view)
            .setPositiveButton(positiveLabelResId) { _, _ -> onConfirm() }
            .setCancelable(cancelable)

        if (negativeLabelResId != null) {
            builder.setNegativeButton(negativeLabelResId) { _, _ -> onCancel() }
        }

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCanceledOnTouchOutside(cancelable)
        dialog.show()
    }
}
