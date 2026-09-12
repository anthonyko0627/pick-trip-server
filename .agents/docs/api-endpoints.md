# API 엔드포인트 초안

이 문서는 PickTrip 서버가 클라이언트에 제공하는 내부 REST API 목록을 정의한다.
실제 요청/응답 스키마는 구현 후 OpenAPI(Swagger)로 확정한다.

## 공통 규칙

- 기본 접두사: `/api/v1`
- 인증이 필요한 API: `Authorization: Bearer <accessToken>` 헤더 필요
- 비로그인 허용 API: 인증 헤더 없이 호출 가능

## Auth

| 메서드  | URL                             | 인증 필요 | 설명                          |
| ------ | ------------------------------- | :------: | ----------------------------- |
| GET    | `/oauth2/authorization/kakao`   | X        | 카카오 로그인 시작             |
| GET    | `/oauth2/authorization/google`  | X        | 구글 로그인 시작               |
| GET    | `/login/oauth2/code/{provider}` | X        | 소셜 로그인 콜백 (Spring 처리)  |
| POST   | `/api/v1/auth/token/refresh`    | X        | 액세스 토큰 재발급             |
| DELETE | `/api/v1/auth/logout`           | O        | 로그아웃 (토큰 폐기)           |

현재 로그인 사용자 정보 조회는 `GET /api/v1/users/me` 로 제공한다(사용자 섹션 참고).

### 소셜 로그인 흐름

카카오·구글 모두 Spring Security 의 `oauth2Login` 이 처리한다. 클라이언트는 인가 코드를 다루지 않는다.

1. 클라이언트가 `/oauth2/authorization/{kakao|google}` 로 이동
2. 공급자 로그인·동의 후 `/login/oauth2/code/{provider}` 로 콜백
3. 서버가 토큰 교환·사용자 저장 후, `app.oauth2.redirect-uri` 로
   `?accessToken=...&refreshToken=...` 을 붙여 리다이렉트

새 공급자를 추가하려면 `application.yaml` 에 registration·provider 를 넣고
`OAuth2UserInfoFactory` 에 분기를 추가한다.

## 사용자

| 메서드 | URL                | 인증 필요 | 설명                                                                  |
| ----- | ------------------ | :------: | --------------------------------------------------------------------- |
| GET   | `/api/v1/users/me` | O        | 현재 로그인 사용자 정보 조회                                            |
| DELETE | `/api/v1/users/me` | O       | 회원 탈퇴 (소프트 삭제, 30일 후 일괄 하드 삭제. 유예 기간 내 재로그인 시 복구) |

## 콘텐츠

| 메서드 | URL                      | 인증 필요 | 설명                                      |
| ----- | ------------------------ | :------: | ----------------------------------------- |
| GET   | `/api/v1/contents`            | X        | 콘텐츠 목록 조회 (지역, 카테고리, 필터 등) |
| GET   | `/api/v1/contents/{id}`       | X        | 콘텐츠 상세 조회                           |
| GET   | `/api/v1/contents/{id}/nearby` | X       | 해당 콘텐츠 좌표 기준 반경 내 주변 콘텐츠 조회 (거리순) |

목록·상세 응답의 각 콘텐츠에는 관광객수 지표 `visitorStats` 가 붙는다 (없으면 `null`).

- `totalVisitors` — 기간 누적 방문자수. 자체 프록시일 때는 그 콘텐츠가 바구니에 담긴 횟수.
- `dailyAverageVisitors` — 일평균 방문자수. 자체 프록시일 때는 `null`.
- `period` — 집계 기간 (예: `"2026-05~2026-06"`). 자체 프록시일 때는 `null`.
- `source` — `"한국관광공사 지역별 방문자수"`(공공데이터포털 15101972) 또는 `"PickTrip 내부 지표"`.
- `baseDate` — 기준일 (`yyyy-MM-dd`).
- `approximate` — **항상 `true`**. 개별 장소 단위 관광객수를 주는 공개 데이터가 없어, 지역(시군구) 단위 통계를 그 지역 콘텐츠 값으로 그대로 내려주거나 자체 프록시로 대체하기 때문이다.

