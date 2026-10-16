package com.example.chessarrows

import android.content.Context
import android.graphics.*
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

class OverlayView(context: Context) : View(context), View.OnTouchListener,
    GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boardPaint = Paint()
    private val gridPaint = Paint()
    private val uiPaint = Paint()
    private val textPaint = Paint()
    private val gesture = GestureDetector(context, this)

    private var board: RectF? = null
    private var cellW = 0f
    private var cellH = 0f
    private var mode = Mode.NONE
    private var dragStart: PointF? = null
    private var dragEnd: PointF? = null
    private val items = mutableListOf<Item>()
    private var currentColor = Color.RED
    private var currentAlpha = 180
    private var currentWidth = 8f

    private val menuBtn = RectF()
    private val panel = RectF()
    private var panelOpen = false
    private var draggingMenu = false
    private val menuOffset = PointF()
    private val alphaBar = RectF()
    private val thickBar = RectF()
    private var alphaThumb = 180f
    private var thickThumb = 8f
    private var draggingAlpha = false
    private var draggingThick = false

    init {
        gesture.setOnDoubleTapListener(this)
        setOnTouchListener(this)

        boardPaint.color = 0x99FFFFFF
        boardPaint.style = Paint.Style.STROKE
        boardPaint.strokeWidth = 2f

        gridPaint.color = 0x88FFFFFF
        gridPaint.style = Paint.Style.STROKE
        gridPaint.strokeWidth = 1f

        textPaint.color = Color.WHITE
        textPaint.textSize = 16f
        textPaint.textAlign = Paint.Align.CENTER

        uiPaint.style = Paint.Style.FILL
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        menuBtn.set(w - 70f, 40f, w - 10f, 80f)
        panel.set(10f, 100f, w - 10f, 100f + 260f)
        alphaBar.set(30f, panel.bottom + 20f, w - 30f, panel.bottom + 40f)
        thickBar.set(30f, panel.bottom + 60f, w - 30f, panel.bottom + 80f)
        alphaThumb = alphaBar.left + (currentAlpha / 255f) * alphaBar.width()
        thickThumb = thickBar.left + ((currentWidth - 2f) / 38f) * thickBar.width()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        board?.let { c.drawRect(it, boardPaint) }

        board?.let {
            val bw = it.width() / 8f
            val bh = it.height() / 8f
            for (i in 0..8) {
                c.drawLine(it.left, it.top + i * bh, it.right, it.top + i * bh, gridPaint)
                c.drawLine(it.left + i * bw, it.top, it.left + i * bw, it.bottom, gridPaint)
            }
        }

        for (it in items) drawItem(c, it)

        if (dragStart != null && dragEnd != null) {
            paint.color = currentColor
            paint.alpha = currentAlpha
            paint.strokeWidth = currentWidth
            paint.style = Paint.Style.STROKE
            c.drawLine(dragStart!!.x, dragStart!!.y, dragEnd!!.x, dragEnd!!.y, paint)
            drawArrowHead(c, dragStart!!.x, dragStart!!.y, dragEnd!!.x, dragEnd!!.y)
        }

        drawUI(c)
    }

    private fun drawItem(c: Canvas, it: Item) {
        when (it) {
            is LineItem -> {
                paint.color = it.color
                paint.alpha = it.alpha
                paint.strokeWidth = it.width
                paint.style = Paint.Style.STROKE
                c.drawLine(it.p1.x, it.p1.y, it.p2.x, it.p2.y, paint)
                drawArrowHead(c, it.p1.x, it.p1.y, it.p2.x, it.p2.y)
                if (it.isKnight && it.mid != null) {
                    c.drawLine(it.p1.x, it.p1.y, it.mid!!.x, it.mid!!.y, paint)
                    c.drawLine(it.mid!!.x, it.mid!!.y, it.p2.x, it.p2.y, paint)
                    drawArrowHead(c, it.mid!!.x, it.mid!!.y, it.p2.x, it.p2.y)
                }
            }
            is CellItem -> {
                paint.color = it.color
                paint.alpha = it.alpha
                paint.style = Paint.Style.FILL
                c.drawRect(it.rect, paint)
            }
            is CircleItem -> {
                paint.color = it.color
                paint.alpha = it.alpha
                paint.style = Paint.Style.FILL
                c.drawCircle(it.c.x, it.c.y, it.r, paint)
            }
        }
    }

    private fun drawArrowHead(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (len < 1f) return
        val ux = dx / len
        val uy = dy / len
        val h = 14f
        val w = 7f
        val path = Path()
        path.moveTo(x2, y2)
        path.lineTo(x2 - ux * h + uy * w, y2 - uy * h - ux * w)
        path.lineTo(x2 - ux * h - uy * w, y2 - uy * h + ux * w)
        path.close()
        paint.style = Paint.Style.FILL
        c.drawPath(path, paint)
    }

    private fun cellOf(x: Float, y: Float): Pair<Int, Int>? {
        board ?: return null
        val bw = board!!.width() / 8f
        val bh = board!!.height() / 8f
        val col = ((x - board!!.left) / bw).toInt().coerceIn(0, 7)
        val row = ((y - board!!.top) / bh).toInt().coerceIn(0, 7)
        return Pair(col, row)
    }

    private fun cellCenter(col: Int, row: Int): PointF {
        val bw = board!!.width() / 8f
        val bh = board!!.height() / 8f
        return PointF(board!!.left + (col + 0.5f) * bw, board!!.top + (row + 0.5f) * bh)
    }

    private fun rectOf(col: Int, row: Int): RectF {
        val bw = board!!.width() / 8f
        val bh = board!!.height() / 8f
        return RectF(
            board!!.left + col * bw,
            board!!.top + row * bh,
            board!!.left + (col + 1) * bw,
            board!!.top + (row + 1) * bh
        )
    }

    private fun addArrow(p1: PointF, p2: PointF) {
        board ?: return
        val c1 = cellOf(p1.x, p1.y) ?: return
        val c2 = cellOf(p2.x, p2.y) ?: return
        val dx = Math.abs(c2.first - c1.first)
        val dy = Math.abs(c2.second - c1.second)
        val isKnight = (dx == 1 && dy == 2) || (dx == 2 && dy == 1)
        var mid: PointF? = null
        if (isKnight) {
            mid = cellCenter(c2.first, c1.second)
        }
        items.add(LineItem(p1, p2, currentColor, currentAlpha, currentWidth, isKnight, mid))
        if (items.size > 200) items.removeAt(0)
    }

    fun undo() {
        if (items.isNotEmpty()) items.removeAt(items.size - 1)
        invalidate()
    }

    fun clear() {
        items.clear()
        invalidate()
    }

    fun setColor(c: Int) {
        currentColor = c
    }

    fun setAlpha(a: Int) {
        currentAlpha = a
    }

    fun setWidth(w: Float) {
        currentWidth = w
    }

    fun setDrawMode() {
        mode = Mode.DRAW_ARROW
        panelOpen = false
        invalidate()
    }

    fun setSelectBoard() {
        mode = Mode.SELECT_BOARD
        panelOpen = false
        invalidate()
    }

    private fun keepInBounds() {
        val w = width.toFloat()
        val h = height.toFloat()
        menuBtn.left = menuBtn.left.coerceIn(0f, w - menuBtn.width())
        menuBtn.top = menuBtn.top.coerceIn(0f, h - menuBtn.height())
        menuBtn.right = menuBtn.left + menuBtn.width()
        menuBtn.bottom = menuBtn.top + menuBtn.height()
    }

    private fun drawUI(c: Canvas) {
        uiPaint.color = 0xCC000000
        c.drawRoundRect(menuBtn, 18f, 18f, uiPaint)
        textPaint.textSize = 22f
        c.drawText("\u2D37", menuBtn.centerX(), menuBtn.centerY() + 6f, textPaint)

        if (!panelOpen) return

        uiPaint.color = 0xDD000000
        c.drawRoundRect(panel, 12f, 12f, uiPaint)

        val btnH = 44f
        val gap = 8f
        val bw2 = (panel.width() - 3 * gap) / 4f
        var ty = panel.top + 10f
        var tx = panel.left + gap

        val regions = mutableListOf<Pair<RectF, () -> Unit>>()

        fun addBtn(tx: Float, ty: Float, action: () -> Unit) {
            c.drawRoundRect(RectF(tx, ty, tx + bw2, ty + btnH), 8f, 8f, uiPaint)
            regions.add(Pair(RectF(tx, ty, tx + bw2, ty + btnH), action))
        }

        addBtn(tx, ty) { setDrawMode() }
        c.drawText("رسم", tx + bw2 / 2, ty + btnH / 2 + 5f, textPaint)
        tx += bw2 + gap

        addBtn(tx, ty) { setSelectBoard() }
        c.drawText("تحديد", tx + bw2 / 2, ty + btnH / 2 + 5f, textPaint)
        tx += bw2 + gap

        addBtn(tx, ty) { pickColor() }
        c.drawText("لون", tx + bw2 / 2, ty + btnH / 2 + 5f, textPaint)
        tx += bw2 + gap

        addBtn(tx, ty) { undo() }
        c.drawText("تراجع", tx + bw2 / 2, ty + btnH / 2 + 5f, textPaint)

        ty += btnH + gap
        tx = panel.left + gap

        addBtn(tx, ty) { clear() }
        c.drawText("مسح", tx + bw2 / 2, ty + btnH / 2 + 5f, textPaint)
        tx += bw2 + gap

        addBtn(tx, ty) { panelOpen = false; invalidate() }
        c.drawText("طي", tx + bw2 / 2, ty + btnH / 2 + 5f, textPaint)

        textPaint.textSize = 16f
        c.drawText("الشفافية: $currentAlpha", panel.left, panel.bottom + 16f, textPaint)

        uiPaint.color = 0x66FFFFFF
        c.drawRect(alphaBar, uiPaint)
        uiPaint.color = currentColor
        c.drawRect(RectF(alphaBar.left, alphaBar.top, alphaThumb, alphaBar.bottom), uiPaint)
        c.drawCircle(alphaThumb, alphaBar.centerY(), 10f, uiPaint)

        ty = panel.bottom + 56f
        c.drawText("سمك الخط: ${currentWidth.toInt()}", panel.left, ty + 16f, textPaint)
        c.drawRect(thickBar, uiPaint)
        uiPaint.color = currentColor
        c.drawRect(RectF(thickBar.left, thickBar.top, thickThumb, thickBar.bottom), uiPaint)
        c.drawCircle(thickThumb, thickBar.centerY(), 10f, uiPaint)
    }

    private fun pickColor() {
        val colors = intArrayOf(
            Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW,
            Color.CYAN, Color.MAGENTA, Color.WHITE, 0xFFFFA500
        )
        val names = arrayOf("أحمر", "أزرق", "أخضر", "أصفر", "سماوي", "بنفسجي", "أبيض", "برتقالي")
        android.app.AlertDialog.Builder(context)
            .setTitle("اختر اللون")
            .setItems(names) { _, which ->
                currentColor = colors[which]
                invalidate()
            }
            .show()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gesture.onTouchEvent(event)
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (menuBtn.contains(x, y)) {
                    draggingMenu = true
                    menuOffset.set(x - menuBtn.left, y - menuBtn.top)
                    return true
                }

                if (panelOpen) {
                    if (alphaBar.contains(x, y) && Math.abs(y - alphaBar.centerY()) < 22f) {
                        draggingAlpha = true
                        alphaThumb = x
                        return true
                    }
                    if (thickBar.contains(x, y) && Math.abs(y - thickBar.centerY()) < 22f) {
                        draggingThick = true
                        thickThumb = x
                        return true
                    }

                    val btnH = 44f
                    val gap = 8f
                    val bw2 = (panel.width() - 3 * gap) / 4f
                    var ty = panel.top + 10f
                    var tx = panel.left + gap

                    val regions = mutableListOf<Pair<RectF, () -> Unit>>()

                    fun addBtnHit(tx: Float, ty: Float, action: () -> Unit) {
                        regions.add(Pair(RectF(tx, ty, tx + bw2, ty + btnH), action))
                    }

                    addBtnHit(tx, ty) { setDrawMode() }
                    tx += bw2 + gap
                    addBtnHit(tx, ty) { setSelectBoard() }
                    tx += bw2 + gap
                    addBtnHit(tx, ty) { pickColor() }
                    tx += bw2 + gap
                    addBtnHit(tx, ty) { undo() }

                    ty += btnH + gap
                    tx = panel.left + gap

                    addBtnHit(tx, ty) { clear() }
                    tx += bw2 + gap
                    addBtnHit(tx, ty) { panelOpen = false; invalidate() }

                    for ((r, a) in regions) {
                        if (r.contains(x, y)) {
                            a.invoke()
                            return true
                        }
                    }
                    return true
                } else {
                    if (panel.contains(x, y)) {
                        panelOpen = true
                        invalidate()
                        return true
                    }
                }

                if (mode != Mode.NONE) {
                    dragStart = PointF(x, y)
                    dragEnd = PointF(x, y)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (draggingMenu) {
                    menuBtn.offsetTo(x - menuOffset.x, y - menuOffset.y)
                    keepInBounds()
                    invalidate()
                    return true
                }
                if (draggingAlpha) {
                    alphaThumb = x.coerceIn(alphaBar.left, alphaBar.right)
                    currentAlpha = ((alphaThumb - alphaBar.left) / alphaBar.width() * 255).toInt().coerceIn(0, 255)
                    invalidate()
                    return true
                }
                if (draggingThick) {
                    thickThumb = x.coerceIn(thickBar.left, thickBar.right)
                    currentWidth = (2f + ((thickThumb - thickBar.left) / thickBar.width() * 38f)).coerceIn(2f, 40f)
                    invalidate()
                    return true
                }
                dragEnd?.set(x, y)
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingMenu = false
                draggingAlpha = false
                draggingThick = false

                if (dragStart != null && dragEnd != null && mode != Mode.NONE) {
                    if (mode == Mode.SELECT_BOARD) {
                        board = RectF(dragStart!!.x, dragStart!!.y, dragEnd!!.x, dragEnd!!.y)
                        if (board!!.width() < 50f || board!!.height() < 50f) {
                            board = null
                        } else {
                            cellW = board!!.width() / 8f
                            cellH = board!!.height() / 8f
                        }
                    } else {
                        addArrow(dragStart!!, dragEnd!!)
                    }
                    dragStart = null
                    dragEnd = null
                    invalidate()
                }
                return true
            }
        }

        return true
    }

    override fun onDown(e: MotionEvent): Boolean = true
    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent): Boolean = false
    override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean = false
    override fun onLongPress(e: MotionEvent) {
        board ?: return
        val cell = cellOf(e.x, e.y) ?: return
        items.add(CellItem(rectOf(cell.first, cell.second), currentColor, currentAlpha))
        invalidate()
    }
    override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean = false
    override fun onDoubleTap(e: MotionEvent): Boolean {
        board ?: return false
        val cell = cellOf(e.x, e.y) ?: return false
        val center = cellCenter(cell.first, cell.second)
        val r = min(cellW, cellH) * 0.4f
        items.add(CircleItem(center, r, currentColor, currentAlpha))
        invalidate()
        return true
    }
    override fun onDoubleTapEvent(e: MotionEvent): Boolean = false

    enum class Mode { NONE, DRAW_ARROW, SELECT_BOARD }

    sealed class Item

    data class LineItem(
        val p1: PointF, val p2: PointF, val color: Int, val alpha: Int,
        val width: Float, val isKnight: Boolean, val mid: PointF?
    ) : Item()

    data class CellItem(val rect: RectF, val color: Int, val alpha: Int) : Item()

    data class CircleItem(val c: PointF, val r: Float, val color: Int, val alpha: Int) : Item()
}
