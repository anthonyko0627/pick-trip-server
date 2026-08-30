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

| 메서드 | URL                | 인증 필요 | 설명                     |
| ----- | ------------------ | :------: | ------------------------ |
| GET   | `/api/v1/users/me` | O        | 현재 로그인 사용자 정보 조회 |

## 콘텐츠

| 메서드 | URL                      | 인증 필요 | 설명                                      |
| ----- | ------------------------ | :------: | ----------------------------------------- |
| GET   | `/api/v1/contents`            | X        | 콘텐츠 목록 조회 (지역, 카테고리, 필터 등) |
| GET   | `/api/v1/contents/{id}`       | X        | 콘텐츠 상세 조회                           |
| GET   | `/api/v1/contents/{id}/nearby` | X       | 해당 콘텐츠 좌표 기준 반경 내 주변 콘텐츠 조회 (거리순) |

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

목록 조회는 요약 정보(`itineraryId`, `title`, `region`, `travelDate`, `duration`, `lastModifiedAt`)만 반환하며 일차·항목은 포함하지 않는다. 상세는 `GET /api/v1/itineraries/{id}` 를 사용한다. 일정 삭제 시 해당 일정의 활성 공유 토큰도 함께 비활성화된다.

## 일정 공유

| 메서드 | URL                              | 인증 필요 | 설명                         |
| ----- | -------------------------------- | :------: | ---------------------------- |
| POST  | `/api/v1/itineraries/{id}/share` | O        | 공유 링크(토큰) 생성          |
| GET   | `/api/v1/share/{token}`          | X        | 공유 토큰으로 일정 조회       |
