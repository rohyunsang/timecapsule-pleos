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
| F-06 | 편지 캡슐 | 1년/10년/100일/직접, 날짜 전에는 잠김 | `ui/Screens.kt` |
| F-07 | 추억 경로 | 앨범 → NaviHelper 경유지(최대 2곳) + 목적지 | `navi/PleosNaviSource.kt` |
| F-08 | 여행 앨범 (P1) | 내비 안내 시작~도착을 여행으로 묶음, 달린 거리·인원 | `capsule/CapsuleRepository.kt` |
| F-09 | 도착 제안 (P1) | 도착 + P단 + 문 열림 + 캡슐 없는 곳, 하루 2회 | `drive/DriveController.kt` |
| F-13 | 개인정보 | 명시적 호출로만 녹음, 알림음 끌 수 없음, 개별·전체 삭제, 도착 제안 끄기 | `ui/Screens.kt` |

단위 테스트 30개(태그, 내레이션, 상태 흐름, 거리·경로·접근 규칙).

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
