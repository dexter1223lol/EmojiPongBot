package com.example.emojipong

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val capture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK && r.data != null) {
            startForegroundService(Intent(this, BotService::class.java)
                .putExtra("code", r.resultCode).putExtra("data", r.data))
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }
        col.addView(TextView(this).apply {
            text = "1) Enable 'EmojiPongBot' under Accessibility (lets it drag the paddle).\n" +
                   "2) Tap Start, allow screen capture.\n" +
                   "3) Open the Instagram game and start playing."
        })
        col.addView(Button(this).apply {
            text = "1. Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        col.addView(Button(this).apply {
            text = "2. Start bot"
            setOnClickListener {
                capture.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
            }
        })
        col.addView(Button(this).apply {
            text = "Stop bot"
            setOnClickListener { stopService(Intent(this@MainActivity, BotService::class.java)) }
        })
        setContentView(col)
    }
}
