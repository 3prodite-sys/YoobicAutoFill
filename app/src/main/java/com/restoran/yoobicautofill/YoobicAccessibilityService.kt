package com.restoran.yoobicautofill

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.Toast

class YoobicAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    private val allItems = listOf(
        BreakfastItem("croissant", "Savoury croissant", "Ham", "86.5", true),
        BreakfastItem("porridge", "Porridge", "Porridge", "82.0", true),
        BreakfastItem("macaroni", "Macaroni Cheese", "Macaroni Cheese", "84.5", true),
        BreakfastItem("hotwrap", "Hot wrap", "Hot wrap", "83.0", true),
        BreakfastItem("soup", "Soup", "Soup", "85.0", false),
        BreakfastItem("beans", "Breakfast beans", "Breakfast beans", "84.0", false),
        BreakfastItem("sausage", "Sausage", "Sausage", "83.5", false)
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        showFloatingButton()
    }

    private fun showFloatingButton() {
        if (floatingView != null) return
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val btn = Button(this).apply {
            text = "DOLDUR"
            setBackgroundColor(Color.parseColor("#1976D2"))
            setTextColor(Color.WHITE)
            setPadding(28, 16, 28, 16)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 16
            y = 0
        }

        var initialY = 0
        var initialTouchY = 0f
        var moved = false

        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = params.y
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dy) > 10) moved = true
                    params.y = initialY + dy
                    windowManager?.updateViewLayout(btn, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) runAutoFill()
                    true
                }
                else -> false
            }
        }

        floatingView = btn
        windowManager?.addView(btn, params)
    }

    private fun runAutoFill() {
        val prefs = getSharedPreferences("yoobic_prefs", Context.MODE_PRIVATE)
        val activeItems = allItems.filter { prefs.getBoolean("en_${it.key}", it.defaultEnabled) }
            .map { it.copy(defaultTemp = prefs.getString("temp_${it.key}", it.defaultTemp) ?: it.defaultTemp) }

        if (activeItems.isEmpty()) {
            Toast.makeText(this, "Seçili ürün yok", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Form dolduruluyor...", Toast.LENGTH_SHORT).show()
        val firstBatch = processVisibleItems(activeItems)

        handler.postDelayed({
            fillEmptyFieldsForItems(firstBatch)
            scrollDown()
            handler.postDelayed({
                val secondBatch = processVisibleItems(activeItems.filterNot { firstBatch.contains(it) })
                handler.postDelayed({
                    fillEmptyFieldsForItems(secondBatch)
                    Toast.makeText(this, "Tamamlandı!", Toast.LENGTH_SHORT).show()
                }, 600)
            }, 700)
        }, 600)
    }

    private fun processVisibleItems(candidates: List<BreakfastItem>): List<BreakfastItem> {
        val root = rootInActiveWindow ?: return emptyList()
        val triggered = mutableListOf<BreakfastItem>()

        for (item in candidates) {
            val matches = root.findAccessibilityNodeInfosByText(item.label)
            if (!matches.isNullOrEmpty()) {
                val sw = findSwitchNear(matches[0])
                if (sw != null) {
                    if (!sw.isChecked) {
                        sw.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    triggered.add(item)
                }
            }
        }
        return triggered
    }

    private fun fillEmptyFieldsForItems(items: List<BreakfastItem>) {
        if (items.isEmpty()) return
        val root = rootInActiveWindow ?: return
        val editTexts = mutableListOf<AccessibilityNodeInfo>()
        collectEditTexts(root, editTexts)

        val emptyInputs = editTexts.filter {
            val txt = it.text?.toString().orEmpty()
            val hint = it.hintText?.toString().orEmpty()
            txt.isEmpty() || txt == hint
        }

        var idx = 0
        for (item in items) {
            if (idx + 1 >= emptyInputs.size) break
            setTextValue(emptyInputs[idx], item.defaultProduct)
            setTextValue(emptyInputs[idx + 1], item.defaultTemp)
            idx += 2
        }
    }

    private fun setTextValue(node: AccessibilityNodeInfo, value: String) {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findSwitchNear(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var parent = node?.parent
        repeat(3) {
            if (parent != null) {
                val found = findCheckableChild(parent)
                if (found != null) return found
                parent = parent?.parent
            }
        }
        return null
    }

    private fun findCheckableChild(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isCheckable || node.className?.toString()?.contains("Switch") == true) {
            return node
        }
        for (i in 0 until node.childCount) {
            val res = findCheckableChild(node.getChild(i))
            if (res != null) return res
        }
        return null
    }

    private fun collectEditTexts(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.className?.toString() == "android.widget.EditText" && node.isEditable) {
            out.add(node)
        }
        for (i in 0 until node.childCount) {
            collectEditTexts(node.getChild(i), out)
        }
    }

    private fun scrollDown() {
        val dm = resources.displayMetrics
        val path = Path().apply {
            moveTo(dm.widthPixels / 2f, dm.heightPixels * 0.78f)
            lineTo(dm.widthPixels / 2f, dm.heightPixels * 0.25f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
            .build()
        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager?.removeView(it) }
        floatingView = null
    }
}
