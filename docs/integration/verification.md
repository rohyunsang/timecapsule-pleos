# integration 검증 결과

검증일: 2026-11-02. 원본 proto `5b58938` 위에 적용한 integration 개선입니다.

## 통과한 검사

- JDK 21 / Gradle wrapper로 `testDebugUnitTest`, `assembleDebug`, `lintDebug`, `assembleRelease`를 실행했습니다.
- 단위 테스트 49개, 실패 0개, 오류 0개입니다. 개봉일·반경 밖·위치 없음·오래된 위치·잘못된 좌표·임시보관·방문 권한 만료·세션 종료·장시간 주차·수동 재개 규칙을 포함합니다.
- Android Lint: 오류 0개, 경고 35개입니다. 라이브러리 버전 안내와 기존 리소스/API 관련 경고가 남습니다. SDK의 deprecated Gear 조회 경고도 남아 있습니다.
- `python3 scripts/verify-migrations.py`: Room v1→v3, v2→v3의 실제 마이그레이션 SQL을 SQLite에서 실행했습니다. 캡슐 파일 경로·날짜 잠금·기존 공유 정보 보존과 Room v3 schema 일치를 확인했습니다. Android 기기에서 Room을 실행한 검증은 아닙니다.
- `git diff --check`를 통과했습니다.

## APK와 로그

로컬 보관 경로: `/Users/block-s-dev/ym/artifacts/timecapsule-integration-20261102`.

release 서명키가 없어 release APK는 unsigned입니다. 제출용 서명 APK로 소개하지 않습니다. debug APK는 개발용입니다.

- `app-debug.apk`: `7a4458e9e282b27469eeaf3919d4cccc101d7c72ddf21b7acb0de0e7280a02d7`
- `app-release-unsigned.apk`: `93d067e3711605a04c5a3370d614b65508a92f7137ab3ba3d8cadc225e62d6c5`

## 아직 확인하지 않은 항목

- `adb devices`에 연결된 기기가 없어 설치·실행·화면 검증은 하지 않았습니다.
- proto용 Firebase 설정이 없어 서버 로그인·공유 철회·다른 계정 수신 시나리오는 미검증입니다. 배포나 Firebase 규칙 변경은 수행하지 않았습니다.
- 실제 Gleo 호출, 핸들 RECORD 매핑, 내비와 음성 응답, foreground 서비스 수명, 실차 마이크 음질은 미검증입니다.
- 지도 위 표시 권한과 차량별 화면 노출 정책은 기존 proto 구현을 계승했으며 이번 검사로 승인 여부가 입증되지는 않습니다.
- 수신한 원본 음성의 사전 다운로드 방식은 유지합니다. 서버에서 새 개봉을 확인하는 앱 정책이며, 변조된 앱이나 파일 추출을 막는 암호학적 잠금은 아닙니다.

## 기기 검증 순서

1. 기존 proto DB를 가진 기기에서 업데이트 후 목록·오디오·날짜 잠금 보존을 확인합니다.
2. 준비 전 기록 거절, P단 준비 후 마이크 미사용, 명시적 기록 후에만 마이크 사용을 확인합니다.
3. D단·내비 전면에서 보호된 기록 입력과 안내 중단 후 비공개 임시보관을 확인합니다.
4. 날짜 전·구역 밖·위치 만료·서버 오프라인의 새 개봉을 거절하고, 정상 현장 개봉 후 구역을 지나도 재생 가능한지 확인합니다.
5. 안전 일시정지 후 자동 재생이 없고 PLAY로만 재개되는지, PAUSE가 재개를 유발하지 않는지 확인합니다.
6. 새 친구에게 과거 캡슐이 전달되지 않고 철회·친구 해제 후 새 개봉을 거절하는지 확인합니다.
7. 주행 종료·앱 종료·계정 변경 후 서비스와 방문 권한이 끝나는지 확인합니다.
