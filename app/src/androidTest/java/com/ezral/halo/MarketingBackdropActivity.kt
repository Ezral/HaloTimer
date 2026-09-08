package com.ezral.halo

import android.app.Activity
import android.os.Bundle
import android.graphics.*
import android.view.View

/** Separate test-APK app, never shipped in Halo. Original recipe backdrop for honest overlay captures. */
class MarketingBackdropActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(246, 243, 235)
        window.navigationBarColor = Color.rgb(246, 243, 235)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContentView(object : View(this) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(c: Canvas) {
                val s = width / 400f
                c.save(); c.scale(s, s)
                c.drawColor(Color.rgb(246, 243, 235))
                fun text(t: String, x: Float, y: Float, size: Float, bold: Boolean = false, color: Int = Color.rgb(34, 55, 48)) {
                    p.color = color; p.textSize = size; p.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
                    c.drawText(t, x, y, p)
                }
                text("S L O W   M O R N I N G S", 28f, 40f, 11f, true)
                p.color = Color.rgb(212, 216, 204); c.drawRect(28f, 60f, 372f, 61f, p)
                text("A better", 28f, 116f, 42f, true)
                text("morning cup.", 28f, 164f, 42f, true)
                text("A simple pour-over ritual.", 28f, 199f, 16f)
                p.color = Color.rgb(222, 227, 205); c.drawRoundRect(28f, 228f, 372f, 445f, 24f, 24f, p)
                p.color = Color.rgb(163, 173, 143); c.drawOval(102f, 385f, 300f, 420f, p)
                p.color = Color.rgb(245, 239, 222); c.drawRoundRect(267f, 286f, 319f, 359f, 24f, 24f, p)
                p.color = Color.rgb(222, 227, 205); c.drawRoundRect(277f, 300f, 307f, 346f, 13f, 13f, p)
                p.color = Color.rgb(250, 247, 235); c.drawRoundRect(125f, 280f, 282f, 397f, 36f, 36f, p)
                c.drawOval(125f, 267f, 282f, 307f, p)
                p.color = Color.rgb(112, 73, 49); c.drawOval(139f, 279f, 268f, 303f, p)
                text("01", 28f, 491f, 13f, true); text("Bloom", 72f, 492f, 21f, true)
                text("Wet the grounds. Let the coffee open up.", 72f, 519f, 13f)
                text("02", 28f, 568f, 13f, true); text("Slow pour", 72f, 569f, 21f, true)
                text("Pour gently, in small circles.", 72f, 596f, 13f)
                text("03", 28f, 645f, 13f, true); text("Enjoy", 72f, 646f, 21f, true)
                text("Take your time. You have earned it.", 72f, 673f, 13f)
                text("SAMPLE RECIPE  /  OVERLAY DEMONSTRATION", 28f, 752f, 9f, false, Color.rgb(111, 123, 108))
                c.restore()
            }
        })
    }
}
