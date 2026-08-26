---
name: picktrip-ai-integration
description: Use when implementing ItineraryService AI generation flow, AiItineraryClient interface, AI response parsing, or failure handling for timeout and provider errors in the PickTrip project.
---

# PickTrip AI 일정 생성 연동 패턴

## Overview

AI 일정 생성은 **입력 검증 → AI 호출 → 응답 파싱·검증 → 미리보기 반환** 순서로 처리한다.
응답은 즉시 저장하지 않으며, 저장은 별도 요청으로 분리한다.

## 연동 흐름

```
클라이언트
  │
  ▼
ItineraryService
  │  1. 입력 검증 (지역, 날짜, 콘텐츠 2개 이상)
  │  2. 콘텐츠 상세 정보 로드 (운영시간, 체류시간, 행사 기간 등)
  │  3. 여행 조건 + 콘텐츠 목록 → AI 입력 변환
  ▼
AiItineraryClient  (infra/ai/)
  │  4. AI 제공자 호출
  │  5. 응답 파싱 → 일정 도메인 모델로 변환
  ▼
ItineraryService
  │  6. 변환된 일정 반환 (저장 전 미리보기)
  ▼
클라이언트
```

## 패키지 구조

```
src/main/java/travel_agency/pick_trip/infra/ai/
└── AiItineraryClient.java   ← AI 제공자 호출 인터페이스
```

도메인 코드(`itinerary` 패키지)는 `AiItineraryClient` 인터페이스에만 의존한다.
구현체(프로바이더별)는 `infra/ai` 패키지에 위치시켜 교체 가능하게 유지한다.

## AI 입력에 포함되는 정보

| 항목 | 원천 | 비고 |
|------|------|------|
| 여행 날짜/기간 | 바구니 조건 | 필수 |
| 동행·스타일 조건 | 바구니 조건 | 필수. enum 코드가 아니라 한국어 라벨(`TravelCondition.getLabel()`)로 전달 |
| 장소명 | `TravelContent.title` | 필수 |
| 카테고리 | `TravelContent.category` | 필수 |
| 좌표 | `latitude`, `longitude` | 동선 계산 필수 |
| 운영시간 | `ContentDetail.useTime` | 품질 검수 필요 |
| 휴무일 | `ContentDetail.restDate` | 품질 검수 필요 |
| 행사 기간 | `eventStartDate`, `eventEndDate` | 축제 배치 제약 |
| 우선순위 | `BasketItem.priority` | "꼭 가기" 우선 배치. enum 코드가 아니라 한국어 라벨(`Priority.getLabel()`)로 전달 |
| 예상 체류 시간 | 자체 검수 데이터 | TourAPI만으로 부족 |
| 실내/실외 | `ContentDetail.indoorOutdoor` | 우천 대안 판단 |
| 걷기 부담 | `ContentDetail.walkingLevel` | 부모님/아이 동반 고려 |
| 주차 | `ContentDetail.parking` | 가족 여행 중요 필드 |

## AI 응답 처리 규칙

- AI 응답은 즉시 저장하지 않고 먼저 **도메인 모델로 파싱·검증**한다.
- 파싱 성공 시 일정 미리보기를 반환하고, 저장은 별도 요청으로 처리한다.
- **AI 응답의 장소는 입력(바구니) contentId 집합으로 필터링**한다. 시스템 프롬프트의
  "입력 contentId 만 사용" 지시는 강제가 아니므로, 모델이 임의의 장소를 섞어 넣어도
  사용자가 담지 않은 장소가 일정에 노출되지 않게 `ItineraryService` 에서 걷어낸다.
  제거된 contentId 는 WARN 로그로만 남기고, 일차 구성·순서는 그대로 둔다.
- 각 장소 배치에 **AI가 생성한 이유**(`reason`)를 함께 반환한다.
  - 예: "축제 운영시간이 오전 10시부터라서 1일차 오전에 배치했습니다."
- **`reason` 문구는 서버에서 후처리**한다 (`ReasonSanitizer`). 시스템 프롬프트로 금지를 지시해도
  강제가 아니므로, contentId 표기·괄호 안 숫자 ID·동행/우선순위 enum 코드를 걷어내고 enum 코드는
  한국어 라벨로 치환한다. 생성 미리보기와 저장 경로(`save`·`modify`·`regenerate`) 모두에 적용하며,
  바뀐 건수는 WARN 로그로만 남긴다.

## 실패 처리 전략

| 실패 유형 | HTTP 상태 | 에러 코드 | 처리 방법 |
|----------|-----------|----------|----------|
| 입력 조건 부족 | 400 | `ITINERARY_INPUT_INSUFFICIENT` | AI 호출 전 서버에서 즉시 반환 |
| AI 응답 타임아웃 | 408 | `ITINERARY_GENERATION_TIMEOUT` | 재시도 가능 상태로 반환 |
| AI 제공자 장애 | 502 | `ITINERARY_PROVIDER_FAILED` | 서버 로그에 원인 기록 |
| 응답 파싱 실패 | 502 | `ITINERARY_PROVIDER_FAILED` | AI 응답 원문은 로그에만 기록 |

에러 코드 구현은 [[picktrip-error-handling]] 참고.

## 보안 규칙

- AI에 전달하는 **프롬프트 전문은 로그에 남기지 않는다**.
- AI 응답 원문도 운영 로그에 포함하지 않는다. 디버그 레벨에서만 허용한다.
- AI 제공자 API key는 `.env`에 정의하고 Git에 커밋하지 않는다.

보안 규칙 전체는 [[picktrip-security-checklist]] 참고.
