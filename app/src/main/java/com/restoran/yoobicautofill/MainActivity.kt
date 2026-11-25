package com.restoran.yoobicautofill

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

data class BreakfastItem(
    val key: String,
    val label: String,
    val defaultProduct: String,
    val defaultTemp: String,
    val defaultEnabled: Boolean
)

data class TempCheckField(
    val key: String,
    val label: String,
    val defaultValue: String
)

class MainActivity : AppCompatActivity() {

    private val breakfastItems = listOf(
        BreakfastItem("croissant", "Savoury croissant", "Ham", "86.5", true),
        BreakfastItem("porridge", "Porridge", "Porridge", "82.0", true),
        BreakfastItem("macaroni", "Macaroni Cheese", "Macaroni Cheese", "84.5", true),
        BreakfastItem("hotwrap", "Hot wrap", "Hot wrap", "83.0", true),
        BreakfastItem("soup", "Soup", "Soup", "85.0", false),
        BreakfastItem("beans", "Breakfast beans", "Breakfast beans", "84.0", false),
        BreakfastItem("sausage", "Sausage", "Sausage", "83.5", false)
    )

    private val tempCheckFields = listOf(
        TempCheckField("tc_fridge_1", "Fridge #1", "2.4"),
        TempCheckField("tc_fridge_2", "Fridge #2", "4.3"),
        TempCheckField("tc_fridge_3", "Fridge #3", "3.2"),
        TempCheckField("tc_fridge_4", "Fridge #4", "2.8"),
        TempCheckField("tc_fridge_5", "Fridge #5", "3.7"),
        TempCheckField("tc_init_fridges", "Initials (Fridges)", "Sa"),
        TempCheckField("tc_freezer_1", "Freezer #1", "-19"),
        TempCheckField("tc_freezer_2", "Freezer #2", "-21"),
        TempCheckField("tc_freezer_3", "Freezer #3", "-22"),
        TempCheckField("tc_freezer_4", "Freezer #4", "-22.1"),
        TempCheckField("tc_init_freezers", "Initials (Freezers)", "Ss"),
        TempCheckField("tc_langar_1", "BTS / MIXED #1 - Langar", "7"),
        TempCheckField("tc_langar_2", "BTS / MIXED #2 - Langar", "4.3"),
        TempCheckField("tc_langar_3", "MTS #1 - Langar", "3.9"),
        TempCheckField("tc_init_langars", "Initials (Langars)", "Aa"),
        TempCheckField("tc_init_display", "Initials (Display)", "Ss")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("yoobic_prefs", Context.MODE_PRIVATE)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 60)
        }

        root.addView(Button(this).apply {
            text = "1. Üstte Gösterme İzni Ver"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })

        root.addView(Button(this).apply {
            text = "2. Erişilebilirlik Servisini Aç"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        root.addView(TextView(this).apply {
            text = "BREAKFAST HEATING AYARLARI"
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 32, 0, 8)
        })

        val switches = mutableMapOf<String, Switch>()
        val tempInputs = mutableMapOf<String, EditText>()

        for (item in breakfastItems) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 12, 0, 8)
            }
            val sw = Switch(this).apply {
                text = item.label
                isChecked = prefs.getBoolean("en_${item.key}", item.defaultEnabled)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val etTemp = EditText(this).apply {
                setText(prefs.getString("temp_${item.key}", item.defaultTemp))
                hint = "°C"
                minWidth = 180
            }
            switches[item.key] = sw
            tempInputs[item.key] = etTemp
            row.addView(sw)
            row.addView(etTemp)
            root.addView(row)
        }

        root.addView(TextView(this).apply {
            text = "TEMPERATURE CHECK AYARLARI"
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 36, 0, 8)
        })

        val tcInputs = mutableMapOf<String, EditText>()
        for (field in tempCheckFields) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            val tv = TextView(this).apply {
                text = field.label
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val et = EditText(this).apply {
                setText(prefs.getString(field.key, field.defaultValue))
                minWidth = 200
            }
            tcInputs[field.key] = et
            row.addView(tv)
            row.addView(et)
            root.addView(row)
        }

        root.addView(Button(this).apply {
            text = "Tüm Ayarları Kaydet"
            setOnClickListener {
                val editor = prefs.edit()
                for (item in breakfastItems) {
                    editor.putBoolean("en_${item.key}", switches[item.key]?.isChecked == true)
                    editor.putString("temp_${item.key}", tempInputs[item.key]?.text.toString())
                }
                for (field in tempCheckFields) {
                    editor.putString(field.key, tcInputs[field.key]?.text.toString())
                }
                editor.apply()
                Toast.makeText(this@MainActivity, "Tüm ayarlar kaydedildi", Toast.LENGTH_SHORT).show()
            }
        })

        scroll.addView(root)
        setContentView(scroll)
    }
}
