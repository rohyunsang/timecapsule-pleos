# 타임캡슐 인수인계 (2026-11-11)

이 브랜치(`backup/handoff-1111`)는 지금까지 작업을 한곳에 모은 백업이에요.
코드는 `origin/integration` 기준이고, 저장소에 없던 문서·영상·APK와 촬영 도구를 더했어요.

## 1. 지금 상태

| 무엇 | 위치 | 상태 |
|---|---|---|
| 모바일 앱 + Firebase 연결 | PR #21 (integration → main) | 열림, CI 통과 |
| 주행 안전·동기화 점검 수정 | PR #22 (`fix/review-1110-on-integration` → integration) | 열림, 빌드·테스트·lint 통과 |
| 결과물에 맞춘 기획서 최종본 | 브랜치 `docs/proposal-final` (PR 없음) | `docs/타임캡슐_기획서_최종.docx` + PDF |
| 이 백업 | 브랜치 `backup/handoff-1111` | 문서·영상·APK·촬영 도구 |

권장 병합 순서: PR #22 → integration, 그다음 PR #21 → main, 기획서는 `docs/proposal-final`에서 PR을 열어 합치기.

## 2. 마감과 제출

- 1차 제출 마감 11/15. 제출 메일: appdevchallenge@hyundai-partners.com
- 제출물: 시연 영상(화면 녹화 인정), GitHub·APK는 선택이지만 가산점.
- APK 규칙: 파일 이름 `팀명_앱이름_release.apk`, 저장소 `apk/` 폴더. README에 앱 이름·실행 방법·테스트 환경·APK 위치를 적어야 함.
  - `apk/타임캡슐_release.apk`는 **11/2에 빌드한 예전 버전**이에요. 제출 전에 최신 코드로 다시 빌드하고 팀명을 붙여야 해요.
- 2차: 12/3~4 오프라인 PT, 에뮬레이터나 기기로 **실시간 시연**.
- 숏폼(선택): 30~60초 세로 영상, `#HMG차량용앱개발공모전`, 가산점 없음, 별도 상금.
- 영상에 개인정보·계정 정보·개발자 실명 금지, 저작권 있는 음악·이미지 금지.

## 3. 빌드에 필요한데 저장소에 없는 것 (따로 전달)

- `local.properties`: `sdk.dir`, `firebase.apiKey`, `firebase.appId`, `firebase.projectId` (Firebase 프로젝트 `tc-companion-20261110`). 없으면 앱이 가짜 서버로 동작해요.
- `keystore.properties` + 릴리스 키 `release.jks`: 릴리스 APK 서명용. **절대 GitHub에 올리지 말 것.** 원래 PC의 `C:\Users\<사용자>\.timecapsule\release.jks`에 있어요.
- Firestore 규칙은 팀원이 이미 배포했어요. 저장소의 `firebase/firestore.rules`를 바꿨다면 배포 전에 팀원과 확인.

## 4. 시연 영상

- `docs/타임캡슐_기능시연영상.mp4`(2분 18초)는 **무음 임시본**이에요. 자막으로 기능 10개를 설명하고, 끝에 사용한 SDK 카드가 나와요.
- 다시 찍을 때 구성(그대로 쓰면 됨): 차량 상태 태그(와이퍼=비, 전조등=밤, 탑승 인원) → 주행 중 버튼 하나 녹음 → 친구 코드 추가 → 편지 캡슐(날짜 잠금) → 친구 캡슐 300m 접근 알림·"들려줘" → 방향지시등 일시정지·이어 듣기 → 안내 뒤 친구 목소리 → 정차 시 재알림 → 여행 앨범·지도 → 야간 모드.
- 심사 기준 연결: 차량특화성(차량 신호·UX 제한), 창의성(장소에 묻는 목소리·편지), 기술 활용(Pleos Vehicle·Navigation·TTS/STT), 사업성.

### 알려진 문제 (촬영 전에 꼭 읽기)

