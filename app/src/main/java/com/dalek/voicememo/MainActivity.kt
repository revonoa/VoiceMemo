package com.dalek.voicememo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dalek.voicememo.data.AppDatabase
import com.dalek.voicememo.ui.ReminderAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var tvStatus: TextView
    private lateinit var tvRecognized: TextView
    private lateinit var btnVoiceMemo: Button

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private var isListening: Boolean = false

    //"끝" 말하면 true로 바뀌고 그때만 stopListening() 실행
    private var endRequested: Boolean = false

    //누적 텍스트 버퍼
    private val memoBuffer = StringBuilder()

    private lateinit var reminderAdapter: ReminderAdapter

    private lateinit var dao: com.dalek.voicememo.data.ReminderDao


    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceFlow()
        } else {
            tvStatus.text = "마이크 권한이 필요합니다"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        tvStatus = findViewById(R.id.tvStatus)
        tvRecognized = findViewById(R.id.tvRecognized)
        btnVoiceMemo = findViewById(R.id.btnVoiceMemo)

        tts = TextToSpeech(this, this)

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(recognitionListner)
        } else {
            tvStatus.text = "현재 기기는 음성인식을 지원하지 않습니다"
            btnVoiceMemo.isEnabled = false
        }

        btnVoiceMemo.setOnClickListener {
            if (isListening) {
                // 사용자가 버튼으로 끄는 건 "끝"과 동일하게 종료 처리
                endRequested = true
                stopListening()
                tvStatus.text = "중지됨"
            } else {
                ensureAudioPermissionThenStart()
            }
        }

        val rv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvReminders)
        reminderAdapter = ReminderAdapter()
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = reminderAdapter

