# 수집한 캡슐의 지도 재생

## 사용자 요청과 변경

장소를 벗어나 잠깐 이어 듣는 정책을, 현장에서 수집한 캡슐을 지도에서 다시 듣는 정책으로 바꿉니다. proto의 기존 Compose 디자인을 유지하고 앨범 상단에 수집 지도 진입 버튼을 추가합니다.

- 날짜·현장 위치·수신 권한을 통과하고 원본 음성 파일의 재생이 시작됐을 때 Room에 `collectedAt`을 기록합니다. 내레이션만 나온 경우, 포커스 거절, 파일 오류는 수집이 아닙니다.
- 기존 음성 파일과 좌표를 사용합니다. 수집은 다운로드나 녹음 저장과 다릅니다. 이전 재생 이력은 실제 현장 확인 증거가 아니므로 일괄 수집 처리하지 않습니다.
- 수집한 캡슐은 세션과 앱 재시작 이후에도 위치 조건 없이 재생합니다. 5분 방문 권한과 그 만료 로직을 제거합니다.
- 주차 중 지도에서 재생을 시작할 때 마이크 권한·주행 준비·현재 위치를 요구하지 않습니다. 차량의 주차·화면 제한 검사는 유지합니다. 준비되지 않은 주차 재생에서 앱이 화면을 벗어나거나 주행을 시작하면 멈춥니다.
- 받은 캡슐은 재생할 때 현재 공유 권한을 다시 확인합니다. 철회된 공유를 수집 기록으로 우회하지 않습니다. 현재 일시정지된 재생의 수동 재개만 서버 재검사를 생략합니다.
- 삭제·전체 초기화 시 수집 기록도 제거됩니다. 새 기기 동기화나 다른 계정으로 수집 기록을 이전하는 기능은 포함하지 않습니다.

## 지도

MapLibre Native Android 12.0.1의 `MapView`를 Compose `AndroidView`로 표시합니다. Leaflet JS/CSS와 WebView 코드는 제거했습니다. MapLibre 라이선스는 `app/src/main/assets/native-map/LICENSE.MapLibre.md`에 포함합니다. 현재 Kotlin 2.0 계열과 호환되는 버전을 사용하며, 최신 13.6.1의 Kotlin 2.2.10 요구사항 때문에 앱 전체 Kotlin 업그레이드를 섞지 않았습니다.

배경은 OpenStreetMap 표준 HTTPS raster 타일입니다. 화면에 출처 링크를 상시 표시합니다. MapLibre 전용 HTTP 클라이언트에 앱을 식별하는 User-Agent와 64 MiB 디스크 캐시를 적용하고 서버의 HTTP 캐시 헤더를 따릅니다. `prefetchZoomDelta=0`으로 미리 받기를 끄며 일괄 다운로드나 오프라인 지도 저장을 제공하지 않습니다. 공개 OSM 타일은 가용성을 보장하지 않으므로 서비스 규모가 커지면 별도 공급원으로 전환해야 합니다.

캡슐 ID·좌표·선택 상태는 네이티브 GeoJSON 소스에만 전달하며, 제목·음성·계정은 타일 서버에 보내지 않습니다. 타일 서버는 요청된 지도 영역과 IP를 알 수 있습니다. 숫자 마커는 Android Canvas로 생성하므로 외부 글꼴·아이콘 서버를 사용하지 않습니다.

같은 좌표의 캡슐은 숫자로 묶습니다. 마커를 반복 선택하면 해당 위치의 캡슐을 순서대로 선택하며, 아래 목록에서도 각각 선택할 수 있습니다. 전체 보기·현재 위치 버튼을 제공합니다. 지도 배경 오류 시 안내와 목록 재생을 유지합니다. 화면 시작·재개·중단·종료와 메모리 경고를 `MapView`에 전달합니다. 지도 조작은 기존 주차 상태 검사 안에서만 허용됩니다.

### Pleos 연동

- 현재 위치: 기존 NaviHelper `getCurrentLocationInfo()`의 결과를 공유합니다. 유효한 좌표이면서 수신 시각이 10초 이내인 경우만 파란 점과 현재 위치 버튼을 표시합니다. GPS가 없어도 수집한 캡슐 재생은 가능합니다.
- 길 안내: 수집한 캡슐에서 ‘이 장소로 길 안내’를 누르면 기존 경로가 바뀐다는 확인 팝업을 표시합니다. 확인 후 DB에서 캡슐을 다시 읽고 수집·좌표·주차 상태를 재검사한 다음 기존 `NaviSource.drive()`와 Pleos `requestRoute()`를 사용합니다. 마커 선택이나 재생만으로는 경로를 변경하지 않습니다.
- SDK 요청이 반환되었다는 사실과 실제 내비게이션의 경로 설정 성공을 구분해 ‘길 안내를 요청했어요’라고 표시합니다.
- 지도 렌더러는 MapLibre이며 Pleos 자체 지도 SDK로 표기하지 않습니다. Pleos `Map SDK for Android`는 공식 Maps Platform 목록에 있으나 링크된 문서가 Page Not Found여서 사용 가능 여부를 확정할 수 없습니다.

## 데이터와 검증

Room v4는 nullable `collectedAt`을 추가합니다. v1/v2는 기존 v3 마이그레이션 후 v4로 이동하며 기존 음성·날짜 잠금·공유 정보를 보존합니다. v3 데이터도 최초에는 수집되지 않은 상태입니다. 반복 수집 시 최초 수집 시각을 유지합니다.

검증 명령:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21 bash gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleRelease
python3 scripts/verify-migrations.py
```

네이티브 지도 전환 검증 결과와 기기 검증 범위는 `native-map-validation.md`에 기록합니다.

공식 근거(2026-11-02 확인):
- https://maplibre.org/maplibre-native/android/examples/getting-started/
- https://operations.osmfoundation.org/policies/tiles/
- https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations
- https://document.pleos.ai/product-maps
