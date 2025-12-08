package com.moonbench.bifrost.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.moonbench.bifrost.R

class ColorPickerDialog {

    fun show(
        activity: AppCompatActivity,
        initialColor: Int,
        onColorSelected: (Int) -> Unit
    ) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val preview = dialogView.findViewById<View>(R.id.colorPreview)
        val seekR = dialogView.findViewById<SeekBar>(R.id.seekRed)
        val seekG = dialogView.findViewById<SeekBar>(R.id.seekGreen)
        val seekB = dialogView.findViewById<SeekBar>(R.id.seekBlue)

        seekR.max = 255
        seekG.max = 255
        seekB.max = 255
        seekR.progress = Color.red(initialColor)
        seekG.progress = Color.green(initialColor)
        seekB.progress = Color.blue(initialColor)

        fun updatePreview() {
            val color = Color.rgb(seekR.progress, seekG.progress, seekB.progress)
            setPreviewColor(preview, color)
        }

        updatePreview()

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                updatePreview()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        }

        seekR.setOnSeekBarChangeListener(listener)
        seekG.setOnSeekBarChangeListener(listener)
        seekB.setOnSeekBarChangeListener(listener)

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val color = Color.rgb(seekR.progress, seekG.progress, seekB.progress)
                onColorSelected(color)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.window?.setBackgroundDrawable(
            ContextCompat.getDrawable(activity, android.R.color.transparent)
        )
        dialog.show()
    }

    private fun setPreviewColor(view: View, color: Int) {
        val bg = view.background.mutate() as LayerDrawable
        val colorLayer = bg.findDrawableByLayerId(R.id.color_layer) as GradientDrawable
        colorLayer.setColor(color)
    }
}
