# PickTrip Server

# Introduce

PickTrip 서버는 경상도 소도시(하동, 영주, 예천) 여행 일정 생성 서비스의 백엔드를 담당한다.
사용자가 선택한 지역 콘텐츠와 여행 조건을 AI에 전달해 현실적인 일정을 생성하고, 이를 저장·공유할 수 있는 API를 제공한다.

비로그인 사용자도 콘텐츠를 탐색할 수 있으며, 일정 저장·공유는 소셜 로그인(Kakao, Google) 기반 인증 사용자에게만 제공한다.

# Background of the Project

국내 여행자는 유명 관광지에 집중되는 경향이 있다. 하동, 영주, 예천처럼 고유한 음식·자연·문화·축제 자원을 가진 지역은 정보가 흩어져 있어 매력을 한눈에 파악하기 어렵다.

기존 지도 서비스나 블로그는 개별 장소 정보를 제공하지만, 사용자 선택을 실제 일정으로 연결하는 데 한계가 있다.
이 서버는 지역 콘텐츠 탐색과 AI 일정 생성을 하나의 흐름으로 연결해 이 문제를 해결한다.

# Tech Stack

| 분류       | 기술                              |
|----------|---------------------------------|
| 언어       | Java 21                         |
| 프레임워크    | Spring Boot 4.0.6               |
| 웹        | Spring MVC                      |
| 영속성      | Spring Data JPA / Hibernate     |
| DB       | MySQL                           |
| 빌드       | Gradle Wrapper                  |
| 코드 생성    | Lombok                          |
| 테스트      | JUnit 5 / Spring Boot Test      |
| 개발 DB 환경 | Docker Compose (`compose.yaml`) |

# Project Information

| title                 | path                                                          |
|-----------------------|---------------------------------------------------------------|
| 핵심 기능               | `.agents/docs/key-features.md`                                |
| 주요 사용 흐름            | `.agents/docs/key-usage-flow.md`                              |
| MVP 범위              | `.agents/docs/mvp-scope.md`                                   |
| API 엔드포인트 초안        | `.agents/docs/api-endpoints.md`                               |
| 도메인 모델              | `.agents/docs/domain-model.md`                                |
| 지역별 콘텐츠 방향          | `.agents/docs/content-direction-by-region.md`                 |
| Gradle 사용 가이드       | `.agents/docs/package-manager-guide.md`                       |
| 고도 데이터 소스 선정      | `.agents/docs/elevation-source.md`                            |

# Convention

| title      | path                               |
|------------|------------------------------------|
| 코드 규칙      | `.agents/rules/code-convention.md` |
| 테스트 규칙     | `.agents/rules/test-convention.md` |
| TraceId 규칙 | `.agents/rules/trace-id.md`        |
| 깃 규칙       | `.agents/rules/git-convention.md`  |
| 브랜치 포커스    | `.agents/rules/branch-focus.md`    |

# Skills

| title              | path                                                      |
|--------------------|-----------------------------------------------------------|
| 예외 처리 패턴          | `.agents/skills/picktrip-error-handling/SKILL.md`         |
| 보안 체크리스트          | `.agents/skills/picktrip-security-checklist/SKILL.md`     |
| AI 일정 생성 연동        | `.agents/skills/picktrip-ai-integration/SKILL.md`         |
| TourAPI 수집·동기화     | `.agents/skills/picktrip-tour-api-sync/SKILL.md`          |
| Notion API 문서 업데이트  | `.agents/skills/picktrip-notion-api-docs/SKILL.md`        |

# GitHub Workflow

## 이슈 생성

사용자가 이슈 생성을 요청하면, `.github/ISSUE_TEMPLATE/` 의 템플릿을 확인하고 작업 성격에 맞는 템플릿을 골라 그 형식(frontmatter의 `title` 접두사·`labels` 포함)에 맞춰 이슈를 생성한다.

| 템플릿                   | 용도                              | title 접두사     | label     |
|-----------------------|---------------------------------|---------------|-----------|
| `bug_report.md`       | 버그 제보                           | `[Bug] `      | `bug`     |
| `feature_request.md`  | 새로운 기능 제안                       | `[Feature] `  | `feature` |
| `task.md`             | 구현·개선·조사 등 구체적 작업               | `[Task] `     | `task`    |
| `chore.md`            | 설정·의존성·문서 정리 등 유지보수            | `[Chore] `    | `chore`   |