폴백 순서는 `지역 통계 → 자체 프록시(바구니에 담긴 횟수) → null` 이다. 지표 조회가 실패해도 콘텐츠 응답 자체는 정상 반환하며 `visitorStats` 만 `null` 이 된다.

`GET /api/v1/contents/{id}/nearby` 는 쿼리 파라미터 `radiusKm`(기본 5, 최대 20)과 `size`(기본 10, 최대 30)를 받는다.

응답 각 항목의 거리·시간 필드:

- `distanceKm` — 거리(km). `distanceBasis` 로 산출 기준을 구분한다.
- `distanceBasis` — `ROAD`(Kakao Mobility 길찾기로 계산한 실제 자동차 도로 거리) 또는 `STRAIGHT`(직선 거리, 길찾기 실패 시 폴백).
- `durationMinutes` — 자동차 소요 시간(분). `distanceBasis` 가 `STRAIGHT` 이면 `null`.

정렬은 항상 `distanceKm` 오름차순이다. 직선 거리로 상위 `size` 개 후보를 추린 뒤 그 후보만 도로 거리를 조회한다(사용량 절약). 길찾기 API 장애 시 직선 거리 정렬로 폴백하며, 목적지별로 경로를 찾지 못한 항목만 `STRAIGHT` 로 표시된다(부분 폴백).

조회 소스는 응답의 `source` 필드로 구분한다.

- `LOCAL` — 로컬 적재분(`travel_contents`)에서 Haversine 근사로 조회. 기준 콘텐츠가 로컬에 있고 좌표가 유효하며 반경 내 로컬 행이 1건 이상일 때.
- `TOURAPI` — 로컬로 답할 수 없어(기준 콘텐츠 미적재 · 좌표 없음/(0,0) · 반경 내 로컬 행 0건) TourAPI `locationBasedList2`로 조회. 좌표는 로컬 또는 TourAPI 상세에서 확보하며, 기준 콘텐츠 자신·MVP 외 콘텐츠 타입은 제외한다. 이 소스에서는 `summary`가 항상 `null`이고, 대상 지역(하동·영주·예천) 밖 항목은 `region`이 `null`이다.

두 소스를 섞지 않는다. TourAPI가 기준 콘텐츠를 모르면 `CONTENT_NOT_FOUND`, 로컬·TourAPI 어디에서도 좌표를 얻지 못하면 `CONTENT_LOCATION_UNKNOWN`, TourAPI 호출이 실패하면 `CONTENT_PROVIDER_FAILED` 를 반환한다.

## 여행 바구니

| 메서드  | URL                               | 인증 필요 | 설명                     |
| ------ | --------------------------------- | :------: | ------------------------ |
| GET    | `/api/v1/baskets`                 | O        | 여행 바구니 조회          |
| POST   | `/api/v1/baskets/items`           | O        | 바구니에 콘텐츠 추가      |
| PATCH  | `/api/v1/baskets/items/{itemId}`  | O        | 바구니 항목 우선순위 변경 |
| DELETE | `/api/v1/baskets/items/{itemId}`  | O        | 바구니에서 콘텐츠 제거    |

## 일정

| 메서드 | URL                                       | 인증 필요 | 설명                     |
| ----- | ----------------------------------------- | :------: | ------------------------ |
| POST  | `/api/v1/itineraries/generate`            | O        | AI 일정 생성 요청         |
| POST  | `/api/v1/itineraries`                     | O        | 일정 저장                 |
| GET   | `/api/v1/itineraries`                     | O        | 저장된 일정 목록 조회 (요약, 최근 수정순) |
| GET   | `/api/v1/itineraries/{id}`                | O        | 저장된 일정 상세 조회     |
| PATCH | `/api/v1/itineraries/{id}`                | O        | 일정 수정 (순서, 추가 등) |
| POST  | `/api/v1/itineraries/{id}/regenerate`     | O        | 전체 또는 하루 일정 재생성|
| DELETE| `/api/v1/itineraries/{id}`                | O        | 저장된 일정 삭제          |

`POST /api/v1/itineraries/generate` 는 선택 요청 바디를 받는다. 바디를 보내지 않으면 기존과 동일하게 동작한다.

