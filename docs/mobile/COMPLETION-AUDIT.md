# TC 차량·모바일 연결 완료 점검

2026-11-10 기준이다. 코드·로컬 테스트가 있다는 사실과 실서버/기기에서 동작했다는 사실을 구분한다. 아래 미완료 항목이 남아 있으므로 전체 작업은 완료 상태가 아니다.

| 요구 사항 | 현재 소스와 증거 | 남은 검증 또는 작업 |
| --- | --- | --- |
| 별도 Android 모바일 앱 | `mobile/build.gradle.kts`, 모바일 Manifest, `MainActivity.kt`; APK 빌드 | 실제 기기 설치·실행 |
| 차량과 같은 계정 연결 | `shared/.../social/FirebaseBackend.kt`, `FirebaseCompanionStore.kt` | 프로젝트·앱 등록 및 REST 이메일 연결/동일 계정 재로그인 완료; 실제 두 앱 로그인·계정 전환은 기기 검증 필요 |
| 모바일 현장 수집·수집 후 어디서든 재생 | `PhoneLocation.kt`, `CollectionPolicy.kt`, `MobileModel.open`; 위치/날짜/권한 경계 단위 테스트 | 실제 GPS·권한 거부·오프라인·음성 출력 검증 |
| 목록·검색·상태 구분·지도 | `LibraryContent.kt`, `MemoryCapsuleCard.kt`, `CapsuleMapScreen.kt`; 실제 목록/마커 렌더링 | MapLibre 타일·손가락 이동·확대/축소의 기기 검증 |
| 제목·메모·사진·즐겨찾기·앨범 | `MobileModel.kt`, `AlbumsScreen`, 계정별 CompanionStore | 사진 선택·업로드 실패·앨범 편집 충돌 및 공유 대상별 접근 실서버 검증 (사진이 있는 기록 편집 및 앨범 편집 컴포넌트 렌더링 확인) |
| 친구 전달·공개 범위·가족/친구 앨범 공유 | `RemoteDelivery.kt`, `SharingDialogs.kt`, `updateSharing`, `memoryAlbums` | 서로 다른 실제 계정의 허용/철회·원본 삭제·재전송 검증 (전달/공유 화면 렌더링 확인) |
| 음성 답장 | `ReplyComposer.kt`, `ReplyRecorder.kt`, `ReplyPolicy.kt`; 저장 복원/계정 분리/72시간 경계 테스트 | 실제 녹음/오디오 포커스/권한, 서버 시각과 접근 규칙 (개봉 방식 선택·전송 전 작성창 렌더링 확인) |
| 수집 기본·선택 시 72시간 후 방문 없이 듣기 | 서버 `replySentAt`, 공통 정책, 차량 OpenPolicy 및 수집 기록 분리 | 실제 서버 규칙 배포·72시간 전/후 및 기기 시각 변경 검증 |
| 비공개 백업 선택·수집 기록 동기화 | 차량 Settings/SocialSync와 공유 receipts | 서로 다른 두 앱에서 실제 동기화 및 계정 전환 검증 |
| 모바일 목적지 전송·차량 확인 후 안내 | `CarDestination`, 차량 확인창, 계정별 결과 재전송, 재시도 ID 유지 및 생성 트랜잭션 | 실제 내비 연결·네트워크 단절/복구·중복 확인 검증 |
| 차량 저장 결과·잠금 안내·나중에·조용한 주행·종료 기록 | DriveController, Settings, Screens, AlertCadence 테스트 | 실제 주행/중단 시나리오 (저장·종료 안내 600dp/400dp 렌더링 확인) |
| AI 요약 제외 | 이번 작업에서 AI 요약·전사 서비스 호출을 추가하지 않음 | 출시 전 변경 범위 검토 |
| 기존 디자인을 따른 모바일 | MobileTheme와 실제 Compose 스냅샷 21개 | 실제 기기 화면 검증 (차량 안내 2개 및 사진 기록·앨범 편집 확인, 모바일 320dp·시스템 글꼴 1.5배에서 목록·답장·공유 선택 확인) |
| 에뮬레이터 미사용 | 빌드·JVM 단위 테스트·Paparazzi/Layoutlib만 실행 | 계속 유지 |
| PR 병합과 정리 | PR #20 초안, 최초 GitHub Actions 성공, 모바일 하위 작업 병합·정리 | Firebase 및 기기 검증 후 PR→integration 병합, hub 정리 |

