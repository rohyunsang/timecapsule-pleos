# 타임캡슐 · Pleos Connect 네이티브 앱

이 프로젝트는 Kotlin으로 작성한 차량용 Android 앱입니다. 웹 시안을 WebView로 포장하지 않고 Pleos Vehicle·FusedLocation SDK와 Android 마이크·미디어 API를 직접 사용합니다.

## 저장소 안내

`ym` 브랜치에 2026년 11월 2일까지 만든 네이티브 앱·기획서·전체 사용자 플로우·UI 시안을 모았습니다. 기준 앱은 0.3.0입니다.

| 자료 | 경로 |
|---|---|
| 네이티브 앱과 Gradle 빌드 | `app/`, 저장소 루트 |
| 서명한 release APK | [apk/타임캡슐_release.apk](apk/타임캡슐_release.apk) |
| 공식 접수양식 기획서 | [submission/타임캡슐_공모전_접수양식.docx](submission/타임캡슐_공모전_접수양식.docx) |
| 기획서 원고 | [submission/타임캡슐_기획서.md](submission/타임캡슐_기획서.md) |
| 전체 사용자 플로우 | [docs/타임캡슐_전체_사용자_플로우.md](docs/타임캡슐_전체_사용자_플로우.md) |
| 주행 UX 리서치 | [docs/2026-11-01-timecapsule-driving-ux-review.md](docs/2026-11-01-timecapsule-driving-ux-review.md) |
| 검증 결과와 한계 | [evidence/v0.3.0/검증결과.md](evidence/v0.3.0/검증결과.md) |
| 이전 차량 웹 시안 | `prototypes/vehicle/` |
| 초기 모바일 시안 | `prototypes/mobile/out/` |

초기 저장소의 `docs/proposal.html`, `docs/feature-spec.html`과 이전 시안은 설계 경과로 보존했습니다. 최신 구현과 다른 수치·기능을 포함할 수 있습니다. 실제 Gleo 호출·실물 기록 버튼·내비 위 알림 배너·가족 공유는 완료된 기능으로 소개하지 않습니다. 배너는 목표 UX로 설계했으며 실제 Pleos 노출 정책과 연결 검증이 남았습니다. 참가자 정보와 서명은 비워 두었습니다.

## 0.3.0 구현 범위

- 출발 전에 P단에서 동승자 동의·위치·마이크 권한을 확인하고 기록을 준비합니다. 준비만으로 마이크를 열거나 녹음하지 않습니다. foreground microphone·location 서비스가 명시적 기록 요청을 기다립니다.
- 준비 후에는 내비 화면과 D단을 유지한 채 전용 RECORD 키 또는 MediaSession 커스텀 기록 명령으로 최대 15초 녹음합니다. 녹음이 끝나면 시작 위치에 자동 저장합니다. SAVE는 조기 저장, CANCEL은 현재 파일 삭제입니다. 일반 PLAY 키는 녹음하지 않습니다.
- 내비 유형 오디오 포커스 개입, 방향지시등, 차량·위치 정보 불명확 시 진행분을 임시보관합니다. 주차 후 확정/삭제합니다. 안내가 끝나도 녹음을 자동 재개하지 않습니다.
- Gleo `assets/schema.json`과 START/SAVE/CANCEL 수신·콜백 코드를 포함합니다. 수신과 콜백은 `MEDIA_CONTENT_CONTROL` 권한으로 보호합니다. 실제 Gleo 발신 권한 호환성·스키마 심사·음성 호출은 미검증입니다. 일반 앱이 임의로 녹음을 시작하도록 수신기를 개방하지 않습니다.
- 음성과 메타데이터는 앱 내부 저장소에만 보관합니다. 날짜는 지금/1년 뒤 중 주차 중 선택합니다. 서버·계정·공유는 아직 없습니다. Android 자동 백업을 비활성화했습니다.
- 청취도 출발 전 준비합니다. 기본은 도착 알림 후 미디어 PLAY이며, 자동 청취는 별도 선택합니다. MediaBrowserService·MediaSession이 PLAY/PAUSE/NEXT/STOP을 제공합니다. 내비 전면을 유지하고 앱 화면을 강제로 열지 않습니다.
- 개봉은 100m 진입·개봉일·최신 위치를 확인합니다. 자동 감지는 150m 밖을 거친 재방문과 서로 다른 위치 샘플 2회를 요구합니다. 같은 자리에서 켜도 자동 재생하지 않습니다.
- 현장에서 열린 한 캡슐은 5분 이내 현재 세션에서 구역 밖으로 지나가도 마저 들을 수 있습니다. 내비·방향지시등으로 중지된 재생은 위치를 보존하고 사용자가 재개합니다. 연속 자동 재생은 하지 않습니다.
- 기록 준비와 청취는 각각 2시간 또는 15분 연속 주차 후 종료합니다. 사용자 STOP과 앱 종료도 세션을 끝냅니다. 프로세스 재시작 후 몰래 복구하지 않습니다. 수치는 초기 제품 설정이며 안전 인증 기준이 아닙니다.
- 기어/방향지시등은 5초, 위치 수신 및 SDK 타임스탬프는 10초 이내 정보만 사용합니다. CarUxRestrictions를 직접 구독하며 P단과 제한 해제 시에만 편집 UI를 제공합니다. `distractionOptimized`로 시스템을 우회하지 않습니다.
- 현재 좌표 반경 판정은 같은 도로를 보장하지 못합니다. FusedLocation confidence의 단위를 추정하지 않으며 평행도로·고가도로 매칭은 후속입니다. GPS를 처음부터 못 받으면 기록을 시작하지 못하는 제한도 있습니다.

