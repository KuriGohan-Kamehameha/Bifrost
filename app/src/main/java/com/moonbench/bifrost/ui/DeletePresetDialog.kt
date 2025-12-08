package com.moonbench.bifrost.ui

import androidx.appcompat.app.AppCompatActivity
import com.moonbench.bifrost.R

class DeletePresetDialog(
    private val alertDialog: BifrostAlertDialog = BifrostAlertDialog()
) {

    fun show(
        activity: AppCompatActivity,
        presetName: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val title = activity.getString(R.string.delete_preset_title)
        val subtitle = activity.getString(R.string.delete_preset_subtitle, presetName)
        val body = activity.getString(R.string.delete_preset_body)

        alertDialog.show(
            activity = activity,
            title = title,
            subtitle = subtitle,
            body = body,
            positiveLabelResId = R.string.action_delete,
            negativeLabelResId = R.string.action_cancel,
            cancelable = true,
            onConfirm = onConfirm,
            onCancel = onCancel
        )
    }
}
