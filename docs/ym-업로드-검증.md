# ym 브랜치 자료 공유 검증

2026년 11월 2일, 기존 main의 초기 기획 자료를 보존하고 로컬에서 작성한 결과물을 ym 브랜치에 추가했습니다. main과 다른 팀원 브랜치의 병합은 이 업로드에 포함하지 않습니다.

## 포함한 자료

- Kotlin 네이티브 0.3.0 소스, Gradle wrapper와 의존성 설정, 8개 단위 테스트.
- 서명한 release APK와 SHA256SUMS. 서명키는 포함하지 않습니다.
- 공식 접수양식 기획서, 편집 원고와 생성 도구. 참가자·동의·서명은 비워 두었습니다.
- 전체 사용자 플로우, 주행 UX 리서치, 버전별 에뮬레이터 검증 기록.
- 이전 차량 웹 프로토타입과 초기 모바일 UI 시안. 현재 앱의 정책과 차이가 있어 참고용으로 구분했습니다.

## 이 체크아웃에서 다시 확인한 항목

`JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`

- 8개 단위 테스트 통과.
- Android Lint: 오류 0개, 경고 5개. 기존 target API·외부 MediaBrowser 서비스 관련 경고는 버전별 검증 문서에서 설명합니다.
- debug/release 빌드 성공.
- 업로드 APK의 SHA256이 기존 검증 APK와 일치합니다.
- Python 소스 구문 검사, git diff 공백 오류 검사 통과.
- 업로드 대상에서 개인 서명키·local.properties·환경 파일·빌드 캐시를 제외했습니다. 대표적인 토큰 및 개인키 패턴 검사에서 발견 항목이 없었습니다.

## 검증 범위

새 체크아웃에서 실제 차량이나 에뮬레이터 동작 시험을 다시 수행하지는 않았습니다. 이전 에뮬레이터 시험 결과와 최종 release 시나리오 재검증 중 환경 오류는 evidence/v0.3.0/검증결과.md에 구분했습니다. Gleo 실제 음성 호출, 실물 핸들 버튼, 내비 위 알림 배너, 계정·가족·친구 공유는 완료된 기능이 아닙니다.

GitHub 브랜치 공유는 공모전 접수나 Pleos App Market 승인을 의미하지 않습니다.