- 어떤 템플릿이 적절한지 모호하면 사용자에게 확인한다.
- 본문은 선택한 템플릿의 섹션 구조(예: 버그의 `재현 방법`, 작업의 `완료 조건`)를 그대로 따른다.

## PR 생성

사용자가 PR 생성을 요청하면, `.github/pull_request_template.md` 를 확인하고 해당 템플릿 형식(`작업 내용`, `관련 이슈`, `테스트 플랜` 섹션)에 맞춰 PR을 작성한다.

- 관련 이슈가 있으면 `관련 이슈` 섹션에 `Closes #<번호>` 로 연결한다.

# Quick Start

## 로컬 실행 (Windows)

```powershell
# 개발 DB 포함 실행 (Docker Compose 자동 연동)
.\gradlew.bat bootRun

# 테스트 실행
.\gradlew.bat test

# 빌드
.\gradlew.bat build
```

## 로컬 실행 (macOS / Linux)

```bash
./gradlew bootRun
./gradlew test
./gradlew build
```

## 설정

- 로컬 개발: `application.yaml` + `application-dev.yaml`
- 운영: `application-prod.yaml` (secret 값은 환경 변수 또는 secret manager에서 주입)
- `.env` 파일로 로컬 secret을 주입할 수 있다 (`spring.config.import` 설정 참고)

# Package Structure

```text
src/main/java/travel_agency/pick_trip
├── PickTripApplication.java
├── global
│   ├── config
│   ├── error
│   ├── security
│   └── util
└── domain
    ├── auth
    ├── user
    ├── region
    ├── content
    ├── basket
    ├── itinerary
    └── share
```

# Key Domain Overview

| 도메인         | 책임                                       |
|-------------|------------------------------------------|
| `auth`      | Kakao / Google OAuth, 토큰 발급·검증           |
| `user`      | 내부 사용자 계정 관리                             |
| `region`    | 지역 코드 및 메타데이터 (HADONG, YEONGJU, YECHEON) |
| `content`   | 지역 콘텐츠 저장·검색·필터                          |
| `basket`    | 여행 바구니 및 우선순위 관리                         |
| `itinerary` | AI 일정 생성 연동, 저장, 수정                      |
| `share`     | 공유 토큰 생성 및 공개 일정 조회                      |

# Error Response Contract

모든 API 예외는 아래 형태로 응답한다.

```json
{
  "code": "DOMAIN_ERROR_TYPE",
  "message": "사용자에게 표시할 한국어 메시지",
  "traceId": "request-trace-id"
}
```

에러 코드 목록과 HTTP 상태 매핑은 `.agents/skills/picktrip-error-handling/SKILL.md`를 참고한다.

# Known Gotchas

## Java / Spring

- Spring Boot 4.x 기준이므로 `javax.*` 대신 반드시 `jakarta.*`를 사용한다.
  - 예: `javax.persistence.*` → `jakarta.persistence.*`
  - 예: `javax.validation.*` → `jakarta.validation.*`
- `@SpringBootTest`는 전체 컨텍스트를 로드하므로 단순 유닛 테스트에 남용하지 않는다.
- Mockito 사용 시 `@Mock` + `@InjectMocks` 조합을 사용한다. `@MockBean`은 통합 테스트에서만 사용한다.

## JPA / DB

- `FetchType.EAGER`는 N+1 문제를 유발한다. 기본은 `LAZY`이며, 필요 시 fetch join으로 해결한다.
- 개발 환경 Docker Compose DB 포트는 `3306`이다.
- `ddl-auto` 설정은 `validate` 또는 `none`만 사용한다. `create`, `create-drop`은 운영 데이터를 삭제한다.

## Lombok

- `@Data`는 `@EqualsAndHashCode`, `@ToString`을 포함해 Entity에서 순환 참조를 일으킨다. 절대 사용하지 않는다.
- DTO는 `record`를 우선 검토한다. 단, `record`는 상속이 불가하므로 계층 구조가 필요하면 `@Value` 또는 일반 클래스를 사용한다.

## 보안

- JWT 토큰, OAuth Client Secret, DB 비밀번호는 `.env` 파일로 관리하며 Git에 커밋하지 않는다.
- API 응답에서 서버 내부 예외 메시지(`exception.getMessage()`)를 그대로 노출하지 않는다.

