# 타임캡슐 (TimeCapsule)

차 안에서 소중한 사람들과 나눈 이야기를 녹음해 두면, 나중에 같은 길을 지날 때 차가 그날의 추억을 다시 들려주는 Pleos Connect 차량용 앱입니다.

- 팀: 호날두
- APK: [`apk/호날두_타임캡슐_release.apk`](apk/호날두_타임캡슐_release.apk)
- 시연 영상: https://youtu.be/oO8jo5hJyEo
- 숏폼: https://youtube.com/shorts/2_C2LxtG1Eg
- 상황별 화면 70장: [`screenshots/integration_2026-11-03`](screenshots/integration_2026-11-03)

## 주요 기능

- **캡슐 묻기**: 홈의 녹음 버튼을 누르거나 Gleo에게 "타임캡슐 녹음해 줘"라고 말하면 녹음합니다. 녹음한 자리의 위치, 차량 상태(와이퍼는 비, 전조등은 밤, 탑승 인원, 창문, 도착 등), 날씨 서비스에서 받은 날씨와 바깥 기온이 태그로 함께 저장됩니다.
- **접근 알림과 재생**: 캡슐 300m 안에 들어서면 내비 위에 알림이 뜹니다. "들려줘"를 누르거나 말로 답하면 Pleos TTS가 누가 언제 남겼는지 안내한 뒤 원본 목소리를 들려줍니다.
- **안전 중단**: 방향지시등을 켜거나 핸들을 크게 돌릴 때, 내비 음성 안내가 나올 때는 재생을 멈추고 "이어 듣기"로 이어 갑니다. 녹음 중이면 그때까지의 녹음을 비공개로 임시보관합니다. 브레이크를 밟고 있는 동안에는 새 알림과 재생을 시작하지 않고, 주행 중에는 목록과 입력 화면을 잠급니다.
- **친구와 편지 캡슐**: 6자리 친구 코드로 친구를 맺고, 받는 사람과 열리는 날을 정한 편지 캡슐을 남깁니다.
- **여행 앨범과 캡슐 지도**: 한 번 연 캡슐은 앨범과 지도에 남고, 그 뒤로는 주차 중 어디서든 다시 들을 수 있습니다.

## APK

| 항목 | 값 |
|---|---|
| 위치 | `apk/호날두_타임캡슐_release.apk` (약 97MB) |
| 패키지 이름 | `app.timecapsule.proto` |
| 버전 | 0.1.0-proto (versionCode 1) |
| 빌드 | release, 팀 서명키로 서명(APK Signature Scheme v2) |
| 지원 ABI | arm64-v8a, x86_64 |
| Android SDK | minSdk 28, targetSdk 35 |

## 테스트 환경

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect 에뮬레이터, 기기 프로필 `Pleos_Connect_v2_0` (Pleos Connect v2.0) |
| 화면 규격 | 2560 × 1440 가로, 160dpi |
| 시스템 이미지 | Android 14 (API 34) Android Automotive, arm64-v8a |
| Android Emulator | 37.2.13 |
| Pleos SDK | Vehicle 2.3.2, NaviHelper 3.0.5, TextToSpeech·SpeechToText 3.3.1, Gleo Interface([`schema.json`](app/src/main/assets/schema.json)) |

## 실행 방법

1. Pleos Connect v2.0 기기 프로필로 만든 에뮬레이터를 실행합니다.
2. APK를 설치하고 권한을 부여합니다.

   ```sh
   adb install -r -g apk/호날두_타임캡슐_release.apk
   # 앱 화면 밖(내비 위)에도 캡슐 알림을 띄우려면
   adb shell appops set --user 10 app.timecapsule.proto SYSTEM_ALERT_WINDOW allow
   ```

   Android Automotive의 현재 사용자 번호가 10이 아니면 `adb shell am get-current-user`로 확인해 바꿔 넣습니다.
3. 런처에서 **타임캡슐**을 엽니다. 명령으로 열 때는 다음과 같이 실행합니다.

   ```sh
   adb shell am start --user 10 -n app.timecapsule.proto/app.timecapsule.MainActivity
   ```

4. 차량 신호는 Virtual Console에서 바꿉니다. `adb forward tcp:8088 tcp:8088`을 실행한 뒤 브라우저에서 http://127.0.0.1:8088 을 엽니다.
   - 와이퍼나 전조등을 켜면 홈의 녹음 버튼 아래에 "비", "밤" 태그가 붙습니다.
   - 기어를 D로 바꾸면 주행 중 화면으로 바뀌어 탭과 목록이 잠기고 큰 녹음 버튼만 남습니다.
