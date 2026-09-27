package com.ryanhami.mosslightstudio

import android.content.Context
import android.graphics.Canvas
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.ryanhami.mosslightstudio.core.*
import com.ryanhami.mosslightstudio.render.StudioRenderer
import java.util.concurrent.atomic.AtomicLong

class StudioGameView(context: Context, val session: StudioSession, private val onCell: (Cell) -> Unit) :
    View(context), DefaultLifecycleObserver {
    private val token = tokens.incrementAndGet()
    private var renderer: StudioRenderer? = null
    private val choreographer = Choreographer.getInstance()
    private var lifecycle: Lifecycle? = null
    private var resumed = false
    private var owns = false
    private var posted = false
    private val callback = Choreographer.FrameCallback { frame(it) }

    init { contentDescription = "Conservatory scene. Use the directional controls to explore, or Arrange to place objects."; isFocusable = false }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (renderer == null) renderer = StudioRenderer()
        lifecycle = findViewTreeLifecycleOwner()?.lifecycle
        lifecycle?.addObserver(this)
        resumed = lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
        start()
    }
    override fun onDetachedFromWindow() {
        stop(); lifecycle?.removeObserver(this); lifecycle = null; resumed = false; renderer?.release(); renderer = null
        super.onDetachedFromWindow()
    }
    override fun onResume(owner: LifecycleOwner) { resumed = true; start() }
    override fun onPause(owner: LifecycleOwner) { resumed = false; stop() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); if (hasWindowFocus) start() else stop() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); if (visibility == VISIBLE) start() else stop() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); if (visibility == VISIBLE) start() else stop() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { session.neutralize(); if (w > 0 && h > 0) start() else stop() }
    override fun onDraw(canvas: Canvas) { super.onDraw(canvas); if (width > 0 && height > 0) renderer?.draw(canvas, width, height, session.frame()) }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (session.selected == null) return false
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            if (width > 0 && height > 0) {
                val framing = StudioFraming.fit(PixelSize(width, height), arranging = true)
                framing.physicalToRoom(PhysicalPoint(event.x.toDouble(), event.y.toDouble()))?.let {
                    onCell(Cell((it.x / RoomRules.TILE).toInt(), (it.y / RoomRules.TILE).toInt()))
                }
            }
            performClick()
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    private fun eligible() = isAttachedToWindow && isShown && resumed && hasWindowFocus() && windowVisibility == VISIBLE && width > 0 && height > 0
    private fun start() {
        if (owns || !eligible() || !session.acquire(token)) return
        owns = true; post()
    }
    private fun stop() {
        if (posted) choreographer.removeFrameCallback(callback)
        posted = false
        if (owns) session.release(token) else session.neutralize()
        owns = false
    }
    private fun frame(nanos: Long) {
        posted = false
        if (!owns || !eligible()) { stop(); return }
        session.advance(token, nanos); invalidate(); post()
    }
    private fun post() { if (!posted && owns) { posted = true; choreographer.postFrameCallback(callback) } }
    companion object { private val tokens = AtomicLong() }
}
