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
