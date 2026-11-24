package com.restoran.yoobicautofill

import android.content.Context
import android.content.Intent
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

class MainActivity : AppCompatActivity() {
    private val items = listOf(
        BreakfastItem("croissant", "Savoury croissant", "Ham", "86.5", true),
        BreakfastItem("porridge", "Porridge", "Porridge", "82.0", true),
        BreakfastItem("macaroni", "Macaroni Cheese", "Macaroni Cheese", "84.5", true),
        BreakfastItem("hotwrap", "Hot wrap", "Hot wrap", "83.0", true),
        BreakfastItem("soup", "Soup", "Soup", "85.0", false),
        BreakfastItem("beans", "Breakfast beans", "Breakfast beans", "84.0", false),
        BreakfastItem("sausage", "Sausage", "Sausage", "83.5", false)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("yoobic_prefs", Context.MODE_PRIVATE)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
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

        val switches = mutableMapOf<String, Switch>()
        val tempInputs = mutableMapOf<String, EditText>()

        for (item in items) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 24, 0, 12)
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

        root.addView(Button(this).apply {
            text = "Ayarları Kaydet"
            setOnClickListener {
                val editor = prefs.edit()
                for (item in items) {
                    editor.putBoolean("en_${item.key}", switches[item.key]?.isChecked == true)
                    editor.putString("temp_${item.key}", tempInputs[item.key]?.text.toString())
                }
                editor.apply()
                Toast.makeText(this@MainActivity, "Ayarlar kaydedildi", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(TextView(this).apply {
            text = "\nKullanım: İzinleri açıp ayarları kaydedin. YOOBIC Breakfast Heating ekranını açıp sağ kenardaki DOLDUR butonuna bir kez dokunun."
            setPadding(0, 16, 0, 0)
        })

        scroll.addView(root)
        setContentView(scroll)
    }
}