- **소리 끊김(드드득)**: 원래 PC에서는 에뮬레이터가 내는 소리 자체가 0.5ms 정도씩 계속 끊겼어요(말 2~3초에 30~45번). 녹화 방식, 녹화 화질, 윈도우 오디오 드라이버(dsound)를 바꿔도 같았고, Pleos TTS 엔진 쪽은 언더런 0이었어요. 실제 차·실기기에서는 없을 문제로 보여요.
  - 다른 PC에서 찍는다면 먼저 `scripts/demo-shoot/crackle.py`로 확인: `python crackle.py 녹화.webm` → `jumps=0`이면 깨끗한 거예요. 깨끗한 TTS는 0, 원래 PC 녹화본은 30~45가 나왔어요.
  - 다른 방법: 실기기로 찍거나, 앱이 말한 문장을 깨끗한 음성으로 입히기.
- **화질**: `adb emu screenrecord`는 `--size 2560x1440`을 꼭 줘야 원본 해상도로 녹화돼요. 편집할 때 화면을 줄이지 말 것.
- **Pleos 내비 위치**: 에뮬레이터 내비가 성남 시흥동에 고정돼 있고 `adb emu geo fix`를 무시해요. 지도 타일도 비어 있어요.
- **좌석 인원**: Virtual Console에서 좌석을 비워도 앱의 탑승 인원이 줄지 않아요(`PleosVehicleSource.subscribeSeats` 값 매핑 의심, 미수정).
- **시연 패널**: 주행 중(D단)에는 열리지 않아요(의도). 친구 캡슐은 P단에서 패널로 놓고 D로 바꾸세요.
- **에뮬레이터 종료**: 강제 종료하면 부팅 반복에 빠진 적이 있어요. 항상 `adb emu kill`로 끄기.

### 촬영 준비 순서 (원래 PC 기준)

1. 에뮬레이터: AVD `Pleos_Official`, `emulator -avd Pleos_Official -no-snapshot-save -no-boot-anim`. 디스크 여유가 부족하면 안 켜져요(수 GB 필요).
2. 시간대: `adb shell service call alarm 3 s16 Asia/Seoul`
3. Virtual Console: `adb forward tcp:8088 tcp:8088` 후, 1초마다 `POST /browser/session?client=shoot` 를 보내야 값 변경이 먹어요(안 보내면 `emulator_inactive`).
   - 와이퍼 `PUT /vehicle/body?front_wiper=Off|Low`
   - 전조등 `PUT /vehicle/lights?feature=headlamp_mode&value=Off|LowBeam`
   - 기어 `PUT /vehicle/gear?gear=Park|Drive`
   - 속도 `PUT /vehicle/controls?speed=50&accelerator=20&brake=0&steering=0`
   - 방향지시등 `PUT /vehicle/turn-signal?mode=BlinkRight|Off` (측면 카메라 팝업이 함께 떠요)
4. 앱(사용자 10): `adb install -g app-debug.apk`, `appops set --user 10 app.timecapsule.proto SYSTEM_ALERT_WINDOW allow`.
5. 가짜 친구 서버: 앱 `files/fake_server` 파일을 만들고 `scripts/demo-shoot/social_fake*.xml`을 `shared_prefs/`에 복사하면 "윤아"(코드 LNKYGM)와 친구 "민수"로 시작해요. 지영(JYNG38)은 화면에서 코드로 추가.
6. `scripts/demo-shoot/h.sh`에 `tap`, `shot`, `vc`, `rec`, `stoprec` 도우미가 있어요(좌표는 2560×1440 기준, 화면이 바뀌면 다시 확인).

## 5. 이 브랜치에 백업한 파일

- `docs/`: 기획서 각 버전(접수양식, 프로토반영, 1103반영, 1103반영_사진)과 미리보기 PDF, 접수양식 개인정보동의서, 첫 시연 영상, 기능 시연 영상(무음 임시본)
- `apk/타임캡슐_release.apk`: 11/2 빌드(예전 버전)
- `scripts/demo-shoot/`: 소리 끊김 검사기(`crackle.py`), PC 스피커 녹음(`loop.py`), 촬영 도우미(`h.sh`, `trial.sh`), 가짜 계정 설정 파일