## 검증 범위와 남은 연결

Pleos Connect 2.1.0 ARM64 에뮬레이터에서 D단·내비 전면·백그라운드 녹음과 표준 미디어 키를 시험했습니다. 전용 RECORD 키는 에뮬레이터 키 입력이며 실제 핸들 버튼의 매핑 증거가 아닙니다. Gleo 음성 인식부터 녹음까지는 아직 완료하지 않았습니다. 기본 내비가 초기화 지연 화면을 보여 실제 지도 경로 안내 대신 별도 NAVIGATION 오디오 포커스 도구로 중단을 검증했습니다. 마이크 파일 생성 시험은 실차 음질·사람 목소리 검증이 아닙니다.

`android.car`는 컴파일 때 사용하되 manifest의 uses-library는 optional입니다. 대상 Pleos 이미지의 BOOTCLASSPATH에 car API가 있으며 required 라이브러리 이름으로는 설치되지 않기 때문입니다. 실제 CarUxRestrictions 콜백으로 P/D 전환을 확인했습니다. 다른 차량에서 API 연결이 없으면 편집을 잠급니다.

## 빌드

JDK 21, Gradle wrapper 8.13, Android SDK Platform 35, Build Tools 35.0.0을 사용합니다. 앱의 minSdk는 28, targetSdk는 34입니다. Pleos SDK는 공식 공개 Maven 저장소에서 받습니다.

```sh
# local.properties에 본인의 SDK 경로를 지정하거나 ANDROID_HOME을 설정합니다.
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./scripts/build-local.sh
```

스크립트는 단위 테스트·Android Lint·debug/release 빌드 후 release APK에 로컬 서명을 합니다. 개인 서명키는 프로젝트 밖 `~/.local/share/timecapsule-signing`에 생성하고 접근 권한을 제한합니다. 이 키는 분실하지 않도록 별도 보관해야 하며 소스나 제출 압축파일에 넣지 않습니다. 제출용 정식 키·Pleos App Market 등록 키 운영은 참가 계정 확정 후 결정합니다.

- APK: `apk/타임캡슐_release.apk`
- 체크섬: `apk/SHA256SUMS`
- 팀명이 정해지면 공모전 파일 규칙에 맞춰 `팀명_타임캡슐_release.apk`로 이름을 바꿉니다.
- 참가자 정보와 팀명은 사용자의 요청대로 비워 둡니다.

## Pleos 연동