```json
{
  "mode": "STRICT",
  "startContentId": "773075",
  "travelModes": ["CAR", "TRANSIT"]
}
```

| 필드   | 타입   | 기본값     | 설명                                                          |
| ------ | ------ | ---------- | ------------------------------------------------------------- |
| `mode` | enum   | `STRICT`   | `STRICT` = 바구니에 담은 장소만으로 구성. `AUGMENT` = AI 가 같은 지역의 적재 콘텐츠를 추가 제안할 수 있음 |
| `travelModes` | enum 배열 | `["CAR"]` | 만들 일정안의 이동수단. `CAR`(자동차) · `TRANSIT`(대중교통). 모드마다 일정안이 하나씩 나오며, 중복은 제거하고 최대 4개까지만 만든다. 미지정·빈 배열이면 기존과 같은 자동차 단일안이다 |
| `startContentId` | string | 없음 | 여행을 시작할 바구니 항목의 `contentId`. 일차 배분과 하루 순서 최적화는 이 값과 무관하게 항상 수행하며(AI 배분은 초기값), 이 값은 앵커 고정 여부만 결정한다. 지정하면 그 장소에서 동선을 시작해 해당 일차의 첫 스톱으로 고정하고, 미지정이면 첫 스톱도 최적화 대상이 된다. 바구니에 없는 값이면 `ITINERARY_INPUT_INSUFFICIENT` |

`AUGMENT` 에서는 같은 지역의 유효 콘텐츠 후보를 AI 프롬프트에 함께 실어 보내고, 응답으로 돌아온 장소 중 DB 에 없거나 다른 지역인 것은 서버가 제거한다. 추가된 장소는 응답 항목의 `addedByAi` 가 `true` 이며, 사용자가 저장 전에 제거할 수 있다.

응답에는 이동수단별 일정안이 `variants` 배열로 담긴다.

```jsonc
{
  "title": "...",        // = variants[0].title
  "region": "HADONG",
  "travelDate": "2026-10-01",
  "duration": 2,
  "days": [],            // = variants[0].days
  "adjustments": [],     // = variants[0].adjustments
  "suggestions": [],     // 최상위 유지
  "variants": [
    {
      "label": "자동차 힐링 루트", "travelMode": "CAR", "title": "...", "days": [], "adjustments": [],
      "metrics": {
        "totalTravelMinutes": 50,
        "totalWalkingMinutes": 0,
        "totalTransitCost": 3158,
        "placeCount": 3,
        "unavailableReasons": {}
      }
    }
  ]
}
```

**하위호환 규칙**: 최상위 `title`·`days`·`adjustments` 는 `variants[0]` 의 값을 그대로 복제한 것이다.
`variants` 를 모르는 기존 클라이언트는 지금까지처럼 최상위 필드만 읽으면 되고, 이동수단을 지정하지 않으면
`variants` 는 자동차 안 하나뿐이라 기존 응답과 내용이 같다.

각 `variants` 항목의 필드는 `label`(사용자 노출용 일정안 이름), `travelMode`, `title`, `days`, `adjustments`,
`metrics`(안끼리 비교하는 지표) 다.

### `variants[].metrics` — 일정안 비교 지표

스플릿 뷰에서 안을 나란히 놓고 비교하기 위한 값이다. 별도 비교 엔드포인트는 없고 생성 응답에 그대로 포함한다.

| 필드 | 타입 | 설명 |
|------|------|------|
| `totalTravelMinutes` | number\|null | 전 일차 이동 시간(분) 합. 일차별 `days[].totalTravelMinutes` 의 합과 같다 |
| `totalWalkingMinutes` | number\|null | 도보로 분류된 구간의 시간(분) 합. `CAR` 은 주차 후 도보를 모델에 두지 않아 항상 0 이다 |
| `totalTransitCost` | number\|null | 총 예상 교통비(원). 상수 기반 개략 추정이다 |
| `placeCount` | number\|null | 전 일차 방문 장소 수 |
| `unavailableReasons` | object | 산출하지 못한 지표의 사유 코드(`지표명` → 코드). 전부 산출되면 `{}` |

