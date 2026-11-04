# 차량용 앨범 UI 검토

확인일: 2026-11-04. 대상은 주차 상태에서 사용하는 TimeCapsule 앨범입니다. 주행 중 화면 접근 제한이나 개봉·공유 권한을 변경하는 작업은 아닙니다.

## 조사한 공식 레퍼런스

1. [Pleos Connect UI Design Guide](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro): 짧은 시간에 이해할 수 있는 핵심 정보, 일관된 조작, 충분한 대비, 간결한 문구를 기준으로 삼았습니다.
2. [Pleos Typography](https://document.pleos.ai/docs/connect/guide/docs-design/foundations/typography): 기존 Asta Sans와 정보 위계에 따른 글자 크기를 유지했습니다.
3. [Pleos Display Layout](https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/layout): 앱 창 크기가 바뀌므로 좁은 창에서도 기능이 잘리거나 버튼 라벨이 세로로 나뉘지 않도록 구성했습니다.
4. [Android Automotive List items](https://developers.google.com/cars/design/automotive-os/components/list-items): 한 열 목록, 주 정보와 보조 정보 분리, 서로 다른 동작의 터치 영역 분리, 화면 폭에 따른 배치를 참고했습니다. 기본 제목은 32sp, 보조 정보는 24sp로 맞췄습니다.
5. [Android Automotive Tabs](https://developers.google.com/cars/design/automotive-os/components/tabs): 일반 실행 버튼과 구별되는 고정 탭과 선택 표시를 참고했습니다.
6. [Android Automotive custom screen guidance](https://developers.google.com/cars/design/automotive-os/apps/media/guidelines-custom-screens): 해당 문서는 미디어 앱의 설정·로그인 화면을 대상으로 합니다. 앱 전체에 강제되는 인증 기준으로 해석하지 않고, 76dp 이상의 터치 영역과 24dp 간격 권장을 참고했습니다. 이 앨범의 주요 버튼에는 88dp를 적용했습니다.

## 변경한 배치

- 헤더, 탭, 목록의 최대 폭을 1320dp로 맞춰 좌우 정렬을 통일했습니다.
- 탭은 배경 전체를 채우는 큰 버튼 대신 밑줄과 숫자 배지로 선택 상태를 표시합니다. 제목과 숫자를 각 탭 중앙에 정렬했습니다.
- 헤더의 수집 지도 진입은 지도 아이콘으로 바꾸고, 88dp 터치 영역과 접근성 이름을 유지했습니다.
- 임시보관은 개수가 표시되는 접이식 영역으로 모았습니다. 펼치면 미리 듣기·저장·삭제가 가능합니다.
- 날짜와 개수는 한 줄로 줄이고, 각 캡슐은 독립적인 목록 항목으로 만들었습니다.
- 카드의 정보는 상태/메타데이터, 장소 제목, 한 줄 안내 순서로 고정했습니다. 상태가 버튼 아래에서 여러 줄로 갈라지는 배치를 제거했습니다.
- 캡슐 그림은 80dp 영역에 담고 카드 모서리는 16dp로 정리했습니다.
- 재생/잠금과 삭제는 서로 옆에 놓고 88dp 크기, 24dp 간격을 적용했습니다. 잠금 버튼의 길 안내 확인과 삭제 재확인은 유지합니다.
- 공개/전달/저장 버튼은 카드 내부에 배치했습니다. 1120dp 미만에서는 보조 동작만 카드 안의 아래 영역으로 옮깁니다. 820dp 미만에서는 장식용 그림을 생략해 읽는 공간을 확보합니다.
- 기존 주간/야간 색상을 사용하며, 미수집 타인의 캡슐을 숨기는 정책과 받은 캡슐의 즉시 재생 정책을 유지했습니다.

## 검증 범위

빌드·lint와 에뮬레이터의 실제 화면을 확인합니다. 목록 크기·스크롤·창 너비·잠금 확인·삭제 확인을 검증하며, 실차 주행 중 가독성을 검증했다고 간주하지 않습니다. 테스트용 친구 캡슐의 서버 권한은 실제 공유 계정을 대체하지 않습니다.