5. 녹음하려면 P단에서 홈의 **이번 주행 준비 → 준비하기**로 마이크 권한과 동승자 동의를 먼저 확인합니다. 그다음 녹음 버튼을 누르고 말한 뒤 **녹음 완료**를 누르면 그 자리에 캡슐이 저장됩니다.

## 필요한 설정

- **마이크 입력**: 말소리가 없거나 3초보다 짧은 녹음은 저장하지 않습니다. 에뮬레이터의 Extended Controls → Microphone에서 "Virtual microphone uses host audio input"을 켜거나, 에뮬레이터를 `-allow-host-audio` 옵션으로 실행해 PC 마이크를 연결해 주세요.
- **인터넷**: 친구와 공유 캡슐(Firebase), 날씨(Open-Meteo), 캡슐 지도 배경(OpenFreeMap)에 필요합니다. 인터넷이 없어도 녹음, 재생, 앨범은 동작합니다.
- **계정**: 이 APK는 팀의 Firebase 프로젝트에 연결되어 있습니다. 친구 화면에서 이름만 정하면 바로 시작합니다.
- **위치**: Pleos NaviHelper가 알려 주는 내비 위치를 씁니다. 에뮬레이터의 내비 위치는 한곳에 고정되어 있어 `adb emu geo fix`로 바뀌지 않습니다.
- **시연 패널**: 홈 오른쪽 위 ⋯ 버튼으로 엽니다. 가짜 차량·내비 모드, 그리고 가짜 서버로 친구 캡슐을 받아 재생하는 기능은 debug 빌드에서만 동작합니다. 시연 영상에서 친구 캡슐에 다가가는 장면은 debug 빌드의 시연 모드로 만들었습니다.

## 직접 빌드하기

JDK 21과 Android SDK 35가 필요합니다. Pleos SDK는 `settings.gradle.kts`에 등록한 Pleos 저장소(nexus-playground.pleos.ai)에서 받습니다. 비밀 값은 저장소에 넣지 않고, 아래 두 파일을 저장소 최상위에 직접 만듭니다.

```properties
# local.properties
sdk.dir=/path/to/Android/sdk
firebase.apiKey=...      # 세 값이 없으면 앱 안의 가짜 서버로 동작합니다
firebase.appId=...
firebase.projectId=...

# keystore.properties (release 서명용)
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

```sh
./gradlew :app:testDebugUnitTest :app:assembleRelease
# 결과: app/build/outputs/apk/release/app-release.apk
```

## 검증

2026-11-15 기준으로 확인한 내용입니다.

- `:app:testDebugUnitTest` 단위 테스트 90개를 모두 통과했습니다.
- 제출 APK를 데이터가 비어 있는 새 Pleos Connect v2.0 에뮬레이터(arm64-v8a)에 설치해 실행했습니다. 홈, 여행 앨범, 캡슐 지도, 친구 화면이 열리고 Firebase 서버에 연결되는 것을 확인했습니다.
- 실제 차량에서의 GPS와 마이크 동작, 실제 Gleo 음성 명령은 아직 시험하지 못했습니다.

## 저장소 구성

| 폴더 | 내용 |
|---|---|
| `app/` | 차량 앱(제출 APK) |
| `mobile/` | 휴대폰 앱(2단계 확장, 개발 중이며 제출 APK와 별개) |
| `shared/` | 차량 앱과 휴대폰 앱이 함께 쓰는 코드 |
| `firebase/` | Firestore 보안 규칙 |
| `docs/` | 설계와 검증 문서. 개봉, 공유, 주행 세션 정책은 [integration 동작 정책](docs/integration/overview.md)과 [결정 기록](docs/integration/decisions.md)에 정리했습니다. |
| `screenshots/` | 상황별 화면 |
| `apk/` | 제출 APK |

## 알려진 제한

- "그날처럼"(캡슐을 열 때 창문, 실내 온도, 조명을 그날처럼 되돌리는 기능)은 차량 제어 SDK가 공개되지 않아 구현하지 않았습니다.
- 에뮬레이터의 Virtual Console에서 좌석을 비워도 앱의 탑승 인원이 줄지 않는 경우가 있습니다.

## 권리

All rights reserved.