**모든 안이 같은 키 집합을 반환한다.** 산출할 수 없는 지표도 키를 빼지 않고 값을 `null` 로 내리며,
그 이유를 `unavailableReasons` 에 담는다. 스플릿 뷰가 열을 맞춰 그릴 수 있게 하기 위함이다.

```jsonc
"metrics": {
  "totalTravelMinutes": 40,
  "totalWalkingMinutes": 0,
  "totalTransitCost": null,
  "placeCount": 3,
  "unavailableReasons": { "totalTransitCost": "UNKNOWN_TRAVEL_DISTANCE" }
}
```

사유 코드는 다음과 같다.

| 코드 | 의미 |
|------|------|
| `UNKNOWN_TRAVEL_DISTANCE` | 구간 좌표(또는 도로 거리)가 하나도 없어 이동 거리를 잴 근거가 없다 |

"해당 없음"은 산출 불가가 아니다. 예를 들어 `CAR` 의 `totalWalkingMinutes` 는 언제나 `0` 이며 사유 코드를 남기지 않는다.
방문 장소가 0~1곳이라 이동 구간 자체가 없으면 교통비는 `null` 이 아니라 `0` 원이다.

### 교통비 계산 정책

교통비는 실시간 요금 조회가 아니라 상수 기반 개략 추정이다. 단가는 `application.yaml` 의 `itinerary.cost` 설정값이며,
유가·요금이 개정되면 코드가 아니라 설정만 바꾼다.

| 설정 | 기본값 | 근거 |
|------|--------|------|
| `itinerary.cost.car-cost-per-km-won` | 142 | 휘발유 1,700원/L ÷ 연비 12km/L ≒ 142원/km |
| `itinerary.cost.transit-base-fare-won` | 1500 | 경남·경북 시내버스 성인 현금 기본요금(2025년 기준) |

- `CAR` — `총 이동 km × car-cost-per-km-won`. 총 이동 km 는 일차별 `totalTravelKm` 의 합이다.
  **통행료는 0 으로 근사한다.** 하동·영주·예천은 고속도로보다 국도 비중이 높아 유료 구간을 지나는 경우가 드물다.
  주차비도 장소별 요금 데이터가 없어 포함하지 않는다.
- `TRANSIT` — `transit-base-fare-won × 승차 횟수`. 승차 횟수는 실제 이동 거리(직선거리 × 우회 계수 1.3)가
  `WALK_MAX_KM`(2.0km)을 넘는 구간 수다. 그 이하 구간은 도보로 보고 요금을 매기지 않으며, 도보 판정 경계는
  이동시간 환산과 같은 상수를 쓴다.
  **국내에 지역 시내버스 요금 공개 API 가 없어 환승 할인·거리 비례(시계외) 요금을 반영하지 못한다.**
  대중교통 길찾기 API(ODsay 등)를 붙이면 실측 요금으로 교체한다.
- 좌표가 없어 구간 거리를 하나도 재지 못하면 교통비는 `null` + `UNKNOWN_TRAVEL_DISTANCE` 다.
  일부 구간만 거리를 모르면 잰 구간만으로 근사한다(과소 추정).

이동시간 모델은 이동수단마다 다르다.

- `CAR` — Kakao Mobility 여러 목적지 길찾기로 얻은 실제 도로 거리·소요 시간을 쓴다. 길찾기가 실패하거나
  기준점 반경 10km 를 벗어나 응답에서 빠진 구간은 직선거리 × 우회 계수(1.3) ÷ 평균 속도로 폴백한다.
  이때 평균 속도는 실측에 성공한 구간에서 관측한 값을 쓰고, 성공한 구간이 하나도 없으면 35km/h 를 쓴다.
- `TRANSIT` — 실제 이동 거리 2.0km(`WALK_MAX_KM`) 이하는 도보 3.5km/h, 그보다 먼 구간은 대기 15분 +
  시내버스 25km/h 로 환산한다. 노선·배차를 모르는 근사이며 대중교통 길찾기 API 연동 시 실측으로 교체한다.

