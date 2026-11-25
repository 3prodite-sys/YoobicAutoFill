package com.restoran.yoobicautofill

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
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

    private data class FillTask(
        val key: String,
        val isClickOnly: Boolean,
        val value: String,
        val targetNode: AccessibilityNodeInfo?,
        val tapX: Float,
        val tapY: Float
    )

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

    private fun getYoobicRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active != null && active.packageName?.toString() != packageName) {
            return active
        }
        for (win in windows) {
            val r = win.root ?: continue
            if (r.packageName?.toString() != packageName && r.childCount > 0) {
                return r
            }
        }
        return active
    }

    private fun getNodeText(node: AccessibilityNodeInfo): String {
        val t = node.text?.toString()?.trim().orEmpty()
        if (t.isNotEmpty()) return t
        return node.contentDescription?.toString()?.trim().orEmpty()
    }

    private fun normalizeLabel(raw: String): String {
        return raw.replace("\\s+".toRegex(), " ").trim()
    }

    private fun collectAllNodes(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        out.add(node)
        for (i in 0 until node.childCount) {
            collectAllNodes(node.getChild(i), out)
        }
    }

    private fun dispatchAutoFill() {
        val root = getYoobicRoot() ?: return
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, nodes)

        val isTempCheck = nodes.any {
            val txt = normalizeLabel(getNodeText(it))
            txt.contains("Temperature ch", ignoreCase = true) ||
                txt.contains("FRIDGES", ignoreCase = true) ||
                txt.contains("FREEZERS", ignoreCase = true) ||
                txt.contains("LANGARS", ignoreCase = true) ||
                txt.contains("PRODUCTS ON DISPLAY", ignoreCase = true)
        }

        if (isTempCheck) {
            runTemperatureCheckAutoFill()
        } else {
            runBreakfastAutoFill()
        }
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

        fun executeScrollPass(passIndex: Int) {
            val tasks = collectTemperatureTasks(savedValues, completedKeys)
            runTaskQueue(tasks, 0) {
                if (completedKeys.size < 18 && passIndex < 6) {
                    scrollDown(0.75f, 0.32f)
                    handler.postDelayed({ executeScrollPass(passIndex + 1) }, 750)
                } else {
                    Toast.makeText(this, "Tamamlandı! (${completedKeys.size}/18 alan)", Toast.LENGTH_SHORT).show()
                }
            }
        }

        executeScrollPass(0)
    }

    private fun collectTemperatureTasks(
        values: Map<String, String>,
        completed: MutableSet<String>
    ): List<FillTask> {
        val root = getYoobicRoot() ?: return emptyList()
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, allNodes)

        val dm = resources.displayMetrics
        val minY = (dm.heightPixels * 0.11f).toInt()
        val maxY = (dm.heightPixels * 0.88f).toInt()
        val tasks = mutableListOf<FillTask>()

        // 1. "Yes" butonlarını bul
        for (node in allNodes) {
            if (!getNodeText(node).equals("Yes", ignoreCase = true)) continue
            val r = Rect()
            node.getBoundsInScreen(r)
            if (r.width() <= 0 || r.height() <= 0 || r.top < minY || r.bottom > maxY) continue

            if (!completed.contains("yes_working") && hasTextAbove(allNodes, "working", r.top, 450)) {
                tasks.add(FillTask("yes_working", true, "", node, r.centerX().toFloat(), r.centerY().toFloat()))
                completed.add("yes_working")
            } else if (!completed.contains("yes_display") && hasTextAbove(allNodes, "RIGHT TICKET", r.top, 550)) {
                tasks.add(FillTask("yes_display", true, "", node, r.centerX().toFloat(), r.centerY().toFloat()))
                completed.add("yes_display")
            }
        }

        // 2. Sıcaklık giriş kutularını bul
        for (field in tempCheckNumericFields) {
            if (completed.contains(field.key)) continue
            val labelNode = allNodes.firstOrNull {
                normalizeLabel(getNodeText(it)).equals(field.label, ignoreCase = true)
            } ?: continue

            val lr = Rect()
            labelNode.getBoundsInScreen(lr)
            if (lr.width() <= 0 || lr.top < minY) continue

            val inputNode = findInputNodeBelow(allNodes, lr)
            val ir = Rect()
            if (inputNode != null) {
                inputNode.getBoundsInScreen(ir)
            } else {
                ir.set(lr.left, lr.bottom + 18, lr.left + 260, lr.bottom + 115)
            }

            if (ir.bottom > maxY) continue

            tasks.add(
                FillTask(
                    key = field.key,
                    isClickOnly = false,
                    value = values[field.key] ?: field.defaultValue,
                    targetNode = inputNode,
                    tapX = ir.centerX().toFloat(),
                    tapY = ir.centerY().toFloat()
                )
            )
            completed.add(field.key)
        }

        // 3. Dört farklı "Initials" kutusunu bölüm konumuna göre bul
        for (node in allNodes) {
            if (!normalizeLabel(getNodeText(node)).equals("Initials", ignoreCase = true)) continue
            val lr = Rect()
            node.getBoundsInScreen(lr)
            if (lr.width() <= 0 || lr.top < minY) continue

            val initKey = resolveInitialsKey(allNodes, lr) ?: continue
            if (completed.contains(initKey)) continue

            val inputNode = findInputNodeBelow(allNodes, lr)
            val ir = Rect()
            if (inputNode != null) {
                inputNode.getBoundsInScreen(ir)
            } else {
                ir.set(lr.left, lr.bottom + 18, lr.left + 400, lr.bottom + 115)
            }

            if (ir.bottom > maxY) continue

            tasks.add(
                FillTask(
                    key = initKey,
                    isClickOnly = false,
                    value = values[initKey] ?: "",
                    targetNode = inputNode,
                    tapX = ir.centerX().toFloat(),
                    tapY = ir.centerY().toFloat()
                )
            )
            completed.add(initKey)
        }

        return tasks
    }

    private fun runTaskQueue(tasks: List<FillTask>, index: Int, onFinished: () -> Unit) {
        if (index >= tasks.size) {
            onFinished()
            return
        }

        val task = tasks[index]
        if (task.isClickOnly) {
            task.targetNode?.let { clickNodeOrParent(it) }
            tapScreen(task.tapX, task.tapY)
            handler.postDelayed({ runTaskQueue(tasks, index + 1, onFinished) }, 220)
        } else {
            task.targetNode?.let {
                it.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                clickNodeOrParent(it)
            }
            tapScreen(task.tapX, task.tapY)

            handler.postDelayed({
                val focused = getYoobicRoot()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: task.targetNode
                if (focused != null) {
                    applyTextOrPaste(focused, task.value)
                }
                handler.postDelayed({ runTaskQueue(tasks, index + 1, onFinished) }, 200)
            }, 220)
        }
    }

    private fun applyTextOrPaste(node: AccessibilityNodeInfo, value: String) {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        val setOk = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        if (!setOk || node.text?.toString() != value) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("yoobic_val", value))
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
    }

    private fun findInputNodeBelow(
        allNodes: List<AccessibilityNodeInfo>,
        labelRect: Rect
    ): AccessibilityNodeInfo? {
        return allNodes.firstOrNull { node ->
            val cls = node.className?.toString().orEmpty()
            val isInputLike = cls.contains("EditText") || node.isEditable ||
                (node.isFocusable && node.isClickable && getNodeText(node).isEmpty())
            if (!isInputLike) return@firstOrNull false

            val r = Rect()
            node.getBoundsInScreen(r)
            r.width() > 60 &&
                r.height() in 25..220 &&
                r.top >= labelRect.bottom - 10 &&
                (r.top - labelRect.bottom) < 220 &&
                kotlin.math.abs(r.left - labelRect.left) < 140
        }
    }

    private fun resolveInitialsKey(allNodes: List<AccessibilityNodeInfo>, labelRect: Rect): String? {
        val hasFreezersBelow = hasTextBelow(allNodes, "FREEZERS", labelRect.bottom)
        val hasLangarsBelow = hasTextBelow(allNodes, "LANGARS", labelRect.bottom)
        val hasDisplayBelow = hasTextBelow(allNodes, "PRODUCTS ON DISPLAY", labelRect.bottom)

        val hasFridgeAbove = hasTextAbove(allNodes, "Fridge #", labelRect.top, 1200)
        val hasFreezerAbove = hasTextAbove(allNodes, "Freezer #", labelRect.top, 1200)
        val hasLangarAbove = hasTextAbove(allNodes, "- Langar", labelRect.top, 1200)
        val hasTicketAbove = hasTextAbove(allNodes, "RIGHT TICKET", labelRect.top, 1200)

        return when {
            hasFreezersBelow || (hasFridgeAbove && !hasFreezerAbove) -> "tc_init_fridges"
            hasLangarsBelow || (hasFreezerAbove && !hasLangarAbove) -> "tc_init_freezers"
            hasDisplayBelow || (hasLangarAbove && !hasTicketAbove) -> "tc_init_langars"
            hasTicketAbove -> "tc_init_display"
            else -> null
        }
    }

    private fun hasTextAbove(
        allNodes: List<AccessibilityNodeInfo>,
        query: String,
        targetTop: Int,
        maxDist: Int
    ): Boolean {
        return allNodes.any {
            if (!normalizeLabel(getNodeText(it)).contains(query, ignoreCase = true)) return@any false
            val r = Rect()
            it.getBoundsInScreen(r)
            r.top < targetTop && (targetTop - r.bottom) < maxDist
        }
    }

    private fun hasTextBelow(
        allNodes: List<AccessibilityNodeInfo>,
        query: String,
        targetBottom: Int
    ): Boolean {
        return allNodes.any {
            if (!normalizeLabel(getNodeText(it)).contains(query, ignoreCase = true)) return@any false
            val r = Rect()
            it.getBoundsInScreen(r)
            r.bottom > targetBottom
        }
    }

    private fun tapScreen(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo) {
        var current: AccessibilityNodeInfo? = node
        repeat(4) {
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
            scrollDown(0.78f, 0.25f)
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
        val root = getYoobicRoot() ?: return emptyList()
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, allNodes)
        val triggered = mutableListOf<BreakfastItem>()

        for (item in candidates) {
            val match = allNodes.firstOrNull {
                normalizeLabel(getNodeText(it)).contains(item.label, ignoreCase = true)
            }
            if (match != null) {
                val sw = findSwitchNear(match)
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
        val root = getYoobicRoot() ?: return
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, allNodes)

        val emptyInputs = allNodes.filter {
            val cls = it.className?.toString().orEmpty()
            (cls.contains("EditText") || it.isEditable) &&
                (it.text?.toString().orEmpty().isEmpty() || it.text?.toString() == it.hintText?.toString())
        }

        var idx = 0
        for (item in items) {
            if (idx + 1 >= emptyInputs.size) break
            applyTextOrPaste(emptyInputs[idx], item.defaultProduct)
            applyTextOrPaste(emptyInputs[idx + 1], item.defaultTemp)
            idx += 2
        }
    }

    private fun findSwitchNear(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var parent = node?.parent
        repeat(4) {
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

    private fun scrollDown(startRatio: Float, endRatio: Float) {
        val dm = resources.displayMetrics
        val path = Path().apply {
            moveTo(dm.widthPixels / 2f, dm.heightPixels * startRatio)
            lineTo(dm.widthPixels / 2f, dm.heightPixels * endRatio)
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
