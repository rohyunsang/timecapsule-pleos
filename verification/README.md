# 검증 스크립트

실차에서 실행하지 않습니다. tc_ui.py는 emulator-* 시리얼만 허용합니다. Virtual Console이 활성 상태여야 하며 테스트 APK는 별도 focusProbe 모듈입니다. 테스트 APK를 사용자에게 배포하거나 제출 APK로 소개하지 않습니다.

- `test_driving_recording.py`: 0.3.0 debug APK에서 명시적 기록 명령, D단·내비 전면 녹음, 중복 요청, 15초 자동 저장과 시작 좌표 보존, NAVIGATION 포커스 임시보관, 취소, PLAY/RECORD 구분, 방향지시등과 STOP을 확인합니다. 테스트가 만든 녹음만 앱 UI에서 삭제합니다.
- `test_navigation_ux.py`: debug APK에서 합성 무음 AAC와 잠긴 미래 캡슐을 넣어 청취를 검증합니다. 원래 메타데이터를 백업하고 보존합니다. `--reuse-fixtures`는 준비된 fixture를 사용하여 실제 release APK를 검증합니다.
- `test_release_recording.py`: release APK에서 D단 녹음과 자동 저장 로그, 주차 보관함, 시험 녹음 삭제를 확인합니다.
- `test_draft_ux.py`, `test_background.py`, `test_audio_and_recording.py`: 이전 0.1/0.2 정책의 역사적 스크립트입니다. 0.3.0의 기대 결과로 사용하지 않습니다.

준비: `./gradlew :focusProbe:assembleDebug`로 도구 APK를 빌드하고 앱과 같은 로컬 키로 서명해 설치합니다. 같은 서명은 MediaBrowser 신뢰 검사용이며 타사 앱의 임의 접근을 허용하는 뜻이 아닙니다. 실제 Gleo·핸들 버튼·내비 경로 안내는 별도 플랫폼 시험이 필요합니다.

빌드 검증: `./scripts/build-local.sh`가 8개 단위 테스트, Lint, debug/release 빌드와 release 서명 검사를 실행합니다. 검증 후 테스트 APK를 제거하고 테스트 데이터만 정리합니다.
