# integration 변경 결정

## 기준과 작업 구조

- UI: proto `5b58938`의 색상·타이포그래피·날씨 배경·화면 구성을 유지합니다. 기존 행·버튼·확인창으로 준비와 임시보관을 추가합니다.
- 비교 자료: main `2c15ab9`의 명세, ym `7b3afd0`의 `CapsulePolicy`, `ListeningSession`, `CapsuleEngine` 및 사용자 플로우입니다. 코드 전체 병합 대신 proto 구조에 맞춰 구현했습니다.
- 사용자 지정 통합 브랜치: `integration`. hub: `/Users/block-s-dev/ym/timecapsule-pleos-worktrees/integration/hub`.
- 하위 작업: `feature/integration--reliability`, 작업 경로: `/Users/block-s-dev/ym/timecapsule-pleos-worktrees/integration/reliability`. 통합 후 하위 작업을 정리합니다. Android 프로젝트로 로컬 웹 서버 포트 배정은 해당하지 않습니다.
- 원격 업로드가 이번 요청의 범위입니다. main 병합이나 원본 proto 교체는 포함하지 않습니다.

## 정책

1. `OpenPolicy`에서 임시보관·위치 불확실·날짜·현재 좌표·위치 수신 시각·반경을 검사합니다. 화면의 재생 버튼 노출을 권한으로 간주하지 않습니다. 실제 재생 직전 Room에서 날짜 잠금을 다시 읽습니다.
2. 녹음 3~30초, 기본 반경 300m는 proto 값을 유지합니다. 접근 후보 범위 1.2km 중 실제 반경에 들어온 캡슐만 청취 제안으로 노출합니다. 위치는 수신 후 10초, 기어·방향지시등은 5초를 유효시간으로 사용합니다. 이는 실제 센서 측정 시각이나 같은 도로 판정을 보장하지 않습니다.
3. 녹음 시작 좌표가 없으면 종료 좌표로 대신 묻지 않습니다. 비공개 장소 미확정 메모를 남깁니다. 녹음 중단은 임시보관, 사용자 취소는 현재 파일 삭제로 구분합니다.
4. 이번 주행 준비는 화면이 보이는 P단에서 microphone/mediaPlayback foreground service를 시작합니다. 마이크는 명시적인 녹음 또는 접근 알림 음성 응답 시에만 사용합니다. `START_NOT_STICKY`로 자동 복원하지 않습니다.
5. 내비·방향지시등 등으로 멈춘 재생은 수동으로 재개합니다. 미디어 세션의 transport 콜백과 안전 일시정지 상태 표시를 연결합니다. 녹음도 오디오 포커스를 요청하고 다른 안내가 개입하면 임시보관합니다.
6. 새로 열린 공유 캡슐은 서버 확인이 필요합니다. 유효하게 열린 한 캡슐의 방문 권한은 5분이며 주행 종료·계정 변경 때 폐기합니다. 공유 철회는 이미 열린 방문 권한을 즉시 회수하는 정책이 아닙니다.
7. 친구 공유는 현재 수신자 스냅샷을 사용합니다. 기존 공유의 수신자는 친구 해제 시 줄일 수 있지만 새 친구를 자동 추가하지 않습니다. 기존 proto에서 이미 공개한 캡슐의 과거 수신 범위는 소급 변경하지 않습니다.
8. Room destructive migration을 제거하고 v1/v2 → v3 마이그레이션으로 기존 데이터와 날짜 잠금을 보존합니다.
9. 시스템 전용 `PRIVATE_CAR_SAFETY`가 필요한 telltale 구독은 제거합니다. 일반 앱에서 ADAS 전체 신호를 읽는다고 주장하지 않습니다.

## 남은 연동

- 권한으로 보호한 START/SAVE/CANCEL 수신 경로를 추가했으나 Gleo 스키마 심사·실제 발신 권한·음성 호출은 검증 전입니다.
- 전용 RECORD 키의 실물 핸들 매핑과 표준 차량 미디어 탐색 서비스는 별도 검증·개발 대상입니다.
- 내비 위 배너·실제 음성·통화·차량별 서비스 수명·위치 오차·공유 철회 서버 동작은 기기 시험이 필요합니다.
- 위치 없는 임시 음성은 미리 듣기와 삭제만 제공합니다. 수동 위치 지정, 전원 차단 중 파일 복구, 가족 그룹·모바일 앱은 포함하지 않습니다.

## Android 근거

foreground service 유형 및 권한: https://developer.android.com/develop/background-work/services/fgs/service-types

백그라운드 마이크 서비스 시작 제약: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

확인일: 2026-11-02. 위 문서는 서비스 실행 구조의 근거이며 실차 동작 증거가 아닙니다.
