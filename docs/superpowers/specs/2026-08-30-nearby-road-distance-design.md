# 근처 콘텐츠 도로 거리 정렬 설계

- 이슈: #66 (근처 콘텐츠 조회 API 확장)
- 브랜치: `feat/66` / PR #67 에 이어서 반영
- 날짜: 2026-08-30

## 배경

`GET /api/v1/contents/{id}/nearby` 는 현재 직선거리(Haversine, TourAPI `dist`)로 주변 콘텐츠를
정렬한다. 소도시는 강·산으로 직선상 가까워도 도로로는 크게 우회하는 경우가 많아,
"이 장소 주변" 목록 순서가 실제 방문 편의와 어긋난다.

## 목표

- 근처 콘텐츠를 **실제 자동차 도로 거리** 기준으로 정렬한다.
- 응답에 도로 거리와 소요 시간을 담는다.
- 사용량을 최소화한다. 프론트는 3~4개만 노출하므로 길찾기 호출도 최소로.
- 길찾기 API 장애 시 직선거리 정렬로 폴백한다 (근처 탐색은 best-effort).

## 비목표

- `DayScheduler`(일정 스케줄링, 직선거리 × 1.3 보정)는 이번에 건드리지 않는다.
- 도보·대중교통 경로. 자동차 기준만.
- 경로 폴리라인/내비게이션 데이터. 거리·시간 요약만.

## 외부 API

**Kakao Mobility 여러 목적지 길찾기**
`POST https://apis-navi.kakaomobility.com/v1/destinations/directions`

- 헤더: `Authorization: KakaoAK {REST_API_KEY}`, `Content-Type: application/json`
- 본문: `origin{x,y}` + `destinations[{x,y,key}]` (최대 30) + `radius`(m)
- 응답: `routes[{key, result_code, summary{distance(m), duration(s)}}]`
  - `result_code == 0` 이면 성공. 그 외(목적지 주변 도로 없음 등)는 개별 실패.
- 중심 1점 → 주변 N점을 **1콜**로 조회 → nearby 유스케이스에 정확히 부합.
  1:1 길찾기(후보마다 1콜)나 Tmap(1:1, 별도 가입) 대비 우위.

인증 키: 기존 Kakao 앱의 REST API 키를 재사용한다. 값은 OAuth `client-id` 와 동일하므로
설정에서 `${KAKAO_CLIENT_ID}` 를 그대로 참조한다 (신규 env 변수 없음).

## 데이터 흐름 (`ContentService.getNearbyContents` 확장)

```
1. radius/size 클램프                                   (기존)
2. origin 좌표 확보: 로컬 → 없으면 adapter.fetchDetail    (기존)
3. 후보 수집 (직선거리 정렬):
   - LOCAL:  travelContentRepository.findNearby(...)     — limit 을 size 가 아닌 ROUTE_CANDIDATES
   - TOURAPI: adapter.fetchNearby(...)                   — 내부 numOfRows 를 ROUTE_CANDIDATES 기준으로
   후보가 비면 그대로 빈 응답(폴백 불필요).
4. RoadDistanceResolver.resolve(originLat, originLng, 후보들, size):
   - 좌표 없는 후보 제외 후 직선거리 상위 size 개만 Kakao 여러목적지 길찾기 1콜
     (radius = 가장 먼 후보를 덮되 10km 상한. "radius is mandatory" 이므로 필수)
   - 목적지별 병합:
       result_code == 0  → distanceKm = distance/1000, durationMinutes = round(duration/60), basis = ROAD
       result_code != 0  → 직선거리 유지, durationMinutes = null,                          basis = STRAIGHT
   - Kakao 호출 자체 실패(4xx/5xx/timeout/파싱) → 후보 전체 basis = STRAIGHT, duration = null (예외 던지지 않음)
5. basis 무관하게 distanceKm 오름차순 정렬
6. NearbyContentResponse 반환 (source 는 LOCAL/TOURAPI 그대로)
```

### 라우팅 개수

정확히 직선거리 상위 `size` 개만 라우팅한다. 프론트가 3~4개만 노출하고 사용량을
아끼자는 요구에 맞춰 버퍼를 두지 않는다. Kakao 1콜 상한(목적지 30)·radius 상한(10km).