일정안이 여러 개여도 AI 는 한 번만 호출한다. 같은 AI 결과·같은 장소 집합으로 스케줄링만 이동수단별로 다시 돌린다.

### 도보 경사 반영과 휴식 스톱 자동 삽입

도보로 분류되는 구간(= `TRANSIT` 이면서 실제 이동 거리가 `WALK_MAX_KM` 이하)에는 오르막 추가 시간이 붙는다.
장소 좌표의 해발고도를 조회해(고도 소스는 `.agents/docs/elevation-source.md`) 구간 누적 상승고도를 구하고,
Naismith 규칙 근사(상승 600m 당 +60분)로 이동시간에 더한다. 자동차 구간에는 붙지 않으며, 고도를 모르는 구간은
상승고도 0·페널티 0 이라 고도 조회 이전과 결과가 같다. 고도 조회는 일정안 수와 무관하게 요청당 한 번만 한다.

마지막 휴식 이후 **누적 도보 시간이 90분** 이상이거나 **누적 상승고도가 200m** 이상이면, 그 스톱 뒤에 근처 카페를
휴식 스톱으로 자동 삽입하고 누적값을 리셋한다(한 일차에 여러 번 삽입될 수 있다). 삽입 대상은 `TRANSIT` 안뿐이다.

- 삽입된 항목은 응답 항목의 **`addedForRest` 가 `true`** 이며, `reason` 에 삽입 이유(도보 시간 초과 / 상승고도 초과 /
  둘 다)가 한국어로 담긴다. AI 추가 제안(`addedByAi`)과는 구분되며 두 값이 동시에 `true` 가 되지 않는다.
- 실제 콘텐츠(`contentId`·`title`·방문 시각을 모두 가진 근처 음식 분류 콘텐츠)라 저장·수정 요청에 그대로 실어 보낼 수
  있고, 사용자가 저장 전에 지울 수도 있다.
- 체류 시간은 30분이며, 뒤따르는 스톱의 방문 시각은 그만큼 밀린다.
- 이미 그 일정에 있는 장소는 후보에서 제외한다. 근처에 카페가 없거나 조회가 실패하면 예외 없이 삽입만 건너뛴다.

`POST /api/v1/itineraries/generate` 응답 최상위에는 혼잡 기반 순서변경 제안 `suggestions` 배열이 있다 (제안이 없으면 빈 배열). 제안은 첫 번째 안(`variants[0]`)의 확정 시각을 기준으로 만들며 최상위에만 둔다.

- `type` — 제안 종류. 현재는 `CONGESTION_REORDER` 뿐이다.
- `message` — 사용자에게 보여줄 한국어 문장.
- `dayIndex` — 제안이 적용되는 일차.
- `contentId` — 붐비는 장소.
- `swapWithContentId` — 대신 먼저 방문할 장소 (없으면 `null`).

확정된 방문 시작 시각이 속한 시간대에 그 장소의 혼잡이 `HIGH` 이고, 같은 일차 뒤쪽에 같은 시간대 혼잡이 더 낮은 장소가 있을 때만 제안한다. **서버는 제안대로 재정렬하지 않는다.** 사용자가 수락하면 클라이언트가 순서를 바꾼 일정으로 `PATCH /api/v1/itineraries/{id}` 를 호출해 반영한다. 혼잡 조회가 실패해도 일정 생성은 정상 진행되며 `suggestions` 는 빈 배열이 된다.

목록 조회는 요약 정보(`itineraryId`, `title`, `region`, `travelDate`, `duration`, `lastModifiedAt`)만 반환하며 일차·항목은 포함하지 않는다. 상세는 `GET /api/v1/itineraries/{id}` 를 사용한다. 일정 삭제 시 해당 일정의 활성 공유 토큰도 함께 비활성화된다.

## 일정 공유

| 메서드 | URL                              | 인증 필요 | 설명                         |
| ----- | -------------------------------- | :------: | ---------------------------- |
| POST  | `/api/v1/itineraries/{id}/share` | O        | 공유 링크(토큰) 생성          |
| GET   | `/api/v1/share/{token}`          | X        | 공유 토큰으로 일정 조회       |
