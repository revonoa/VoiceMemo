package com.dalek.voicememo

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionGuideActivity : AppCompatActivity(){
    private val requestNotiPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Toast.makeText(
            this,
            if (granted) "알림 권한이 허용되었습니다." else "알림 권한이 필요합니다.",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_guide)

        val btnRequestNoti = findViewById<Button>(R.id.btnRequestNoti)
        val btnExact = findViewById<Button>(R.id.btnExactAlarmSettings)
        val btnDone = findViewById<Button>(R.id.btnDone)

        btnRequestNoti.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                val granted = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

                if (granted) {
                    Toast.makeText(this, "이미 알림 권한이 허용되어 있어요.", Toast.LENGTH_SHORT).show()
                } else {
                    requestNotiPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                Toast.makeText(this, "이 안드로이드 버전은 알림 권한 요청이 필요 없어요.", Toast.LENGTH_SHORT).show()
            }
        }

        btnExact.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 31) {
                // 정확 알람 허용 요청 화면
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                runCatching { startActivity(intent) }
                    .onFailure { Toast.makeText(this, "설정 화면을 열 수 없어요.", Toast.LENGTH_SHORT).show() }
            } else {
                Toast.makeText(this, "이 안드로이드 버전은 해당 설정이 필요 없어요.", Toast.LENGTH_SHORT).show()
            }
        }

        btnDone.setOnClickListener { finish() }
    }

    companion object {
        fun hasAllReminderPermissions(context: Context): Boolean {
            // 1) Android 13+ 알림 권한
            if (Build.VERSION.SDK_INT >= 33) {
                val notiGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!notiGranted) return false
            }

            // 2) Android 12+ 정확 알람 허용 여부
            if (Build.VERSION.SDK_INT >= 31) {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (!am.canScheduleExactAlarms()) return false
            }

            return true
        }
    }
}