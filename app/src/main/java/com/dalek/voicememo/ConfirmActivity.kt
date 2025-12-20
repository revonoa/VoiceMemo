package com.dalek.voicememo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

import androidx.lifecycle.lifecycleScope
import com.dalek.voicememo.data.AppDatabase
import com.dalek.voicememo.data.ReminderEntity
import kotlinx.coroutines.launch

class ConfirmActivity: AppCompatActivity() {
    companion object{
        const val EXTRA_TEXT = "extra_text"
    }

    private lateinit var etTitle: EditText
    private lateinit var etBody: EditText
    private lateinit var tvParsed: TextView
    private lateinit var swReminder: SwitchCompat

    private var parsedTimeMillis: Long?= null

    //"날짜는 있는데 시간은 없음"을 감지해서 안내용으로 쓰기
    private var hasDateButNoTime: Boolean = false

    //ADDED: "시간은 있는데 날짜는 없음" 감지
    private var hasTimeButNoDate: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirm)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.confirmRoot)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        etTitle = findViewById(R.id.etTitle)
        etBody = findViewById(R.id.etBody)
        tvParsed = findViewById(R.id.tvParsed)
        swReminder = findViewById(R.id.swReminder)

        val btnCancel: Button = findViewById(R.id.btnCancel)
        val btnSave: Button = findViewById(R.id.btnSave)
        val btnCalendar: Button = findViewById(R.id.btnCalendar)

        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty().trim()
        etBody.setText(text)

        // auto generate title
        etTitle.setText(makeTitle(text))

        //ADDED: 날짜/시간 힌트 판정(안내용 + 알림 가능 조건용)
        val hasDateHint = containsDateHint(text)
        val hasTimeHint = containsTimeHint(text)

        hasDateButNoTime = hasDateHint && !hasTimeHint
        hasTimeButNoDate = !hasDateHint && hasTimeHint

        // "날짜+시간 둘 다 있을 때만" parse 시도 (아니면 null)
        val parsed = parseKoreanDateTime(text)
        parsedTimeMillis = parsed

        btnCalendar.isEnabled = (parsedTimeMillis != null)

        btnCalendar.setOnClickListener {
            val title = etTitle.text.toString().trim().ifBlank { "메모" }
            val body = etBody.text.toString().trim()

            if (parsedTimeMillis == null) {
                Toast.makeText(
                    this,
                    "캘린더 추가는 날짜와 시간이 필요합니다. 예) 내일 오후 3시",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            openCalendarInsert(title, body, parsedTimeMillis)
        }

        if (parsed != null) {
            tvParsed.text = "날짜/시간: ${formatTime(parsed)}"
            swReminder.isEnabled = true
            swReminder.isChecked = true
        } else {
            // 상황별 안내 문구
            tvParsed.text = when {
                hasDateButNoTime ->
                    "날짜/시간: 시간이 없어요. 예) 내일 오후 3시, 12월 20일 14시"
                hasTimeButNoDate ->
                    "날짜/시간: 날짜가 없어요. 예) 내일 오후 3시, 12월 20일 14시"
                else ->
                    "날짜/시간: 자동추출 없음"
            }

            // 날짜/시간 둘 다 없으면 알림 불가
            swReminder.isChecked = false
            swReminder.isEnabled = false
        }

        // 알림 스위치를 켜려 하면 안내 후 다시 OFF
        swReminder.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && parsedTimeMillis == null) {
                Toast.makeText(
                    this,
                    "알림을 원하시면 날짜와 시간을 함께 말씀해 주세요. 예) 내일 오후 3시",
                    Toast.LENGTH_SHORT
                ).show()
                swReminder.isChecked = false
            }
        }

        btnCancel.setOnClickListener { finish() }

        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim().ifBlank { "메모" }
            val body = etBody.text.toString().trim()

            val remindAt = if (swReminder.isChecked) parsedTimeMillis else null

            if (remindAt != null && !PermissionGuideActivity.hasAllReminderPermissions(this)) {
                Toast.makeText(this, "알림을 위해 권한/설정이 필요합니다.", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, PermissionGuideActivity::class.java))
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val id = AppDatabase.get(this@ConfirmActivity).reminderDao().insert(
                    ReminderEntity(
                        title = title,
                        body = body,
                        remindAtMillis = remindAt
                    )
                )

                if (remindAt != null) scheduleReminder(remindAt)

                Toast.makeText(this@ConfirmActivity, "저장됨: id=$id", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun makeTitle(text: String): String{
        val t = text.trim()
        if(t.isEmpty()) return ""
        val maxLen = 18
        return if(t.length <= maxLen) t else t.substring(0, maxLen) + "..."
    }

    private fun formatTime(millis: Long):String{
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        val y = c.get(Calendar.YEAR)
        val m = c.get(Calendar.MONTH) + 1
        val d = c.get(Calendar.DAY_OF_MONTH)
        val hh = c.get(Calendar.HOUR_OF_DAY)
        val mm = c.get(Calendar.MINUTE)

        return "%d-%02d-%02d %02d:%02d".format(y, m, d, hh, mm)
    }

    private fun containsDateHint(text: String): Boolean {
        val t = text.replace("\\s+".toRegex(), " ").trim()
        if (t.contains("오늘") || t.contains("내일") || t.contains("모레")) return true
        val md = "(\\d{1,2})월\\s*(\\d{1,2})일".toRegex()
        return md.containsMatchIn(t)
    }

    private fun containsTimeHint(text: String): Boolean {
        val t = text.replace("\\s+".toRegex(), " ").trim()
        if (t.contains("오전") || t.contains("오후")) return true
        val hour = "(\\d{1,2})\\s*시".toRegex()
        return hour.containsMatchIn(t)
    }

    // 오늘/내일/모레, 오전/오후 없을시 24시간으로, 몇시, 몇시 30분, 몇시 반
    private fun parseKoreanDateTime(text:String):Long?{
        //상대시간 분/시간 후 먼저 처리
        parseRelativeTimeMillis(text)?.let { return it }
        // 날짜+시간 둘 다 힌트가 있어야만 알림 시간 계산
        val hasDate = containsDateHint(text)
        val hasTime = containsTimeHint(text)
        if (!hasDate || !hasTime) return null

        val t = text.replace("\\s+".toRegex()," ").trim()
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance()

        when{
            t.contains("내일") -> cal.add(Calendar.DAY_OF_MONTH, 1)
            t.contains("모레") -> cal.add(Calendar.DAY_OF_MONTH, 2)
            t.contains("오늘") -> {}
        }

        val md = "(\\d{1,2})월\\s*(\\d{1,2})일".toRegex()
        md.find(t)?.let { m ->
            val month = m.groupValues[1].toInt()
            val day = m.groupValues[2].toInt()
            cal.set(Calendar.MONTH, month - 1)
            cal.set(Calendar.DAY_OF_MONTH, day)
            if (cal.timeInMillis < now.timeInMillis) {
                cal.add(Calendar.YEAR, 1)
            }
        }

        val am = t.contains("오전")
        val pm = t.contains("오후")
        val hourMatch = "(\\d{1,2})\\s*시".toRegex().find(t)

        // 시간이 힌트는 있는데 '시'가 없으면(예: "오후에") 알림 불가
        if (hourMatch == null) return null

        var hour = hourMatch.groupValues[1].toInt().coerceIn(0, 23)
        var minute = 0

        val minMatch = "(\\d{1,2})\\s*분".toRegex().find(t)
        if (minMatch != null) {
            minute = minMatch.groupValues[1].toInt().coerceIn(0, 59)
        } else if (t.contains("반")) {
            minute = 30
        }

        if (pm && hour in 1..11) hour += 12
        if (am && hour == 12) hour = 0

        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        if (cal.timeInMillis < now.timeInMillis) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        return cal.timeInMillis
    }

    private fun scheduleReminder(triggerAtMillis: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        //Android 12+(31+) 에서만 exact alarm 허용 여부 체크
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(this, "정확한 알람 권한이 필요합니다. 설정에서 허용해 주세요.", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, PermissionGuideActivity::class.java))
                return
            }
        }

        val intent = Intent(this, ReminderReceiver::class.java).apply {
            putExtra("title", etTitle.text.toString())
            putExtra("body", etBody.text.toString())
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        //실제 알람 등록은 SDK 상관없이 실행 + SecurityException 방어
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                kotlin.math.max(triggerAtMillis, System.currentTimeMillis() + 1000),
                pendingIntent
            )
        } catch (se: SecurityException) {
            Toast.makeText(this, "정확한 알람이 차단되어 있어요. 설정에서 허용해 주세요.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, PermissionGuideActivity::class.java))
        }
    }


    private fun parseRelativeTimeMillis(text: String): Long?{
        val t = text.replace("\\s+".toRegex(), "").trim()
        val now = System.currentTimeMillis()

        // 30분후 / 30분 후
        "(\\d{1,3})분후".toRegex().find(t)?.let {
            val minutes = it.groupValues[1].toLong()
            return now + minutes * 60_000L
        }

        // 2시간후 / 2시간 후
        "(\\d{1,2})시간후".toRegex().find(t)?.let {
            val hours = it.groupValues[1].toLong()
            return now + hours * 3_600_000L
        }

        return null
    }

    private fun openCalendarInsert(title: String, body: String, startMillis: Long?) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = android.provider.CalendarContract.Events.CONTENT_URI
            putExtra(android.provider.CalendarContract.Events.TITLE, title)
            putExtra(android.provider.CalendarContract.Events.DESCRIPTION, body)

            // 시간이 있을 때만 시작시간 넣기
            if (startMillis != null) {
                putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)

                // 기본 30분짜리 일정으로(원하면 60분으로)
                putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 30 * 60 * 1000L)
            }
        }

        // 캘린더 앱이 없을 수도 있으니 안전하게
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(this, "캘린더 앱을 열 수 없어요.", Toast.LENGTH_SHORT).show()
            }
    }

}
