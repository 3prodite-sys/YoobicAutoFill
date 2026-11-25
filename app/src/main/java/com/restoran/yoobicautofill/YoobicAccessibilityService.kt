package com.restoran.yoobicautofill

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
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

    private val allBreakfastItems = listOf(
        BreakfastItem("croissant", "Savoury croissant", "Ham", "86.5", true),
        BreakfastItem("porridge", "Porridge", "Porridge", "82.0", true),
        BreakfastItem("macaroni", "Macaroni Cheese", "Macaroni Cheese", "84.5", true),
        BreakfastItem("hotwrap", "Hot wrap", "Hot wrap", "83.0", true),
        BreakfastItem("soup", "Soup", "Soup", "85.0", false),
        BreakfastItem("beans", "Breakfast beans", "Breakfast beans", "84.0", false),
        BreakfastItem("sausage", "Sausage", "Sausage", "83.5", false)
    )

    private val tempCheckNumericFields = listOf(
        TempCheckField("tc_fridge_1", "Fridge #1", "2.4"),
        TempCheckField("tc_fridge_2", "Fridge #2", "4.3"),
        TempCheckField("tc_fridge_3", "Fridge #3", "3.2"),
        TempCheckField("tc_fridge_4", "Fridge #4", "2.8"),
        TempCheckField("tc_fridge_5", "Fridge #5", "3.7"),
        TempCheckField("tc_freezer_1", "Freezer #1", "-19"),
        TempCheckField("tc_freezer_2", "Freezer #2", "-21"),
        TempCheckField("tc_freezer_3", "Freezer #3", "-22"),
        TempCheckField("tc_freezer_4", "Freezer #4", "-22.1"),
        TempCheckField("tc_langar_1", "BTS / MIXED #1 - Langar", "7"),
        TempCheckField("tc_langar_2", "BTS / MIXED #2 - Langar", "4.3"),
        TempCheckField("tc_langar_3", "MTS #1 - Langar", "3.9")
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
                    if (!moved) dispatchAutoFill()
                    true
                }
                else -> false
            }
        }

        floatingView = btn
        windowManager?.addView(btn, params)
    }

    private fun dispatchAutoFill() {
        val root = rootInActiveWindow ?: return
        if (isTemperatureCheckScreen(root)) {
            runTemperatureCheckAutoFill()
        } else {
            runBreakfastAutoFill()
        }
    }

    private fun isTemperatureCheckScreen(root: AccessibilityNodeInfo): Boolean {
        val markers = listOf("Temperature ch", "FRIDGES", "FREEZERS", "LANGARS", "PRODUCTS ON DISPLAY")
        return markers.any { !root.findAccessibilityNodeInfosByText(it).isNullOrEmpty() }
    }

    // --- TEMPERATURE CHECK OTOMASYONU ---
    private fun runTemperatureCheckAutoFill() {
        val prefs = getSharedPreferences("yoobic_prefs", Context.MODE_PRIVATE)
        val savedValues = mutableMapOf<String, String>()
        for (f in tempCheckNumericFields) {
            savedValues[f.key] = prefs.getString(f.key, f.defaultValue) ?: f.defaultValue
        }
        savedValues["tc_init_fridges"] = prefs.getString("tc_init_fridges", "Sa") ?: "Sa"
        savedValues["tc_init_freezers"] = prefs.getString("tc_init_freezers", "Ss") ?: "Ss"
        savedValues["tc_init_langars"] = prefs.getString("tc_init_langars", "Aa") ?: "Aa"
        savedValues["tc_init_display"] = prefs.getString("tc_init_display", "Ss") ?: "Ss"

        Toast.makeText(this, "Temperature Check dolduruluyor...", Toast.LENGTH_SHORT).show()
        val completedKeys = mutableSetOf<String>()

        fun executePass(passIndex: Int) {
            scanAndFillTemperaturePass(savedValues, completedKeys)
            if (passIndex < 4) {
                scrollDown()
                handler.postDelayed({ executePass(passIndex + 1) }, 650)
            } else {
                Toast.makeText(this, "Temperature Check tamamlandı!", Toast.LENGTH_SHORT).show()
            }
        }

        executePass(0)
    }

    private fun scanAndFillTemperaturePass(
        values: Map<String, String>,
        completed: MutableSet<String>
    ) {
        val root = rootInActiveWindow ?: return
        val editTexts = mutableListOf<AccessibilityNodeInfo>()
        collectEditTexts(root, editTexts)

        // 1. "Yes" butonlarını işaretle
        val yesNodes = root.findAccessibilityNodeInfosByText("Yes").orEmpty()
            .filter { it.text?.toString()?.trim() == "Yes" }

        for (yesNode in yesNodes) {
            val yesRect = Rect()
            yesNode.getBoundsInScreen(yesRect)

            if (!completed.contains("yes_working") && isTextDirectlyAbove(root, "working?", yesRect.top, 380)) {
                clickNodeOrParent(yesNode)
                completed.add("yes_working")
            } else if (!completed.contains("yes_display") && isTextDirectlyAbove(root, "RIGHT TICKET", yesRect.top, 450)) {
                clickNodeOrParent(yesNode)
                completed.add("yes_display")
            }
        }

        // 2. Sıcaklık kutularını doldur
        for (field in tempCheckNumericFields) {
            if (completed.contains(field.key)) continue
            val labelNodes = root.findAccessibilityNodeInfosByText(field.label).orEmpty()
                .filter { it.text?.toString()?.trim() == field.label }
            if (labelNodes.isEmpty()) continue

            val labelRect = Rect()
            labelNodes[0].getBoundsInScreen(labelRect)
            val targetEdit = findEditTextBelowLabel(editTexts, labelRect)
            if (targetEdit != null) {
                setTextValue(targetEdit, values[field.key] ?: field.defaultValue)
                completed.add(field.key)
            }
        }

        // 3. Dört farklı "Initials" kutusunu bölüm konumuna göre doldur
        val initialsNodes = root.findAccessibilityNodeInfosByText("Initials").orEmpty()
            .filter { it.text?.toString()?.trim() == "Initials" }

        for (initNode in initialsNodes) {
            val labelRect = Rect()
            initNode.getBoundsInScreen(labelRect)
            val targetEdit = findEditTextBelowLabel(editTexts, labelRect) ?: continue

            val initKey = resolveInitialsKey(root, labelRect)
            if (initKey != null && !completed.contains(initKey)) {
                setTextValue(targetEdit, values[initKey] ?: "")
                completed.add(initKey)
            }
        }
    }

    private fun resolveInitialsKey(root: AccessibilityNodeInfo, labelRect: Rect): String? {
        val hasFreezersBelow = isTextBelow(root, "FREEZERS", labelRect.bottom)
        val hasLangarsBelow = isTextBelow(root, "LANGARS", labelRect.bottom)
        val hasDisplayBelow = isTextBelow(root, "PRODUCTS ON DISPLAY", labelRect.bottom)

        val hasFridgeAbove = isTextAbove(root, "Fridge #", labelRect.top)
        val hasFreezerAbove = isTextAbove(root, "Freezer #", labelRect.top)
        val hasLangarAbove = isTextAbove(root, "- Langar", labelRect.top)
        val hasTicketAbove = isTextAbove(root, "RIGHT TICKET", labelRect.top)

        return when {
            hasFreezersBelow || (hasFridgeAbove && !hasFreezerAbove) -> "tc_init_fridges"
            hasLangarsBelow || (hasFreezerAbove && !hasLangarAbove) -> "tc_init_freezers"
            hasDisplayBelow || (hasLangarAbove && !hasTicketAbove) -> "tc_init_langars"
            hasTicketAbove -> "tc_init_display"
            else -> null
        }
    }

    private fun findEditTextBelowLabel(
        editTexts: List<AccessibilityNodeInfo>,
        labelRect: Rect
    ): AccessibilityNodeInfo? {
        return editTexts.firstOrNull { et ->
            val r = Rect()
            et.getBoundsInScreen(r)
            r.top >= labelRect.bottom - 15 &&
                (r.top - labelRect.bottom) < 250 &&
                kotlin.math.abs(r.left - labelRect.left) < 140
        }
    }

    private fun isTextDirectlyAbove(
        root: AccessibilityNodeInfo,
        query: String,
        targetTop: Int,
        maxDist: Int
    ): Boolean {
        return root.findAccessibilityNodeInfosByText(query).orEmpty().any {
            val r = Rect()
            it.getBoundsInScreen(r)
            r.bottom <= targetTop + 15 && (targetTop - r.bottom) < maxDist
        }
    }

    private fun isTextAbove(root: AccessibilityNodeInfo, query: String, targetTop: Int): Boolean {
        return root.findAccessibilityNodeInfosByText(query).orEmpty().any {
            val r = Rect()
            it.getBoundsInScreen(r)
            r.top < targetTop
        }
    }

    private fun isTextBelow(root: AccessibilityNodeInfo, query: String, targetBottom: Int): Boolean {
        return root.findAccessibilityNodeInfosByText(query).orEmpty().any {
            val r = Rect()
            it.getBoundsInScreen(r)
            r.bottom > targetBottom
        }
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo) {
        var current: AccessibilityNodeInfo? = node
        repeat(3) {
            if (current != null) {
                if (current!!.isClickable) {
                    current!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return
                }
                current = current?.parent
            }
        }
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    // --- BREAKFAST HEATING OTOMASYONU ---
    private fun runBreakfastAutoFill() {
        val prefs = getSharedPreferences("yoobic_prefs", Context.MODE_PRIVATE)
        val activeItems = allBreakfastItems.filter { prefs.getBoolean("en_${it.key}", it.defaultEnabled) }
            .map { it.copy(defaultTemp = prefs.getString("temp_${it.key}", it.defaultTemp) ?: it.defaultTemp) }

        if (activeItems.isEmpty()) {
            Toast.makeText(this, "Seçili ürün yok", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Breakfast Heating dolduruluyor...", Toast.LENGTH_SHORT).show()
        val firstBatch = processVisibleBreakfastItems(activeItems)

        handler.postDelayed({
            fillEmptyFieldsForItems(firstBatch)
            scrollDown()
            handler.postDelayed({
                val secondBatch = processVisibleBreakfastItems(activeItems.filterNot { firstBatch.contains(it) })
                handler.postDelayed({
                    fillEmptyFieldsForItems(secondBatch)
                    Toast.makeText(this, "Tamamlandı!", Toast.LENGTH_SHORT).show()
                }, 600)
            }, 700)
        }, 600)
    }

    private fun processVisibleBreakfastItems(candidates: List<BreakfastItem>): List<BreakfastItem> {
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
