# TourAPI 수집·동기화 설계

이슈 #7 (수집 2·3단계) 기준 설계 문서. 이후 이슈 B(4·5단계)·C(6·7단계)의 기준이 된다.

## 1. 핵심 방침

- **실시간 TourAPI 조회(read-through)를 주 경로로 유지**한다. 현재 `ContentService` → `TourApiContentAdapter` 실시간 흐름은 변경하지 않는다.
- **수집·적재는 보조(캐시·성능·안정성 보강)**로만 둔다. 콘텐츠 제공을 DB 기반으로 전면 전환하지 않는다.
- 동기: 일정 생성(`ItineraryService`)이 바구니 항목마다 상세 API를 실시간 호출 → 지연·rate limit 위험. 수집·적재로 이 부분을 보조 캐시화하는 것이 우선 가치.
- 개발 계정 트래픽은 **일 1,000건 기준** → 배치·캐시를 전제로 설계한다.

## 2. 단계별 범위와 이슈 분할

| 단계 | 내용 | 이슈 |
|------|------|------|
| 1 | API 클라이언트 공통 모듈 (`TourApiClient`/`FeignConfig`/`RequestInterceptor`) | 완료 |
| 2 | 지역 코드 seed — `/areaCode2`, `/ldongCode2` | **#7 (이슈 A)** |
| 3 | 분류 코드 seed — `/categoryCode2`, `/lclsSystmCode2`, 내부 매핑 | **#7 (이슈 A)** |
| 4 | 콘텐츠 수집 배치 — `/areaBasedList2` + 상세 API | 이슈 B |
| 5 | 축제 수집 배치 — `/searchFestival2` | 이슈 B |
| 6 | 이미지 보강 — `/detailImage2`, `gallerySearchList1` | 이슈 C |
| 7 | 동기화 배치 — `/areaBasedSyncList2`, `gallerySyncDetailList1` | 이슈 C |

의존성: `1 → 2,3 → 4 → 6 → 7` (5는 2 이후). 이슈 단위로는 **A → B → C 직렬**.

## 3. 데이터 모델 (이슈 A)

데이터 모델은 콘텐츠 엔티티 3종과 코드 테이블 4종으로 구성한다.
엔티티 컨벤션: `@Getter` + `@NoArgsConstructor(PROTECTED)` + `@Builder`(private), `@CreationTimestamp`/`@UpdateTimestamp`, `@Enumerated(STRING)`. **`@Setter`/`@Data` 금지.**

### 3.1 콘텐츠 엔티티 (`domain/content/entity`)

#### `TravelContent` (travel_contents)

`domain-model.md` 기준. **PK는 TourAPI `contentid`를 자연키로 사용**한다(`@Id String sourceContentId`).

| 필드 | 타입 | TourAPI 원천 |
|------|------|--------------|
| `sourceContentId` (PK) | String | `contentid` |
| `contentTypeId` | String | `contenttypeid` |
| `title` | String | `title` |
| `region` | `Region` (enum) | `lDongRegnCd`+`lDongSignguCd` 매핑 |
| `category` | String | `contentTypeId`+`lclsSystm*` 내부 매핑 |
| `summary` | String(TEXT) | `overview` (`/detailCommon2`) |
| `address` | String | `addr1`+`addr2` |
| `latitude` | Double | `mapy` |
| `longitude` | Double | `mapx` |
| `tel` | String | `tel` |
| `homepage` | String(TEXT) | `homepage` |
| `firstImage` | String | `firstimage` |
| `modifiedTime` | String | `modifiedtime` |
| `dataStatus` | `DataStatus` (enum) | `/areaBasedSyncList2` |

- 관계: `@OneToOne(mappedBy)` → `ContentDetail`, `@OneToMany(mappedBy)` → `ContentImage` (cascade ALL, orphanRemoval)
- `DataStatus` enum: `ACTIVE`, `INACTIVE`, `DELETED` (신규 적재 기본값 `ACTIVE`)
- `Region`은 기존 enum을 그대로 사용 (`@Enumerated(STRING)`)

#### `ContentDetail` (content_details)

`TravelContent`와 1:1. **PK 공유(`@MapsId`)** — `travel_contents.source_content_id`를 FK 겸 PK로 사용.

| 필드 | 원천 |
|------|------|
| `sourceContentId` (PK, FK) | `@MapsId` |
| `useTime` | `/detailIntro2` |
| `restDate` | `/detailIntro2` |
| `parking` | `/detailIntro2` |
| `chkBabyCarriage` | `/detailIntro2` |
| `chkPet` | `/detailIntro2` |
| `useFee` | `/detailIntro2`, `/detailInfo2` |
| `reservationRequired` (Boolean) | 자체 검수 |
| `indoorOutdoor` (`IndoorOutdoor` enum) | 자체 검수 + 분류체계 |
| `walkingLevel` (`WalkingLevel` enum) | 자체 검수 |
| `familyTags` (`@ElementCollection Set<String>`) | 자체 검수 |
| `dataVerifiedAt` (LocalDateTime) | 자체 |