## 응답 변경 (하위호환 — 필드 추가)

`NearbyContentResponse.NearbyContentItem` 에 추가:

| 필드 | 타입 | 의미 |
|---|---|---|
| `durationMinutes` | `Integer` | 자동차 소요 시간(분). `STRAIGHT` 이면 `null` |
| `distanceBasis` | `enum DistanceBasis { ROAD, STRAIGHT }` | `distanceKm` 산출 기준 |

`distanceKm` 의 값 의미가 바뀐다(가능하면 도로 거리). **동작 변경**이므로 PR 본문·
`api-endpoints.md` 에 명시한다. 필드 자체는 추가만 하므로 역직렬화 호환은 유지.

## 신규 구성요소

| 파일 | 책임 |
|---|---|
| `content/client/KakaoMobilityClient` | Feign. `POST /v1/destinations/directions` |
| `content/client/KakaoMobilityFeignConfig` | `Authorization: KakaoAK` 인터셉터, timeout(connect 3s/read 5s), Jackson decoder. **재시도 없음**(사용량 절약) |
| `content/client/dto/KakaoMultiDestRequest` | `origin`, `destinations`, `radius` |
| `content/client/dto/KakaoMultiDestResponse` | `routes[{key, resultCode, summary{distance,duration}}]` |
| `content/service/RoadDistanceResolver` | 후보 리스트 + origin → 도로거리 병합·정렬. Kakao 실패 격리 |
| `content/dto/response/NearbyContentResponse` | `durationMinutes`, `distanceBasis` 추가 |

`ContentService` 는 LOCAL/TOURAPI 후보를 `List<NearbyContentItem>`(직선거리 채워진 상태)로
확보한 뒤 `RoadDistanceResolver` 를 통과시킨다. 어느 소스든 병합 로직은 동일.

## 설정

```yaml
# application.yaml
kakao-mobility:
  base-url: https://apis-navi.kakaomobility.com
  rest-api-key: ${KAKAO_CLIENT_ID}   # 기존 Kakao 앱 REST API 키 재사용
```

## 오류 처리

| 상황 | 처리 |
|---|---|
| Kakao 4xx/5xx/timeout/파싱 실패 | 로그 warn, 후보 전체 직선거리 정렬(`STRAIGHT`). 예외 없음 |
| 목적지별 `result_code != 0` | 해당 항목만 `STRAIGHT`, 나머지는 `ROAD` |
| 후보 0건 | 빈 `items`, 200 |
| origin 좌표 없음 / 미존재 | 기존과 동일 (`CONTENT_LOCATION_UNKNOWN` / `CONTENT_NOT_FOUND`) |

AGENTS.md "예외 처리 없이 외부 API 호출 금지" 준수: `RoadDistanceResolver` 가
`FeignException`·`RuntimeException` 을 잡아 폴백으로 흡수.

## 테스트

- `RoadDistanceResolverTest` (Mockito): 전부 ROAD / 일부 result_code 실패 → 부분 STRAIGHT /
  Kakao 예외 → 전체 STRAIGHT / 정렬이 도로거리 기준 / 후보 0건
- `ContentServiceTest`: 도로거리 경로가 resolver 를 거치는지, 폴백 시 직선 정렬 유지
- `KakaoMobilityClient` 는 mock. 실서버 검증은 실제 키로 수동 1회
  (프론트 404 나던 contentId 로 `distanceBasis: ROAD` 확인)

## Decision Log 추가 (AGENTS.md)

| 결정 | 이유 |
|---|---|
| 근처 조회 도로거리에 Kakao Mobility 여러목적지 길찾기 | 중심→주변 N점을 1콜로. 사용량 최소. Tmap은 1:1·별도 가입 |
| 길찾기 실패 시 직선거리 폴백(예외 아님) | 근처 탐색은 보조 기능. 완전 실패보다 근사 정렬이 낫다 |
| Kakao Mobility 재시도 없음 | 사용량 절약. 실패하면 즉시 직선 폴백 |

## 향후

- `DayScheduler` 도 같은 클라이언트로 실제 도로거리 전환 검토 (별도 이슈).
- origin-destination 쌍 캐싱 (지금은 요청당 1콜이라 보류).
