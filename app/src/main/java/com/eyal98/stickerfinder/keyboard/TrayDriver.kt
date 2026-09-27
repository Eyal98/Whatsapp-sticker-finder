package com.eyal98.stickerfinder.keyboard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.eyal98.stickerfinder.index.StickerMatch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * One send from WhatsApp's sticker tray (see [WhatsAppTrayService]): open the emoji panel, switch
 * to stickers, go to the pack, find the sticker among the visible ones by its picture, tap it.
 *
 * WhatsApp's layout isn't an API, so each step looks for what it needs in more than one way (view
 * ids, then labels) and gives up quickly instead of guessing: a wrong tap would send the wrong
 * sticker. Every attempt leaves a trace for problem reports ([TrayTrace]).
 */
internal class TrayDriver(
    private val service: AccessibilityService,
    private val picture: Bitmap,
    packName: String,
) {
    private val pack = normalize(packName)
    private val trace = TrayTrace.Builder()
    private val started = SystemClock.elapsedRealtime()
    private var openedPanel = false

    private class Failed(val reason: String) : Exception(reason)

    suspend fun run(): Boolean {
        val sent = try {
            withTimeout(TOTAL_MILLIS) { steps() }
            true
        } catch (e: Failed) {
            trace.fail(e.reason, clickableIds())
            false
        } catch (e: TimeoutCancellationException) {
            trace.fail("timeout", clickableIds())
            false
        }
        // Leave WhatsApp as it was, so the keyboard can send a copy instead.
        if (!sent && openedPanel) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        trace.finish(sent, SystemClock.elapsedRealtime() - started)
        TrayTrace.save(service, trace.build())
        return sent
    }

    private suspend fun steps() {
        if (whatsApp() == null) throw Failed("not in WhatsApp")

        // 1. The emoji panel, unless the sticker tray is already open.
        if (stickerTab() == null && packTab() == null) {
            val button = await(WAIT_MILLIS) { whatsApp()?.let(::emojiButton) } ?: throw Failed("no emoji button")
            trace.step("emoji", describe(button))
            click(button)
            openedPanel = true
        }

        // 2. The stickers tab.
        val tab = await(WAIT_MILLIS) { stickerTab() } ?: throw Failed("no stickers tab")
        trace.step("tab", describe(tab))
        click(tab)
        delay(SETTLE_MILLIS)

        // 3. The pack: its tab in the strip of packs (scrolled to if needed), or its header.
        val packNode = findPack() ?: throw Failed("pack not in tray")
        click(packNode)
        delay(SETTLE_MILLIS)

        // 4. The sticker, recognized by its picture, page by page through the pack.
        for (page in 0 until MAX_PAGES) {
            val root = whatsApp() ?: throw Failed("left WhatsApp")
            val grid = stickerGrid(root)
            val cells = grid?.let { cells(it) }.orEmpty()
            if (cells.isEmpty()) throw Failed("no sticker cells")
            val shot = screenshot() ?: throw Failed("no screenshot")
            val scores = try {
                cells.map { it to score(shot, it.bounds) }
            } finally {
                shot.recycle()
            }
            val ranked = scores.sortedByDescending { it.second }
            val best = ranked.first()
            val next = ranked.getOrNull(1)?.second
            trace.page(page, cells.size, best.second, next)
            if (StickerMatch.isConfident(best.second, next)) {
                click(best.first.node)
                return
            }
            // Two cells about as close: don't guess, a wrong tap would send the wrong sticker.
            if (best.second >= StickerMatch.SAME_PICTURE) throw Failed("two stickers look alike")
            if (grid?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) != true) break
            delay(SETTLE_MILLIS)
        }
        throw Failed("sticker not found in pack")
    }

    // --- Finding things -------------------------------------------------------------------------

    private fun whatsApp(): AccessibilityNodeInfo? {
        val fromWindows = service.windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root }
            .firstOrNull { it.packageName?.toString() in WhatsAppTrayService.PACKAGES }
        return fromWindows ?: service.rootInActiveWindow?.takeIf { it.packageName?.toString() in WhatsAppTrayService.PACKAGES }
    }

    private fun emojiButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val screenHeight = root.bounds().bottom
        val candidates = root.all().filter { n ->
            val id = n.id()
            id != null && "emoji" in id && "search" !in id && n.isVisibleToUser &&
                n.bounds().centerY() > screenHeight / 2
        }.toList()
        return candidates.firstOrNull { it.id() in EMOJI_BUTTON_IDS }
            ?: candidates.firstOrNull { it.isClickable }
            ?: candidates.firstOrNull()
    }

    /**
     * What's below the message box: the emoji panel (or the keyboard). Every search for tabs,
     * packs and stickers stays in here, so a chat message that happens to mention the pack's name
     * is never tapped.
     */
    private fun panel(root: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> {
        val messageBox = root.all().filter { it.isEditable && it.isVisibleToUser }.map { it.bounds() }.minByOrNull { it.top }
        val top = messageBox?.bottom ?: root.bounds().centerY()
        return root.all().filter { it.isVisibleToUser && it.bounds().top >= top }
    }

    private fun stickerTab(): AccessibilityNodeInfo? {
        val root = whatsApp() ?: return null
        val nodes = panel(root).toList()
        return nodes.firstOrNull { n ->
            val id = n.id() ?: return@firstOrNull false
            "sticker" in id && ("tab" in id || "button" in id || "btn" in id) && "search" !in id
        } ?: nodes.firstOrNull { n -> label(n) in STICKER_TAB_LABELS }
    }

    /** The pack's tab or header, if it's on screen now. */
    private fun packTab(): AccessibilityNodeInfo? {
        val root = whatsApp() ?: return null
        return panel(root).firstOrNull { n -> matchesPack(label(n)) }
    }

    private suspend fun findPack(): AccessibilityNodeInfo? {
        await(WAIT_MILLIS) { packTab() }?.let {
            trace.step("pack", "on screen")
            return it
        }
        // Scroll the strip of pack tabs: forward to the end, then back to the start.
        val root = whatsApp() ?: return null
        val strip = panel(root).filter { n ->
            val b = n.bounds()
            n.isScrollable && b.width() > 3 * b.height()
        }.maxByOrNull { it.childCount } ?: run {
            trace.step("pack", "no strip")
            return null
        }
        val labelled = strip.all().count { label(it) != null }
        for (action in listOf(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            repeat(MAX_STRIP_SCROLLS) { i ->
                if (!strip.performAction(action)) return@repeat
                delay(SCROLL_MILLIS)
                packTab()?.let {
                    trace.step("pack", "after ${i + 1} scrolls, strip ${strip.childCount} tabs, $labelled labelled")
                    return it
                }
            }
        }
        trace.step("pack", "not found, strip ${strip.childCount} tabs, $labelled labelled")
        return null
    }

    /** The scrolling grid of stickers: the biggest scrollable area that isn't the pack strip. */
    private fun stickerGrid(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        panel(root).filter { n ->
            val b = n.bounds()
            n.isScrollable && b.height() > b.width() / 3
        }.maxByOrNull { it.bounds().let { b -> b.width() * b.height() } }

    private class Cell(val node: AccessibilityNodeInfo, val bounds: Rect)

    /** Sticker-sized square items in the grid, below the pack's header if that's on screen. */
    private fun cells(grid: AccessibilityNodeInfo): List<Cell> {
        val area = grid.bounds()
        val header = packTab()?.bounds()?.takeIf { area.contains(it.centerX(), it.centerY()) }
        return grid.all().drop(1).mapNotNull { n ->
            val b = n.bounds()
            val size = maxOf(b.width(), b.height())
            val square = kotlin.math.abs(b.width() - b.height()) <= size / 5
            if (!n.isVisibleToUser || !square || size < area.width() / 12 || size > area.width() / 2) return@mapNotNull null
            if (!area.contains(b)) return@mapNotNull null
            if (header != null && b.top < header.bottom) return@mapNotNull null
            Cell(n, b)
        }.distinctBy { it.bounds.flattenToString() }.toList()
            // A cell is often both a tappable frame and the image in it: keep only the innermost,
            // or one sticker would look like two ([click] finds the tappable parent).
            .let { all -> all.filter { outer -> all.none { inner -> inner !== outer && outer.bounds.contains(inner.bounds) } } }
    }

    // --- Recognizing the sticker ----------------------------------------------------------------

    private val signatures = HashMap<Int, FloatArray?>()

    /** How much the screenshot inside [bounds] looks like the sticker. */
    private fun score(shot: Bitmap, bounds: Rect): Float {
        val r = Rect(bounds).apply { inset(2, 2) }
        if (!r.intersect(0, 0, shot.width, shot.height) || r.width() < 8 || r.height() < 8) return -1f
        val pixels = IntArray(r.width() * r.height())
        shot.getPixels(pixels, 0, r.width(), r.left, r.top, r.width(), r.height())
        val background = pixels[0] or (0xFF shl 24) // the cell's corner: the tray's background
        val cell = StickerMatch.signature(pixels, r.width(), r.height(), background) ?: return -1f
        val sticker = signatures.getOrPut(background) {
            val w = picture.width
            val h = picture.height
            val px = IntArray(w * h).also { picture.getPixels(it, 0, w, 0, 0, w, h) }
            StickerMatch.signature(px, w, h, background)
        } ?: return -1f
        return StickerMatch.similarity(sticker, cell)
    }

    private suspend fun screenshot(): Bitmap? {
        repeat(2) { attempt ->
            val shot = suspendCancellableCoroutine { cont ->
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    service.mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                            val buffer = result.hardwareBuffer
                            val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                            val copy = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                            hardware?.recycle()
                            buffer.close()
                            cont.resume(copy)
                        }

                        override fun onFailure(errorCode: Int) {
                            cont.resume(null)
                        }
                    },
                )
            }
            if (shot != null) return shot
            // Android allows about one screenshot a second.
            if (attempt == 0) delay(SCREENSHOT_RETRY_MILLIS)
        }
        return null
    }

    // --- Acting ---------------------------------------------------------------------------------

    private suspend fun click(node: AccessibilityNodeInfo) {
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isClickable) n = n.parent
        if (n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
        // Not clickable through accessibility: tap its centre.
        val b = node.bounds()
        val path = Path().apply { moveTo(b.exactCenterX(), b.exactCenterY()) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, TAP_MILLIS)).build()
        suspendCancellableCoroutine { cont ->
            val dispatched = service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) = cont.resume(Unit)
                    override fun onCancelled(gestureDescription: GestureDescription?) = cont.resume(Unit)
                },
                null,
            )
            if (!dispatched) cont.resume(Unit)
        }
    }

    private suspend fun <T> await(millis: Long, find: () -> T?): T? {
        val end = SystemClock.elapsedRealtime() + millis
        while (true) {
            find()?.let { return it }
            if (SystemClock.elapsedRealtime() >= end) return null
            delay(POLL_MILLIS)
        }
    }

    // --- Helpers --------------------------------------------------------------------------------

    private fun matchesPack(label: String?): Boolean {
        if (label == null || pack.isEmpty()) return false
        return label == pack || (pack.length >= 4 && label.contains(pack))
    }

    /** WhatsApp's ids of what can be tapped on screen, for the trace when a step fails. */
    private fun clickableIds(): List<String> =
        whatsApp()?.all()?.filter { it.isClickable && it.isVisibleToUser }?.mapNotNull { it.id() }?.distinct()?.take(40)?.toList().orEmpty()

    private fun describe(n: AccessibilityNodeInfo) = n.id()?.let { "id $it" } ?: "by label"

    private fun AccessibilityNodeInfo.id(): String? = viewIdResourceName?.substringAfter(":id/")

    private fun AccessibilityNodeInfo.bounds(): Rect = Rect().also { getBoundsInScreen(it) }

    /** Breadth-first, at most [MAX_NODES] nodes. */
    private fun AccessibilityNodeInfo.all(): Sequence<AccessibilityNodeInfo> = sequence {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(this@all) }
        var seen = 0
        while (queue.isNotEmpty() && seen < MAX_NODES) {
            val n = queue.removeFirst()
            seen++
            yield(n)
            for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
        }
    }

    private fun label(n: AccessibilityNodeInfo): String? =
        (n.contentDescription ?: n.text)?.toString()?.let(::normalize)?.takeIf { it.isNotEmpty() }

    companion object {
        private const val TOTAL_MILLIS = 12_000L
        private const val WAIT_MILLIS = 1_500L
        private const val SETTLE_MILLIS = 450L
        private const val SCROLL_MILLIS = 250L
        private const val POLL_MILLIS = 100L
        private const val TAP_MILLIS = 50L
        private const val SCREENSHOT_RETRY_MILLIS = 1_100L
        private const val MAX_PAGES = 5
        private const val MAX_STRIP_SCROLLS = 15
        private const val MAX_NODES = 4_000

        private val EMOJI_BUTTON_IDS = setOf("emoji_picker_btn", "emoji_btn", "emoji_button", "input_emoji")
        private val STICKER_TAB_LABELS = setOf("stickers", "sticker", "מדבקות", "מדבקה")

        private fun normalize(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")
    }
}
