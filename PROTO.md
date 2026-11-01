# 프로토타입 (proto/sso06052)

팀 명세 v0.1(docs/feature-spec.html) 기준 개인 프로토타입입니다. main에는 합치지 않습니다.

## 실행

```
JAVA_HOME=<JDK 17> ./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb forward tcp:8088 tcp:8088   # Virtual Console: http://127.0.0.1:8088/
```

Pleos 에뮬레이터에서는 Vehicle SDK·NaviHelper를 실제로 쓰고, 그 외 기기에서는 가짜 차량·가짜 내비로 자동 전환됩니다. 홈 오른쪽 위 "개발"을 누르면 시연용 패널이 열립니다(캡슐을 18년 전으로 옮기기, 1.2km 접근 흉내).

## 구현 상태

| ID | 기능 | 상태 | 코드 |
|---|---|---|---|
| F-01 | 캡슐 묻기 | 버튼 녹음, 3~30초, 시작·끝 알림음, 안전 신호 시 저장 | `audio/Recorder.kt`, `drive/DriveController.kt` |
| F-02 | 분위기 태그 | 실차 값으로 태그 생성, 원시값 JSON 저장 | `vehicle/PleosVehicleSource.kt`, `tag/` |
| F-03 | 경로 예고 | 경로 시작 시 300m 안 캡슐 수 안내, 잠긴 편지는 존재만 | `capsule/CapsuleRules.kt` |
| F-04 | 접근 알림 | 1.2km, 하루 1회, 안전 신호 해제 10초 뒤, 10초 무응답이면 다음 정차 때 | 〃 |
| F-05 | 재생 | 내레이션 + 원본 목소리, 안전 신호에 일시정지, 해제 2초 뒤 재개 | `audio/Player.kt` |
| F-06 | 편지 캡슐 | 받는 사람(나/친구 한 명) + 1년/10년/100일/직접, 날짜 전에는 잠김, 받는 사람 아니면 알리지 않음 | `ui/Screens.kt` |
| F-07 | 추억 경로 | 앨범 → NaviHelper 경유지(최대 2곳) + 목적지 | `navi/PleosNaviSource.kt` |
| F-08 | 여행 앨범 (P1) | 내비 안내 시작~도착을 여행으로 묶음, 달린 거리·인원 | `capsule/CapsuleRepository.kt` |
| F-09 | 도착 제안 (P1) | 도착 + P단 + 문 열림 + 캡슐 없는 곳, 하루 2회 | `drive/DriveController.kt` |
| 친구 | 친구에게 남기기 | 친구 코드(6자리) 또는 QR로 서로 친구. 목소리 남기기 기본은 "친구 모두에게"(설정에서 "나만"). 친구가 그 길을 지나면 "○○님이 남긴 캡슐이 있어요" → 들려줘 | `social/`, `ui/FriendsScreen.kt` |
| F-13 | 개인정보 | 명시적 호출로만 녹음, 알림음 끌 수 없음, 개별·전체 삭제, 도착 제안 끄기 | `ui/Screens.kt` |

단위 테스트 35개(태그, 내레이션, 상태 흐름, 거리·경로·접근 규칙, 친구 코드·QR 링크).

## 친구와 서버 (Firebase)

- 내레이션은 담백하게 한 줄: "18년 전 오늘 남긴 캡슐이에요." / "민수님이 3일 전 남긴 캡슐이에요." 날씨·인원은 화면 태그로만.
- 서버는 Firebase 무료(Spark) 요금제: 익명 로그인 + Firestore만 씀. Pleos에는 구글 플레이 서비스가 없지만 이 둘은 없어도 동작함. 목소리(30초, 64kbps 약 240KB)는 Firestore 문서에 넣어서 유료인 Storage는 안 씀.
- 연결된 프로젝트: `timecapsule-pleos-11020143` (lilmoohyun@gmail.com 계정, 서울 리전). 규칙·익명 로그인은 `firebase.json`으로 배포: `npx firebase-tools deploy --only firestore:rules,auth`.
- 키가 없으면 앱 안의 가짜 서버로 돌아감. 가짜 친구: 민수 `MNSU27`, 지영 `JYNG38`.
- 연결 방법
  1. Firebase 콘솔에서 프로젝트 만들기 → Android 앱 추가(패키지 `app.timecapsule.proto`)
  2. Authentication → 익명 로그인 켜기, Firestore 데이터베이스 만들기
  3. Firestore 규칙에 `firebase/firestore.rules` 붙여넣기
  4. `local.properties`(git에 안 올라감)에 넣기: `firebase.apiKey=...`, `firebase.appId=...`, `firebase.projectId=...`
- 친구 캡슐은 받은 차에 오디오까지 내려받아 둠 → 길에서 인터넷이 끊겨도 알림·재생 됨.
- QR은 `timecapsule://friend?code=...` 링크. 차 안에서 열면 바로 친구 추가. 차에는 카메라가 없어서 QR은 폰으로 찍는 용도이고, 폰 앱(F-10)이 생기면 폰에서 찍어 바로 추가되는 구조.
- 개발 패널: 민수 친구 추가, QR로 지영 추가, "민수가 이 길에 캡슐 남김"(500m 앞 → 몇 초 뒤 알림), 다른 사람으로 시작/바꾸기(한 에뮬레이터에서 나와 친구 번갈아 하기).