## 실제 Firebase 연결 결과 (2026-11-10)

- 사용자가 지정한 inspirelab777 계정의 `tc-companion-20261110`에 차량 `app.timecapsule.proto`와 모바일 `app.timecapsule.mobile`을 등록했다.
- Spark 무료 요금제, Analytics 제외, Firestore Standard `(default)` 서울 `asia-northeast3`를 사용한다. 익명 및 이메일/비밀번호 인증을 활성화했다.
- 두 앱의 SDK 설정은 권한 0600인 gitignore 대상 `local.properties`에만 저장했다. API 키와 인증 토큰은 커밋하지 않는다.
- `firebase/firestore.rules`를 실제 프로젝트에 컴파일·배포했다. 규칙을 우회하는 관리자 토큰 없이 일반 Firebase ID 토큰으로 아래 9개 시나리오를 통과했다.
- `python3 scripts/firebase-live-smoke.py --project tc-companion-20261110`: 익명 인증/프로필, 이메일 연결/두 번째 세션 동일 UID, 공유 음성 접근과 제3자·비로그인 차단, 수집 기록 격리/불변성, 메모·사진 격리, 공유 앨범 접근/철회, 목적지 처리/되돌리기 차단, LOCATION 답장·72시간 전 답장 잠금·변조 차단, 원본 공유 철회 후 음성 접근 차단을 확인했다.
- 테스트 계정 3개와 이번 테스트에서 생성한 문서를 모두 삭제했다. 정리 실패는 0건이다. 테스트 음성/사진은 바이너리 표본으로, 재생·촬영 성공을 뜻하지 않는다.
- 이메일 연결은 보안 기능을 유지한 상태에서 `accounts:signUp`의 `idToken` 연결 방식을 사용한다. `accounts:update`는 실제 서버에서 이메일 사전 인증 오류로 거절됐다. 근거: [Google 공식 이메일 열거 보호 안내](https://docs.cloud.google.com/identity-platform/docs/admin/email-enumeration-protection).
- 72시간 이후 서버 개봉은 아직 실제 시간 경과로 확인하지 않았다. 72시간 경계는 공통 정책 단위 테스트로 검증했고 실서버에서는 개봉 전 거부를 확인했다.
- 실제 Android 기기에서 로그인 SDK 상태 갱신·GPS·마이크·사진 선택·내비 연동은 별도 검증이 필요하다. Android/Firebase 에뮬레이터는 사용하지 않았다.

## 확인할 실제 계정 시나리오

1. 두 앱에서 동일 이메일 로그인 후 비공개 백업을 끄고/켠 경우를 각각 확인한다.
2. A가 남긴 캡슐을 B가 공유받아 방문 전 잠금·현장 수집·다른 장소 재생을 확인한다.
3. 공유 철회·친구 해제·원본 삭제 후 새 재생과 전달이 거부되는지 확인한다.
4. B가 수집한 캡슐을 C에게 전달하고 C가 방문 없이 들을 수 있는지 확인한다.
5. 답장 LOCATION 및 AFTER_THREE_DAYS를 각각 생성하고 서버 시각/권한으로 개봉 여부를 확인한다.
6. 답장과 목적지 전송 직후 네트워크를 끊고 재연결했을 때 중복·유실·상태 오표시가 없는지 확인한다.
7. A→B 계정 변경 후 A의 사진·메모·앨범·임시 답장·목적지가 보이지 않는지 확인한다.
8. 앱을 백그라운드로 전환하거나 통화가 시작될 때 녹음과 재생이 적절히 멈추고 복구 동작을 제공하는지 확인한다.

## 검증 명령

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21 bash ./gradlew \
  :app:assembleDebug :mobile:assembleDebug \
  :app:verifyPaparazziDebug :shared:testDebugUnitTest :mobile:verifyPaparazziDebug \
  :app:lintDebug :shared:lintDebug :mobile:lintDebug
```

GitHub Actions 실행 42786673991 및 42787998042는 성공했다. 후자는 132개 테스트(실패 0개)와 빌드·lint를 통과했다. Firebase 연결 변경 이후 검증은 아래 구현 기록에 별도로 남긴다. 이미지 테스트에는 예시 데이터만 사용하며 로그인·GPS·녹음 성공을 가짜로 증명하지 않는다.