# Decision Log

| 결정 | 이유 |
|------|------|
| OpenFeign 사용 | TourAPI·AI 외부 호출을 선언형으로 단순화하기 위해 선택. `RestTemplate`은 보일러플레이트가 많고, `WebClient`는 리액티브 전환 비용이 크다. |
| DTO에 `record` 우선 | 불변성 보장 + Lombok `@Value` 의존 제거. Java 21 환경이므로 언어 기본 기능 활용. |
| MySQL 선택 | 팀 친숙도와 Docker Compose 로컬 환경 설정 단순화. 운영 이관 시 RDS MySQL 사용 예정. |
| 소셜 로그인만 지원 | MVP 범위에서 자체 회원가입·비밀번호 관리 복잡도를 제거. Kakao·Google OAuth로 인증 위임. |
| Spring MVC (동기) 유지 | 팀 학습 비용과 TourAPI 동기 호출 특성 고려. 리액티브 전환은 MVP 이후 검토. |
| 근처 콘텐츠 도로 거리에 Kakao Mobility 여러 목적지 길찾기 | 기준점→주변 N점을 1콜로 조회. Tmap은 1:1·별도 가입. REST 키는 기존 Kakao 앱 재사용. |
| 길찾기 실패 시 직선 거리 폴백(예외 아님) + 재시도 없음 | 근처 탐색은 보조 기능이라 완전 실패보다 근사 정렬이 낫고, 사용량을 아낀다. |
| 일정 생성 요청 바디는 선택(`required = false`) | 바디 없이 호출하던 기존 클라이언트를 깨지 않으면서 옵션을 늘리기 위함. 누락 필드는 record 컴팩트 생성자에서 기존 동작으로 정규화한다. |
| `AUGMENT` 모드는 지역 콘텐츠 후보를 프롬프트에 실어 보냄 | AI 는 TourAPI contentId 를 알 방법이 없어 후보를 주지 않으면 지어낸 id 만 뱉고 서버 화이트리스트에서 전부 걸러진다. 후보를 줘야 추가 제안이 실제로 동작한다. |
| 일차 배분·순서를 서버가 NN + 2-opt 로 항상 최적화 | AI 배분은 초기값으로만 쓰고, 시작 지점(`startContentId`) 지정 여부와 무관하게 서버가 nearest-neighbor 로 전체 장소를 한 줄로 꿴 뒤 일수로 분할한다(시작 지점은 앵커 고정 여부만 결정). k-means 는 군집 중심만 볼 뿐 "이동 순서"를 만들지 못해 일차 간 연결이 끊기고, 초기값·k 선택 등 손댈 곳이 늘어난다. 하루 순서는 7개 이하면 기존 전순열(전역 최적), 8개 이상은 NN 초기해 + 2-opt 로 근사한다. |
| 회원 탈퇴는 소프트 삭제 + 30일 후 Spring Batch 하드 삭제 | 실수 탈퇴 복구 여지를 두고, 소유 데이터가 FK 없이 user_id 로만 연결돼 앱이 순서대로 지워야 한다. Batch 는 삭제 이력을 BATCH_* 테이블에 남긴다. |
| 이동수단별 일정안은 기존 최상위 필드 유지 + `variants` 래핑 | 응답을 variant 배열로 바꾸면 기존 클라이언트가 전부 깨진다. `variants` 를 추가하고 최상위 `title`·`days`·`adjustments` 는 `variants[0]` 의 복제로 남겨 하위호환을 지킨다. `travelModes` 미지정이면 자동차 단일안이라 기존 응답과 내용이 같다. `suggestions`(혼잡 제안)는 첫 안 기준이라 최상위에 그대로 둔다. |
| `CAR` 은 Kakao 도로 행렬(origin 당 1콜), `TRANSIT` 은 도보/버스 근사 | 여러 목적지 길찾기는 1 origin → 최대 30 목적지를 1콜로 주므로 장소 s개의 전체 행렬을 s콜로 얻는다(인접 구간만 뽑는 것과 콜 수가 비슷하면서 순서 최적화에도 쓴다). `radius` 상한이 10km 라 그보다 먼 구간은 응답에서 빠지며, 그 구간은 실측 구간에서 관측한 평균 속도로 직선거리 폴백한다(실패해도 예외 없이, 재시도 없이). 대중교통은 노선·배차 데이터가 없어 도보 2km 경계로 3.5km/h · 25km/h + 대기 15분 근사를 쓰고, 자동차 도로 행렬은 조회하지 않는다. |
| 교통비는 상수 기반 근사, 산출 불가는 `null`+사유 코드 | 지역 시내버스 요금 공개 API 가 없고 통행료·주차비 데이터도 없어, 유가·기본요금 상수(`itinerary.cost` 설정값)로 근사한다. 값을 못 내는 지표를 응답에서 빼면 스플릿 뷰 열이 어긋나므로, 키는 유지하고 값만 `null` 로 두면서 `unavailableReasons` 에 사유 코드를 담는다. |
| 고도 조회는 OpenTopoData `srtm30m` | 이슈 후보였던 VWorld 는 좌표→고도 REST API 자체가 없고(3D DEM API 는 2019년 종료), Open-Elevation 은 데이터 없는 좌표를 0m 로 내려 고도 미상과 구분되지 않는다. OpenTopoData 는 키 없이 좌표 100개를 1콜로 조회하고 범위 밖은 null 이라 폴백 판정이 명확하다. 부하가 커지면 자체 호스팅으로 base-url 만 바꾼다. |
| 휴식 스톱은 순서 확정 후 삽입 + 근처 카페 없으면 미삽입 | 순서 탐색(전순열·2-opt) 중에 넣으면 후보마다 스톱 수가 달라져 평가가 흔들린다. 삽입 지점 계산은 순수 코드(`RestBreaks`)가 하고 실제 카페 조회는 서비스가 맡아 스케줄링 패키지에 Spring 의존이 새지 않게 한다. 저장·수정이 `contentId` 를 요구하므로 가짜 스톱 대신 실제 콘텐츠만 넣고, 후보가 없으면 예외 없이 건너뛴다. |
| 고도 조회 실패는 예외 아님(고도 미상 폴백) + 재시도 없음 | 경사 페널티는 도보 일정의 보조 정보라 조회 실패로 일정 생성을 막을 이유가 없다. 고도 미상 구간은 상승고도 0·페널티 0 이 되어 기존 일정과 같아진다. |
| 관광객수·혼잡도는 지역 통계 + 자체 프록시 근사 | 개별 장소 단위 관광객수를 주는 공개 데이터가 없어, 지역별 방문자수(공공데이터포털 15101972)와 바구니 담긴 횟수를 조합해 근사하고 응답에 `approximate=true` 로 표시한다. 수집은 Batch 없이 스케줄러가 서비스 메서드를 호출한다. |
| 방문자수는 월 단위 전국 응답 1회 호출을 페이지 순회해 세 지역을 함께 적재 | `locgoRegnVisitrDDList` 는 지역 필터 파라미터가 없어(관광빅데이터 매뉴얼 v4.1) 전국 시군구가 한꺼번에 내려온다. 지역별로 부르면 같은 전국 응답을 3번 받는 셈이라 월마다 1회만 받아 `signguCode` 로 거른다. 페이지 중간 실패는 과소 집계를 막기 위해 그 달 전체를 버린다(예외 없음, 재시도 없음). |

# AI Constraints

다음 행동은 사용자가 명시적으로 요청하더라도 진행 전에 반드시 경고하고 확인을 받는다.

## 절대 금지 (코드)

- Entity 클래스에 `@Setter`, `@Data` 어노테이션 추가
- Controller 메서드에서 Entity를 직접 반환 (`return entity`)
- Controller 레이어에 `@Transactional` 추가
- `application-prod.yaml`에 `ddl-auto: create` 또는 `ddl-auto: create-drop` 설정
- 예외 처리 없이 외부 API(TourAPI, AI, OAuth) 호출 코드 작성

## 절대 금지 (Git / 파일)

- `.env` 파일, 토큰, 비밀번호가 포함된 파일을 Git에 커밋
- 명시적 지시 없이 `main` 브랜치에 직접 커밋
- 명시적 지시 없이 `git push` 실행

## 확인 후 진행 (파괴적 작업)

- DB 스키마 변경 (컬럼 삭제, 타입 변경)
- 기존 API 응답 구조 변경 (하위 호환성 파괴)
- 패키지 구조 대규모 이동·리팩터링