## SDK 확인 결과 (명세 갱신 필요)

- **외기온도 API 없음.** HVAC은 실내 현재 온도(`getHvacTemperatureCurrent`)만 줍니다. 지금은 이걸로 추운/더운 날을 판단합니다.
- **Toll은 ETCS 음성 볼륨만** 있고 고속도로 진입 이벤트가 없습니다. "고속도로를 달리던"은 NaviHelper 현재 주소에 "고속도로"가 들어 있을 때 붙입니다.
- **Safety 텔테일은 `PRIVATE_CAR_SAFETY`(시스템 전용) 권한이 필요**해서 일반 앱은 못 읽습니다. 안전 신호는 방향지시등, 브레이크, 내비 음성(오디오 포커스)으로 판단합니다.
- **Brake는 페달을 밟았는지만** 알려 줍니다. 급제동 대신 "밟는 순간 1.5초 정지"로 처리했습니다.
- **NaviHelper는 경로 선(polyline)을 주지 않습니다.** F-03은 현재 위치 → 경유지 → 목적지 직선 구간으로 근사합니다.
- **경유지는 최대 2곳**(`WaypointIndex.FIRST/SECOND`).
- **위치**: FusedLocation이 준비 중이라 `getCurrentLocationInfo()`를 2초마다 요청합니다.
- **핸들 음성 버튼(VOICE_ASSIST)은 AAOS가 먼저 가져가서** 앱에 오지 않습니다. 음성 호출은 Gleo 연동이 필요합니다.
- **에뮬레이터에 한국어 TTS 엔진이 없습니다.** 내레이션은 화면에 글로 보이고 원본 목소리는 재생됩니다. Pleos TTS SDK가 나오면 교체합니다.
- 좌석 감지는 에뮬레이터 기준 2열 중앙·3열은 지원하지 않습니다(오류 없이 무시).

## UI 기준

레퍼런스:
- [Pleos Page Navigation](https://document.pleos.ai/en/docs/connect/guide/docs-design/components/page-navigation): 화면 위는 제목(하위 화면은 뒤로 + 제목)과 "⋯" 하나.
- [Android for Cars Header](https://developer.android.com/design/ui/cars/guides/components/header): 제목만 있는 헤더도 허용.
- [Android for Cars List template](https://developer.android.com/design/ui/cars/guides/templates/list-template): 행이 바탕 위에 바로 놓이고 얇은 구분선으로 나뉨.
- [Pleos Display Layout](https://document.pleos.ai/en/docs/connect/guide/docs-design/getting-started/layout): 상태 바가 이미 주행 상태를 보여 줌.

- **주 화면은 세로 한 창.** [앱 구조 문서](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model)대로 `screenOrientation`을 두지 않아 기본이 portrait(내비 옆 한 창)입니다. 가로(두 창)일 때만 오른쪽에 캡슐 목록을 더 보여 줍니다.
- **헤더는 제목과 ⋯만.** 앱 로고, "차량 연결됨/주행 중" 같은 상태 표시는 넣지 않습니다(상태 바와 중복).
- **카드 없음.** 패널 테두리, 둥근 흰 상자, 그림자를 쓰지 않습니다. 행은 바탕 위에 바로 놓고 얇은 구분선으로만 나눕니다. 그림자는 화면 위에 뜨는 것(접근 알림, 팝업)에만 씁니다.
- **모노톤.** Pleos 버튼 위계 그대로 Basic(연회색)과 Filled(진회색). 빨강은 녹음 중 표시, 파랑은 켜진 스위치와 라디오에만 씁니다. 아이콘에 색 배지를 씌우지 않습니다.
- **주 동작은 진한 바 하나.** 내비의 "어디로 갈까요?" 바와 같은 모양으로 "목소리 남기기".
- **화면 안의 질문**(도착 제안, 아까 지나친 캡슐)은 글 한 줄과 버튼 두 개. 상자로 감싸지 않습니다.
- **메뉴는 내비 하단 아이콘 줄처럼** 위에 선 하나, 세로선으로 나눈 세 칸. 주행 중엔 자물쇠로 바뀌고 누르면 이유를 알려 줍니다.
- **접근 알림**은 화면 위쪽에 뜨는 System Notification 배치(제목, 설명, 같은 폭 버튼 두 개). 앱이 화면에 없으면 시스템 알림으로 올립니다(`drive/AlertNotifier.kt`).
  - 확인한 제약: 에뮬레이터의 Pleos 알림 서비스는 privileged/플랫폼 서명 앱에만 헤드업 팝업을 띄웁니다(`isSystemImportantNotification`). 일반 앱 알림은 알림 센터에만 들어갑니다.
- **글꼴** Asta Sans(시스템 글꼴), 가이드 글자 스케일(160dpi라 px = sp), 한국어는 단어 단위 줄바꿈.
- **낮/밤**은 Pleos 색 토큰으로 차량 야간 모드를 따릅니다.
- **주행 제한**은 `CarUxRestrictionsManager` + P단 여부.
