package com.ryanhami.mosslightstudio

import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ryanhami.mosslightstudio.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StudioPlatformTest {
    @Test fun arrangeTouchUsesRenderedFloorCoordinates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitReady(scenario)
            scenario.onActivity { it.model.open(Panel.ARRANGE) }
            SystemClock.sleep(300)
            scenario.onActivity { activity ->
                val view = findGameView(activity.window.decorView)!!
                val fit = StudioFraming.fit(PixelSize(view.width, view.height), arranging = true)
                val point = fit.roomToPhysical(LogicalPoint(152.0, 168.0))
                val now = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(now, now, action, point.x.toFloat(), point.y.toFloat(), 0)
                    try { assertTrue(view.dispatchTouchEvent(event)) } finally { event.recycle() }
                }
                assertEquals(Cell(9, 10), activity.model.ui.value.preview)
            }
        }
    }

    @Test fun recreationRetainsSessionAndReplacesFrameOwner() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitReady(scenario)
            var previous: StudioSession? = null
            scenario.onActivity { activity -> previous = activity.model.session; assertEquals(1, previous!!.ownerCount); previous!!.move(1.0, 0.0) }
            scenario.recreate()
            awaitReady(scenario)
            scenario.onActivity { activity ->
                assertSame(previous, activity.model.session)
                assertEquals(1, previous!!.ownerCount)
                assertFalse(previous!!.isMoving)
                val view = findGameView(activity.window.decorView)!!
                view.onWindowFocusChanged(false)
                assertEquals(0, previous!!.ownerCount)
                previous!!.move(-1.0, 0.0)
                assertFalse(previous!!.isMoving)
                view.onWindowFocusChanged(true)
                assertEquals(1, previous!!.ownerCount)
            }
        }
    }

    @Test fun printPanelReleasesWorldAndShareClaimIsExactlyOnce() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitReady(scenario)
            scenario.onActivity { it.model.open(Panel.PRINT) }
            SystemClock.sleep(300)
            scenario.onActivity { activity ->
                assertEquals(0, activity.model.session!!.ownerCount)
                activity.model.stampAt(1, 2)
                activity.model.requestShare()
                val request = activity.model.ui.value.shareRequest!!
                assertTrue(activity.model.claimShare(request.id))
                assertFalse(activity.model.claimShare(request.id))
                assertFalse(activity.model.claimShare(request.id + 1))
                activity.model.shareFinished(request.id, false)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(1, activity.model.ui.value.print.marks.size)
                assertNull(activity.model.ui.value.shareRequest)
                activity.model.open(Panel.ROOM)
            }
            SystemClock.sleep(300)
            scenario.onActivity { assertEquals(1, it.model.session!!.ownerCount) }
        }
    }

    @Test fun generatedImageHasOnlyCacheUriAndPixelsMatchComposition() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<StudioApplication>()
        val design = PrintDesign().add(Stamp.LEAF, 0, 0).add(Stamp.STAR, 3, 3).add(Stamp.DROP, 5, 5)
        val intent = PrintExport.prepare(context, design)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/png", intent.type)
        val uri = intent.clipData!!.getItemAt(0).uri
        assertEquals("${context.packageName}.prints", uri.authority)
        assertTrue(uri.path!!.startsWith("/prints/"))
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertNull(intent.`package`)
        assertNull(intent.component)
        val bitmap = PrintExport.render(design)
        try {
            assertEquals(PrintPixels.WIDTH * 4, bitmap.width)
            assertEquals(PrintPixels.HEIGHT * 4, bitmap.height)
            assertEquals(Color.alpha(bitmap.getPixel(0, 0)), 255)
            val last = PrintPixels.compose(design).last()
            assertEquals(last.argb, bitmap.getPixel(last.x * 4, last.y * 4))
            context.contentResolver.openInputStream(uri)!!.use { assertTrue(it.readBytes().size > 100) }
        } finally { bitmap.recycle() }
        val forbidden = File(context.filesDir, "outside.txt").apply { writeText("synthetic") }
        try {
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.prints", forbidden)
            fail("Private files directory must not be shared")
        } catch (_: IllegalArgumentException) { } finally { forbidden.delete() }
    }

    private fun awaitReady(scenario: ActivityScenario<MainActivity>) {
        repeat(80) {
            var ready = false
            scenario.onActivity { ready = it.model.session?.ownerCount == 1 }
            if (ready) return
            SystemClock.sleep(100)
        }
        fail("Studio did not become ready")
    }

    private fun findGameView(view: View): StudioGameView? {
        if (view is StudioGameView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findGameView(view.getChildAt(index))?.let { return it }
        return null
    }
}
