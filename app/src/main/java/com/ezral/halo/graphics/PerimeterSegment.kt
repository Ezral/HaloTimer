package com.ezral.halo.graphics

import android.graphics.Path
import android.graphics.PathMeasure

/** Extract each side of a closed-loop wrap into its own path.
 *
 * Some Skia versions replace getSegment's destination instead of appending to it.
 * A second extraction with startWithMoveTo=false can therefore lose the tail and
 * draw a chord from (0,0). Never depend on either destination-append behavior or
 * a current point surviving an extraction. addPath explicitly preserves both
 * contours, and their coincident endpoints keep the visible stroke continuous.
 */
internal fun perimeterSegment(measure: PathMeasure, destination: Path, wrapped: Path, start: Float, fraction: Float) {
    destination.rewind()
    wrapped.rewind()
    val length = measure.length
    if (!length.isFinite() || length <= 0f || !start.isFinite() || !fraction.isFinite() || fraction <= 0f) return
    if (fraction >= 1f) {
        if (measure.getSegment(0f, length, destination, true)) destination.close()
        return
    }
    // Normalize in double precision so adding 1 does not round a near-one phase
    // up to the next cycle before the short tail has been drawn.
    val from = (((start.toDouble() % 1.0) + 1.0) % 1.0).toFloat()
    val end = from + fraction
    measure.getSegment(from * length, minOf(end, 1f) * length, destination, true)
    if (end > 1f) {
        measure.getSegment(0f, (end - 1f) * length, wrapped, true)
        destination.addPath(wrapped)
    }
}
