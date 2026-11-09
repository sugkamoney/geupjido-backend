# PostGIS 통합 테스트 자료 동기화

2026-11-09, 이슈 #133 검증 중 전체 PostGIS 테스트에서 확인한 로컬 자료 불일치를 정리했다.

## 법정동 경계

현재 권역 매핑의 법정동 코드 650개 중 로컬 `legal-dong-boundaries.geojson`에 396개가 없었다. `LocationDataImportService`의 누락 경계 검증은 유지했다. `python3 -B scripts/location/download_boundaries.py`를 실행해 VWorld에서 누락분을 이어받았고, 로컬 GeoJSON이 650개 코드를 모두 포함한 뒤 실제 경계 통합 테스트가 통과했다. 경계 파일은 `.gitignore` 대상이므로 다른 개발 환경에서는 `.env`의 VWorld 설정을 사용해 다시 생성해야 한다.

## 실거래 승인 표본

용산 2026-09 승인 표본에서 거래명 `한가람`과 후보명 `한가람아파트`는 현재 이름 정규화 규칙상 `NAME_EXACT`다. 후보 단지 ID `A14072701`과 승인 결정은 그대로 두고, 표본의 오래된 `NAME_REVIEW` 판정만 수정했다.

로컬 DB에 거래가 더 적재되면서 같은 승인 조합의 거래 행도 늘었다. 통합 테스트는 예전 거래 행 수 18·16을 고정해서 비교하지 않고, 현재 승인 조합에 해당하는 거래를 조회해 연결 결과를 검증한다. 표본 검증에서 전체 `apartment_trade`를 `findAll()`로 읽는 방식도 제거하고, 표본별 키로 조회하도록 바꿨다.

## 메모리 제한을 적용한 전체 검증

PostGIS 통합 테스트를 모두 실행할 때는 다음 명령을 사용한다.

```bash
RUN_POSTGIS_IT=true ./gradlew clean build --max-workers=1 -I scripts/test/postgis-memory.gradle
```

이 설정은 테스트 JVM의 최대 힙을 4GB로 제한하고, 테스트 클래스를 한 번에 하나씩 별도 JVM에서 실행해 이전 클래스의 객체가 누적되지 않게 한다. 일반 `./gradlew clean build`의 설정은 바꾸지 않는다.