| 기능 | 실제 사용 API | 선언 권한 |
|---|---|---|
| P/D/R/N 상태 | Vehicle 2.3.2 / Gear get·callback | `pleos.car.permission.CAR_DRIVE` |
| 방향지시등 중단 | Vehicle / TurnSignal get·callback | `pleos.car.permission.CAR_LIGHTS` |
| 위치 | FusedLocation 1.1.3 / isSupported·getLastKnownLocation·callback | `pleos.car.permission.FUSED_LOCATION` |
| 녹음 | Android MediaRecorder / AAC·M4A | `android.permission.RECORD_AUDIO` |
| 청취 대기·재생 | MediaPlayer·AudioFocusRequest·MediaSession | 정확한 위치, foreground location·media playback |

Android 정확한 위치 권한과 Pleos FUSED_LOCATION 런타임 권한을 모두 요청하고 확인합니다. 에뮬레이터에서도 Pleos 위치 권한은 별도의 사용자 동의가 필요하며, 자동 권한 부여 설치만으로 확인하지 않습니다.

SDK 서비스의 지원 여부와 권한 실패는 앱에서 기능 잠금으로 처리합니다. 샘플 위치나 P단 상태로 대신 성공시키지 않습니다. 앱은 차량 상태를 읽기만 하며 기어·창문·공조 등 차량 상태를 제어하지 않습니다.

## 실행

```sh
adb install -r apk/타임캡슐_release.apk
adb shell am start -n io.timecapsule.vehicle/.MainActivity
adb forward tcp:8088 tcp:8088
# 브라우저에서 http://127.0.0.1:8088/ 를 열어 Pleos Virtual Console 사용
```

테스트할 때는 Pleos Connect 2.1.0 ARM64 시스템 이미지와 cold boot를 사용합니다. Virtual Console의 Motion에서 기어를 P로 바꾸고 위치 데이터를 공급한 뒤 마이크 권한을 허용합니다. 에뮬레이터의 마이크 입력 설정과 호스트 OS 권한은 별도입니다. 테스트용 위치 조작은 에뮬레이터에서만 수행합니다.

## 아직 포함하지 않은 기능

가족 계정·상호 팔로우·클라우드 동기화, 차량 간 이전, 임의의 받는 사람과 공개일 편집, NaviHelper 경로 전달, 와이퍼 등 분위기 태그, Gleo 실제 음성 호출·STT·TTS, ADAS·급제동 전체 신호 처리, 장기 보관·암호화 내보내기는 후속 구현 범위입니다. 기기 시계 조작이나 에뮬레이터 위치 조작을 방지하는 서버 검증도 아직 없습니다. 현재 앱은 본인이 이 기기에 만든 캡슐을 다룹니다.

물리 차량의 마이크 품질, 위치 오차, 오디오 라우팅, 차량별 주행 제한과 Pleos App Market 승인 여부는 에뮬레이터 실행만으로 검증되지 않습니다. 구체적인 실행 결과는 `evidence/검증결과.md`에 기록합니다.

## 공식 근거

- [Pleos 개발 환경·에뮬레이터](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [Virtual Console](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)
- [Vehicle 초기화·도메인 접근](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object)
- [Vehicle 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission)
- [FusedLocation 초기화](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/initialization-and-release)
- [FusedLocation 위치 수신](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/location-data-access)

2026년 11월 2일 공식 문서와 공개 배포 SDK를 확인했습니다. SDK를 내려받아 컴파일한 사실과 참가 계정의 사용 승인·실차 검증은 구분합니다.

Android 위치·미디어 서비스 유형은 [공식 foreground service 가이드](https://developer.android.com/develop/background-work/services/fgs/launch)를 따릅니다. Vehicle 2.3.2의 Gear API는 deprecated 경고가 있으며, 후속 버전에서는 표준 Android Automotive GEAR_SELECTION으로 이전할 필요가 있습니다.

추가 근거: [Gleo 스키마 심사](https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/deliver-schema), [마이크 foreground service 제약](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

## 협업과 권리

기존 팀의 협업 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)를 확인하세요. 이 업로드는 `ym` 브랜치 공유를 위한 것이며 `main`의 변경이나 팀의 승인 완료를 의미하지 않습니다. 기존 저장소의 저작권 정책을 유지합니다. 별도 라이선스가 부여되지 않은 팀 저작물은 All rights reserved입니다. 서명키·로컬 SDK 경로·비밀 환경 파일은 업로드하지 않습니다.
