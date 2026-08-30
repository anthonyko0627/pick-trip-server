# 도메인 모델

이 문서는 PickTrip 서버의 핵심 Entity 관계와 주요 필드를 정의한다.
상세 필드와 제약조건은 Entity 클래스에서 확인한다.

## 관계도

```
Region 1──< TravelContent 1──1 ContentDetail
                    │
                    └──< ContentImage

User 1──< Basket ──< BasketItem >── TravelContent

User 1──< Itinerary 1──< ItineraryDay 1──< ItineraryItem >── TravelContent
              │
              └──1 ShareToken
```

## 테이블 정의

### `regions`

지역 코드 테이블. 내부 코드(`HADONG`, `YEONGJU`, `YECHEON`)와 TourAPI 코드를 매핑한다.

| 필드              | 원천               | 설명                   |
| ---------------- | ------------------ | ---------------------- |
| `id`             | 자체               | 내부 지역 ID           |
| `name`           | 자체               | 하동, 영주, 예천        |
| `lDongRegnCd`    | `/ldongCode2`      | 법정동 시도 코드 (지역 필터 기준) |
| `lDongSignguCd`  | `/ldongCode2`      | 법정동 시군구 코드 (지역 필터 기준) |

> legacy `areaCode`/`sigunguCode`는 KorService2에서 신규·갱신 콘텐츠에 채워지지 않아 사용하지 않는다. (#68)

---

### `travel_contents`

TourAPI 기반 콘텐츠 원천 데이터. `content_details`와 1:1 관계.

| 필드                | TourAPI 필드                          | 설명                          |
| ------------------ | ------------------------------------- | ----------------------------- |
| `sourceContentId`  | `contentid`                           | TourAPI 콘텐츠 ID             |
| `contentTypeId`    | `contenttypeid`                       | 관광 타입 ID                  |
| `title`            | `title`                               | 장소명/콘텐츠명               |
| `regionId`         | `lDongRegnCd`, `lDongSignguCd`        | 내부 지역 참조                |
| `category`         | `contentTypeId`, `lclsSystm*`         | PickTrip 내부 카테고리        |
| `summary`          | `overview` (`/detailCommon2`)         | 한 줄 소개                    |
| `address`          | `addr1`, `addr2`                      | 주소                          |
| `latitude`         | `mapy`                                | 위도                          |
| `longitude`        | `mapx`                                | 경도                          |
| `tel`              | `tel`                                 | 문의 전화                     |
| `homepage`         | `homepage`                            | 공식 페이지                   |
| `firstImage`       | `firstimage`, `/detailImage2`         | 대표 이미지                   |
| `modifiedTime`     | `modifiedtime`                        | TourAPI 원천 수정일           |
| `dataStatus`       | `/areaBasedSyncList2`                 | `ACTIVE`, `INACTIVE`, `DELETED` |

---

### `content_details`

`travel_contents`와 1:1 관계. TourAPI `/detailIntro2`, `/detailInfo2` 기반.
자체 검수가 필요한 필드는 별도 관리 테이블 또는 관리자 입력으로 보완한다.

| 필드                   | 원천                              | 설명                        |
| --------------------- | --------------------------------- | --------------------------- |
| `useTime`             | `/detailIntro2`                   | 이용시간/운영시간            |
| `restDate`            | `/detailIntro2`                   | 휴무일                      |
| `parking`             | `/detailIntro2`                   | 주차 가능 여부              |
| `chkBabyCarriage`     | `/detailIntro2`                   | 유모차 이동 가능성 참고      |
| `chkPet`              | `/detailIntro2`                   | 반려동물 동반 가능 여부      |
| `useFee`              | `/detailIntro2`, `/detailInfo2`   | 이용요금                    |
| `reservationRequired` | 자체 검수                         | 예약 필요 여부              |
| `indoorOutdoor`       | 자체 검수 + 분류체계              | 실내/실외                   |
| `walkingLevel`        | 자체 검수                         | 걷기 부담                   |
| `familyTags`          | 자체 검수                         | 아이/부모님/가족 적합도      |
| `dataVerifiedAt`      | 자체                              | 마지막 검증일               |

---

### `content_images`

콘텐츠 이미지. TourAPI `/detailImage2` 우선, 부족 시 `관광사진 정보_GW` 보조.

| 필드                    | 원천                                         | 설명              |
| ---------------------- | -------------------------------------------- | ----------------- |
| `contentId`            | 내부                                         | 연결 콘텐츠       |
| `source`               | `TOUR_API` 또는 `PHOTO_GALLERY`              | 이미지 출처       |
| `imageUrl`             | `originimgurl`, `galWebImageUrl`             | 이미지 URL        |
| `title`                | `imgname`, `galTitle`                        | 이미지 제목       |
| `copyrightType`        | `cpyrhtDivCd`                                | 저작권 유형       |
| `photographyMonth`     | `galPhotographyMonth`                        | 촬영월 (관광사진) |

---

### `baskets` / `basket_items`

사용자의 여행 바구니. 바구니와 콘텐츠는 N:M 관계이며 `basket_items`가 중간 테이블.

| 필드            | 설명                                          |
| -------------- | --------------------------------------------- |
| `userId`       | 소유자                                        |
| `region`       | 선택 지역 (`HADONG`, `YEONGJU`, `YECHEON`)    |
| `travelDate`   | 여행 시작 날짜                                |
| `duration`     | 여행 기간 (일수)                              |
| `companions`   | 동행 조건 (enum, 복수 선택 가능)              |

`basket_items` 추가 필드:

| 필드           | 설명                                              |
| ------------- | ------------------------------------------------- |
| `contentId`   | 담은 콘텐츠                                       |
| `priority`    | `MUST_VISIT`, `PREFERRED`, `OPTIONAL`             |

---

### `itineraries` / `itinerary_days` / `itinerary_items`

저장된 여행 일정. 일정 → 일차 → 항목 계층 구조.

`itineraries` 주요 필드:

| 필드              | 설명                          |
| ---------------- | ----------------------------- |
| `userId`         | 소유자                        |
| `title`          | 일정 제목                     |
| `region`         | 지역                          |
| `travelDate`     | 여행 시작 날짜                |
| `duration`       | 기간                          |
| `lastModifiedAt` | 마지막 수정 시각              |

`itinerary_items` 추가 필드:

| 필드            | 설명                     |
| -------------- | ------------------------ |
| `contentId`    | 배치된 콘텐츠             |
| `dayIndex`     | 몇째 날                  |
| `order`        | 해당 일차 내 순서         |
| `reason`       | AI 배치 이유             |
| `isPinned`     | 고정 여부                |

---

### `share_tokens`

일정 공유 링크 관리.

| 필드            | 설명                                          |
| -------------- | --------------------------------------------- |
| `itineraryId`  | 공유 대상 일정                                |
| `token`        | 예측 불가능한 공유 토큰 (UUID 또는 동등 수준) |
| `isActive`     | 링크 활성 여부                                |
| `createdAt`    | 생성 시각                                     |
