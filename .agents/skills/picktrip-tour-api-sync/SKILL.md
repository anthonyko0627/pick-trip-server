---
name: picktrip-tour-api-sync
description: Use when implementing TourAPI Feign client, content collection batch, sync scheduler, or mapping TourAPI response fields to internal domain models in the PickTrip project.
---

# PickTrip TourAPI 수집·동기화 패턴

## Overview

TourAPI 수집은 **지역 코드 → 분류 코드 → 콘텐츠 → 축제 → 이미지 → 동기화** 순서로 진행한다.
개발 계정 트래픽은 일 1,000건 기준이므로 배치와 캐시를 전제로 설계한다.

## 사용 API

- `국문 관광정보 서비스_GW`: 지역 콘텐츠, 행사, 상세정보, 이미지, 코드, 동기화의 1차 원천
- `관광사진 정보_GW`: 콘텐츠 대표 이미지가 부족할 때 보조 이미지 원천

두 API 모두 동일한 인증키(`PUBLIC_DATA_PORTAL_KEY`)를 사용한다.

## API 공통 규칙

- 응답 형식: `_type=json`
- 공통 요청 파라미터: `serviceKey`, `MobileOS=ETC`, `MobileApp=PickTrip`, `pageNo`, `numOfRows`
- 인증키는 **헤더가 아닌 쿼리 파라미터** `serviceKey`로 주입한다. (→ [[picktrip-security-checklist]])

## MVP 필수 오퍼레이션

| 오퍼레이션 | 사용 목적 | 우선순위 |
|-----------|----------|---------|
| `/areaCode2` | legacy 시도/시군구 코드 seed (지역 필터에는 사용하지 않음) | 보조 |
| `/ldongCode2` | 하동/영주/예천 법정동 코드 확인 (**지역 필터 기준**) | 필수 |
| `/categoryCode2` | 관광 타입 카테고리 코드 확인 | 필수 |
| `/lclsSystmCode2` | 신분류체계 1/2/3Depth 코드 수집 | 필수 |
| `/areaBasedList2` | 하동/영주/예천 콘텐츠 후보 수집 | 필수 |
| `/searchKeyword2` | 사용자 검색어 기반 콘텐츠 탐색 | 필수 |
| `/searchFestival2` | 여행 날짜와 행사/축제 기간 매칭 | 필수 |
| `/detailCommon2` | 주소, 좌표, 개요, 홈페이지 등 | 필수 |
| `/detailIntro2` | 운영시간, 휴무, 주차 등 타입별 상세 | 필수 |
| `/detailInfo2` | 코스, 부대시설, 요금, 체험 항목 등 | 필수 |
| `/detailImage2` | 콘텐츠 상세 이미지 | 필수 |
| `/areaBasedSyncList2` | 콘텐츠 변경/삭제/수정 동기화 | 필수 |
| `/gallerySearchList1` | 지역명/키워드 기반 보조 이미지 검색 | 보조 |
| `/gallerySyncDetailList1` | 관광사진 변경분 동기화 | 보조 |

## 지역 필터 규칙 (#68)

지역 필터가 필요한 오퍼레이션은 **법정동 코드**(`lDongRegnCd`/`lDongSignguCd`)로만 호출한다.
legacy `areaCode`/`sigunguCode`는 사용하지 않는다.

| 지역 | `lDongRegnCd` | `lDongSignguCd` |
|---|---|---|
| 하동 (HADONG) | `48` | `850` |
| 영주 (YEONGJU) | `47` | `210` |
| 예천 (YECHEON) | `47` | `900` |

대상 오퍼레이션: `/areaBasedList2`, `/searchKeyword2`, `/searchFestival2`, `/areaBasedSyncList2`

이유 — KorService2는 신규·갱신 콘텐츠에 법정동 코드만 채운다. legacy 코드로 필터하면 콘텐츠가 30~50% 누락되고(부석사·소수서원 등),
`/searchFestival2`는 legacy 필터에서 **0건**을 반환한다. 법정동 결과는 legacy 결과의 완전한 상위집합이므로 두 코드를 병합 조회할 필요가 없다.

