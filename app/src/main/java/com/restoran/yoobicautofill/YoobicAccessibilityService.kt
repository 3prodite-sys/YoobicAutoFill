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
