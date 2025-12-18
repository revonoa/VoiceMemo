# Voice Memo Reminder (Android)

음성(STT)으로 메모를 작성하고, 한국어 날짜·시간(또는 상대시간)을 인식해 리마인더 알림을 예약하는 Android 앱입니다.

## 주요 기능
- 음성 메모 작성 (STT) + 안내 음성(TTS)
- "끝" 음성 커맨드로 입력 종료
- 한국어 날짜·시간 인식  
  - 예: `내일 오후 3시 치과`, `12월 20일 14시 회의`
- 상대시간 인식  
  - 예: `30분 후`, `2시간 후`
- 날짜/시간이 충족될 때만 알림 활성화 (UX 가드)
- Room DB 저장 + Flow 관찰로 리스트 자동 갱신
- 스와이프 삭제 + Undo(되돌리기)
- 알림 권한/정확 알람(Exact Alarm) 안내 화면
- 캘린더에 일정 추가(Intent 기반)

## 사용 예시
- “내일 오후 3시 치과 예약, 끝” → 메모 저장 + 알림 예약
- “30분 후 빨래 꺼내기, 끝” → 30분 뒤 알림 예약

## 기술 스택
- Kotlin
- Android SpeechRecognizer(STT), TextToSpeech(TTS)
- Room + Kotlin Flow
- RecyclerView(ListAdapter) + ItemTouchHelper(스와이프)
- AlarmManager (setExactAndAllowWhileIdle)
- NotificationChannel / NotificationCompat
- CalendarContract Intent (일정 추가)

## 권한/정책 대응
- Android 13+ : POST_NOTIFICATIONS 권한 요청
- Android 12+ : Exact Alarm 허용 여부 검사 및 설정 안내 (canScheduleExactAlarms)

## 실행 방법
1. Android Studio에서 프로젝트 열기
2. Gradle Sync
3. 실기기 또는 에뮬레이터에서 Run

> 알림 테스트는 “현재 시각 + 1~2분” 정도로 설정하면 안정적으로 확인할 수 있습니다.

## 프로젝트 구조(요약)
- MainActivity: 리스트 표시 + 음성 입력 시작 + 스와이프 삭제/Undo
- ConfirmActivity: 내용/제목 확인, 날짜·시간 파싱, 알림/캘린더 연결
- PermissionGuideActivity: 알림 권한 및 Exact Alarm 안내
- ReminderReceiver: 알림(Notification) 표시
- Room: ReminderEntity / ReminderDao / AppDatabase

## 앞으로 개선 아이디어
- 메모 상세 화면에서 수정/삭제
- 날짜/시간 파싱 규칙 확장(“다음주”, “월요일” 등)
- 알림 클릭 시 해당 메모 상세로 이동