> 자체 검수 필드(reservationRequired/indoorOutdoor/walkingLevel/familyTags/dataVerifiedAt)는 이슈 A에서 **컬럼만 정의**하고 채우지 않는다. 적재는 이슈 B 이후·관리자 입력으로 보완.

#### `ContentImage` (content_images)

`TravelContent`와 1:N. UUID PK.

| 필드 | 원천 |
|------|------|
| `id` (PK, UUID) | 자체 |
| `travelContent` (`@ManyToOne LAZY`) | 내부 FK |
| `source` (`ImageSource` enum: `TOUR_API`/`PHOTO_GALLERY`) | — |
| `imageUrl` | `originimgurl`, `galWebImageUrl` |
| `title` | `imgname`, `galTitle` |
| `copyrightType` | `cpyrhtDivCd` |
| `photographyMonth` | `galPhotographyMonth` |

### 3.2 코드 테이블 (`domain/region`, `domain/content`)

`/areaCode2`·`/ldongCode2`·`/categoryCode2`·`/lclsSystmCode2`를 호출해 코드 테이블에 적재한다.

| 엔티티 | 테이블 | TourAPI | 주요 컬럼 |
|--------|--------|---------|-----------|
| `AreaCode` | area_codes | `/areaCode2` | `code`, `name`, `parentCode`(시도=null, 시군구=시도코드) |
| `LdongCode` | ldong_codes | `/ldongCode2` | `regnCd`, `signguCd`, `name` |
| `CategoryCode` | category_codes | `/categoryCode2` | `code`, `name`, `parentCode`, `depth`(1/2/3) |
| `LclsSystmCode` | lcls_systm_codes | `/lclsSystmCode2` | `code`, `name`, `parentCode`, `depth`(1/2/3) |

- 위치: 지역 계열(`AreaCode`/`LdongCode`)은 `domain/region/entity`, 분류 계열(`CategoryCode`/`LclsSystmCode`)은 `domain/content/entity`.
- 각 코드 엔티티는 `code`(또는 복합키) 기준 upsert. 단순 reference data이므로 자동 생성/수정 타임스탬프만 둔다.
- **`Region` enum은 이슈 A에서 제거하지 않는다.** 코드 테이블은 additive. `Region`을 코드 테이블 기반으로 치환하는 작업은 후속(이슈 B 이후)으로 분리한다.

## 4. Seed 메커니즘 (이슈 A)

- `TourApiClient`에 Feign 메서드 4종 추가:
  `getAreaCode`, `getLdongCode`, `getCategoryCode`, `getLclsSystmCode`
- `CodeSeedService` (`domain/content` 또는 `global`):
  - 네 API를 호출해 코드 테이블을 조회·upsert
  - **TourAPI 호출은 반드시 try/catch로 감싸고**, 실패 시 기존 데이터를 유지하며 원인을 로그로 남긴다 (예외 없는 외부 API 호출 금지).
  - 트리거: 수동(프로파일 가드된 `ApplicationRunner` 또는 관리용 진입점). 운영 `@Scheduled` 자동화는 이슈 C 동기화에서 다룬다.
- 응답 매핑 DTO는 `domain/content/client/dto`의 기존 패턴(record)을 따른다.

## 5. 동기화 전략 (이슈 C, 참고)

- `/areaBasedSyncList2`로 변경 콘텐츠 감지 → `contentid` 기준 상세 API 재호출.
- 삭제/비표시 콘텐츠는 `dataStatus`를 `INACTIVE`/`DELETED`로 변경.
- 갱신 주기: 기본 정보 주 1회 / 운영시간·휴무 일 1회 / 축제 일 1회(시작 1주 전~종료일).
- 스케줄러는 `global/scheduler` 패키지, `@EnableScheduling`은 운영 프로파일에서 활성화.
- 관광사진은 `galUseFlag=1`만 사용.

## 6. 테스트 (이슈 A)

- 엔티티 매핑·양방향 관계(`TravelContent`↔`ContentDetail`↔`ContentImage`) 영속성 테스트.
- `CodeSeedService` 단위 테스트: `TourApiClient` mock으로 정상 적재 + 호출 실패 시 기존 데이터 유지 검증.
- 실시간 콘텐츠 조회 흐름 회귀 없음 확인 (이슈 A는 기존 adapter/service를 건드리지 않음).

## 7. 이슈 A 범위 밖 (명시)

- 실제 콘텐츠 적재 배치(`/areaBasedList2` + 상세 적재) → 이슈 B
- 축제 적재(`/searchFestival2`) → 이슈 B
- 이미지 보강·동기화 스케줄러 → 이슈 C
- `Region` enum의 코드 테이블 치환 → 후속
- 실시간 조회 흐름 변경 → 하지 않음
