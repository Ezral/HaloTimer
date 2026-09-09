package com.ezral.halo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Visual HSV palette, with accessible sliders and optional exact hex entry. */
@Composable
internal fun LineColorPicker(initial: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    val hsv = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toInt(), it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(hsv[2]) }
    val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)).toLong() and 0xFFFFFFFFL
    var hex by remember(color) { mutableStateOf("#%06X".format(color and 0xFFFFFF)) }
    fun parseHex() = hex.removePrefix("#").takeIf { it.length == 6 && it.all { c -> c in '0'..'9' || c in 'A'..'F' || c in 'a'..'f' } }?.toLongOrNull(16)?.let { it or 0xFF000000L }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Custom line color") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(14.dp)).background(Color(color)))
            Canvas(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp))
                .semantics { contentDescription = "Color palette. Adjust saturation and brightness here or use the sliders below." }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun select(p: Offset) { saturation = (p.x / size.width).coerceIn(0f, 1f); brightness = (1 - p.y / size.height).coerceIn(0f, 1f) }
                        select(down.position); down.consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.firstOrNull { it.id == down.id }?.let { select(it.position); it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))))))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val center = Offset(saturation * size.width, (1 - brightness) * size.height)
                drawCircle(Color.Black, 8.dp.toPx(), center, style = Stroke(4.dp.toPx()))
                drawCircle(Color.White, 8.dp.toPx(), center, style = Stroke(2.dp.toPx()))
            }
            Text("Hue")
            Box {
                Box(Modifier.fillMaxWidth().padding(top = 22.dp).height(4.dp).background(Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))))
                Slider(hue, { hue = it }, valueRange = 0f..360f, modifier = Modifier.semantics { contentDescription = "Color hue" },
                    colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent))
            }
            Text("Saturation")
            Slider(saturation, { saturation = it }, modifier = Modifier.semantics { contentDescription = "Color saturation" })
            Text("Brightness")
            Slider(brightness, { brightness = it }, modifier = Modifier.semantics { contentDescription = "Color brightness" })
            OutlinedTextField(hex, { if (it.length <= 7) hex = it }, singleLine = true, label = { Text("Hex (optional)") },
                isError = parseHex() == null, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Custom color input" })
        }
    }, confirmButton = { TextButton(onClick = { parseHex()?.let(onSave) }, enabled = parseHex() != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