//        val dao = AppDatabase.get(this).reminderDao()
//        lifecycleScope.launch {
//            dao.observeAll().collectLatest { list ->
//                reminderAdapter.submitList(list)
//            }
//        }

        dao = AppDatabase.get(this).reminderDao()
        lifecycleScope.launch {
            dao.observeAll().collectLatest { list ->
                reminderAdapter.submitList(list)
            }
        }

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val current = reminderAdapter.currentList
                if (position !in current.indices) return

                val deleted = current[position]

                lifecycleScope.launch {
                    dao.deleteById(deleted.id)
                }

                Snackbar.make(findViewById(R.id.root), "삭제했습니다", Snackbar.LENGTH_LONG)
                    .setAction("되돌리기") {
                        lifecycleScope.launch {
                            dao.insert(deleted.copy(id = 0))
                        }
                    }
                    .show()
            }
        })

        itemTouchHelper.attachToRecyclerView(rv)

        resetVoiceUi()

    }

    override fun onResume() {
        super.onResume()
        resetVoiceUi()
    }

    override fun onPause() {
        super.onPause()
        speechRecognizer?.cancel()
        tts?.stop()
        resetVoiceUi()
    }

    private fun ensureAudioPermissionThenStart() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) startVoiceFlow()
        else requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    // introduce by tts and after start stt
    private fun startVoiceFlow() {
        memoBuffer.clear()
        tvRecognized.text = ""
        tvStatus.text = "안내중.."

        endRequested = false
        isListening = true
        btnVoiceMemo.text = "음성메모 종료"

        speak("메모 내용을 말씀해 주세요.") {
            startListening()
        }
    }

    private fun startListening() {
        if (!isListening) return
        val sr = speechRecognizer ?: return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)

            // "너무 빨리 끝나버림" 완화용(완벽히 막진 못함) - 루프 재시작이 핵심
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
        }

        tvStatus.text = "음성 인식 중..."
        sr.startListening(intent)
    }

    private fun stopListening(updateStatus: Boolean = true) {
        isListening = false
        btnVoiceMemo.text = "음성메모 시작"
        if (updateStatus) tvStatus.text = "중지 중..."
        speechRecognizer?.stopListening()
    }

    // ✅ onResults 직후 바로 startListening 하면 기기 따라 꼬일 수 있어 약간 딜레이
    private fun restartListeningSoon() {
        tvStatus.postDelayed({
            if (isListening && !endRequested) {
                startListening()
            }
        }, 250L)
    }

    private val recognitionListner = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            tvStatus.text = "듣는 중..."
        }

        override fun onBeginningOfSpeech() {
            tvStatus.text = "메모내용을 말하세요"
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            // 여기서 "처리 중..."이 떠도, onResults 후 재시작
            tvStatus.text = "처리 중..."
        }

        override fun onError(error: Int) {
            if (!isListening) return

            // "끝" 때문에 stopListening() 중이라면 무시
            if (endRequested) {
                tvStatus.text = "저장 준비..."
                return
            }

            // 에러가 나도(침묵/취소 등) 계속 듣게 재시작
            tvStatus.text = "인식 오류($error) - 계속 듣는 중..."
            restartListeningSoon()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!isListening) return

            val candidates = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.map { it.trim() }
                .orEmpty()

            if (candidates.isEmpty()) return

            val preview = (memoBuffer.toString() + " " + candidates[0]).trim()
            tvRecognized.text = preview

            Log.d("STT", "partial=$candidates")
            Log.d("STT", "results=$candidates")
        }

        override fun onResults(results: Bundle?) {
            if (!isListening) return

            val candidates = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.map { it.trim() }
                .orEmpty()

            if (candidates.isEmpty()) {
                restartListeningSoon()
                return
            }

            // ✅ 여기서만 종료 판단
            if (candidates.any { isEndCommand(it) }) {
                endRequested = true
                isListening = false

                val cleaned = candidates[0].replace("끝", "").trim()
                if (cleaned.isNotBlank()) {
                    if (memoBuffer.isNotEmpty()) memoBuffer.append(" ")
                    memoBuffer.append(cleaned)
                }

                tvStatus.text = "메모 완료"
                speechRecognizer?.stopListening()

                openConfirmScreen(memoBuffer.toString().trim())
                return
            }

            // 일반 문장 누적
            val finalText = candidates[0]
            if (finalText.isNotBlank()) {
                if (memoBuffer.isNotEmpty()) memoBuffer.append(" ")
                memoBuffer.append(finalText)
            }

            tvRecognized.text = memoBuffer.toString()
            tvStatus.text = "계속 말씀하세요… (저장은 '끝')"

            restartListeningSoon()
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.KOREAN
        } else {
            tvStatus.text = "TTS 초기화 실패"
        }
    }

    private fun speak(text: String, onDone: () -> Unit) {
        val engine = tts ?: return

        val utteranceId = "UTT_${System.currentTimeMillis()}"
        engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {
                runOnUiThread { onDone() }
            }
            override fun onDone(doneUtteranceId: String?) {
                if (doneUtteranceId == utteranceId) {
                    runOnUiThread { onDone() }
                }
            }
        })

        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null

        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun isEndCommand(raw: String): Boolean {
        // 공백/기호 제거 후 판단
        val t = raw
            .trim()
            .replace("\\s+".toRegex(), "")
            .replace("[^가-힣0-9]".toRegex(), "")  // 한글/숫자만 남김 (기호 제거)

        return t == "끝"
                || t.startsWith("끝")          // "끝이야", "끝내", "끝입니다"
                || t == "종료"
                || t.startsWith("종료")
                || t == "저장"
                || t.startsWith("저장")
    }

    private fun goConfirm() {
        val text = memoBuffer.toString().trim()
        val i = Intent(this, ConfirmActivity::class.java).apply {
            putExtra(ConfirmActivity.EXTRA_TEXT, text)
        }
        startActivity(i)
    }

    private fun openConfirmScreen(text: String) {
        val intent = Intent(this, ConfirmActivity::class.java).apply {
            putExtra(ConfirmActivity.EXTRA_TEXT, text)
        }
        startActivity(intent)
    }

    private fun resetVoiceUi(){
        isListening = false
        endRequested = false
        btnVoiceMemo.text = "음성메모 시작"
        tvStatus.text = "대기중..."
    }

}