`/detailCommon2` 역매핑도 `lDongRegnCd`/`lDongSignguCd`를 쓴다 (`areacode`가 빈 문자열로 내려오는 콘텐츠가 다수).

## MVP 콘텐츠 타입 매핑

| contentTypeId | 관광 타입 | PickTrip 카테고리 | 사용 여부 |
|:---:|---|---|:---:|
| `12` | 관광지 | 자연, 문화·역사, 체험 | 사용 |
| `14` | 문화시설 | 문화·역사, 실내 대안 | 사용 |
| `15` | 축제/공연/행사 | 축제 및 행사 | 사용 |
| `25` | 여행코스 | 가족/맛/힐링코스 참고 | 선택 |
| `28` | 레포츠 | 체험, 액티비티 | 선택 |
| `38` | 쇼핑 | 시장 및 로컬 상권 | 사용 |
| `39` | 음식점 | 음식 | 사용 |
| `32` | 숙박 | — | MVP 제외 |

## 초기 수집 순서

```
1단계: 지역 코드 수집
  /ldongCode2 → 법정동 코드 저장 (지역 필터 기준)
  /areaCode2  → legacy 코드 seed (보조)
  → regions 테이블에 HADONG/YEONGJU/YECHEON 매핑

2단계: 분류 코드 수집
  /categoryCode2 → 관광 타입 코드
  /lclsSystmCode2 → 신분류체계 1/2/3Depth
  → PickTrip 내부 카테고리와 매핑

3단계: 콘텐츠 수집
  /areaBasedList2 → contentTypeId 12,14,15,25,28,38,39 필터링
  → 각 콘텐츠별 /detailCommon2, /detailIntro2, /detailInfo2, /detailImage2 순차 호출
  → 이미지 부족 시 /gallerySearchList1 보조 호출

4단계: 축제 수집
  /searchFestival2 → 여행 날짜 기반 호출
  → eventStartDate/eventEndDate를 AI 일정 생성 제약 조건으로 저장

5단계 이후: 이미지 보강 → 동기화 배치
```

## 동기화 전략

**국문 관광정보:**
- `/areaBasedSyncList2`로 변경 콘텐츠 감지
- 수정된 콘텐츠는 `contentid` 기준으로 상세 API 재호출
- 삭제/비표시 콘텐츠는 `dataStatus`를 `INACTIVE` 또는 `DELETED`로 변경
- 운영시간, 휴무일, 축제 기간은 `dataVerifiedAt`을 별도 관리

**관광사진:**
- `galUseFlag=1`인 사진만 사용
- `title`, `regionName` 기반으로 콘텐츠와 매칭

## 데이터 갱신 주기 (Spring @Scheduled)

| 데이터 유형 | 갱신 주기 |
|----------|---------|
| 기본 정보 (장소명, 위치, 주차 등) | 주 1회 (매주 월요일 새벽) |
| 운영시간, 휴무일 | 일 1회 (매일 자정) |
| 축제, 이벤트 | 일 1회 (축제 시작 1주 전부터 종료일까지) |

- 스케줄러는 `global/scheduler` 패키지에 위치
- 갱신 실패 시 기존 저장 데이터를 유지하고 실패 원인을 로그에 기록
- 운영 환경에서 `@EnableScheduling` 명시적 활성화 필요

## TourAPI 필드 → 내부 도메인 매핑

| 내부 필드 | TourAPI 필드 | API |
|---------|------------|-----|
| `sourceContentId` | `contentid` | areaBasedList2 |
| `title` | `title` | areaBasedList2 |
| `latitude` | `mapy` | areaBasedList2 |
| `longitude` | `mapx` | areaBasedList2 |
| `summary` | `overview` | detailCommon2 |
| `address` | `addr1`, `addr2` | detailCommon2 |
| `useTime` | (타입별) | detailIntro2 |
| `restDate` | (타입별) | detailIntro2 |
| `parking` | (타입별) | detailIntro2 |
| `firstImage` | `firstimage` | areaBasedList2 |
| `dataStatus` | `dataStatus` | areaBasedSyncList2 |
