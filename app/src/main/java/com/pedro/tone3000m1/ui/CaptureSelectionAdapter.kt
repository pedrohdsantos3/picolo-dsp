package com.pedro.tone3000m1.ui

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.pedro.tone3000m1.R
import com.pedro.tone3000m1.domain.model.OnlineModel
import java.util.Locale

/** Rows for the capture chooser, with an instrument icon and useful model details. */
internal class CaptureSelectionAdapter(
    context: Context,
    models: List<OnlineModel>,
    private val iconResource: Int,
    private val accentColor: Int,
    private val instrumentLabel: String,
    private val showModelSize: Boolean = true,
) : ArrayAdapter<OnlineModel>(context, 0, models) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = (convertView as? LinearLayout) ?: createRow()
        val icon = row.getChildAt(0) as ImageView
        val labels = row.getChildAt(1) as LinearLayout
        val name = labels.getChildAt(0) as TextView
        val details = labels.getChildAt(1) as TextView
        val model = getItem(position) ?: return row

        icon.setImageDrawable(ContextCompat.getDrawable(context, iconResource))
        icon.setColorFilter(accentColor, PorterDuff.Mode.SRC_IN)
        name.text = model.name
        val size = model.size.trim().takeUnless {
            it.isEmpty() || it.equals("null", ignoreCase = true) || it.equals("unknown", ignoreCase = true)
        }
        details.text = buildString {
            if (showModelSize && size != null) append(size.uppercase(Locale.ROOT)).append(" · ")
            append("#").append(model.id)
        }
        row.contentDescription = "$instrumentLabel: ${model.name}, ${details.text}"
        return row
    }

    private fun createRow(): LinearLayout {
        val horizontalPadding = context.dp(18)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(horizontalPadding, context.dp(10), horizontalPadding, context.dp(10))
            minimumHeight = context.dp(72)
            val selectableBackground = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, selectableBackground, true)
            setBackgroundResource(selectableBackground.resourceId)
        }

        val icon = ImageView(context).apply {
            val iconPadding = context.dp(8)
            setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor((accentColor and 0x00FFFFFF) or 0x22000000)
            }
            contentDescription = instrumentLabel
        }
        row.addView(icon, LinearLayout.LayoutParams(context.dp(44), context.dp(44)))

        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(14), 0, 0, 0)
        }
        val name = TextView(context).apply {
            setTextColor(0xFFF4F7F9.toInt())
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val details = TextView(context).apply {
            setTextColor(0xFF9AA6AE.toInt())
            textSize = 12f
            maxLines = 1
        }
        labels.addView(name)
        labels.addView(details)
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
