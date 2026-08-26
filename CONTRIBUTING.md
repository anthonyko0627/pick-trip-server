# 기여 가이드 (Contributing Guide)

PickTrip Server에 기여해주셔서 감사합니다!
이 문서는 프로젝트에 처음 참여하는 팀원을 위해 작성되었습니다.
개발 환경 설정부터 PR 제출까지 단계별로 안내합니다.

---

## 목차

1. [프로젝트 기술 스택](#1-프로젝트-기술-스택)
2. [개발 환경 설정](#2-개발-환경-설정)
3. [디렉토리 구조](#3-디렉토리-구조)
4. [브랜치 전략](#4-브랜치-전략)
5. [커밋 메시지 규칙](#5-커밋-메시지-규칙)
6. [코드 스타일](#6-코드-스타일)
7. [Pull Request 가이드](#7-pull-request-가이드)
8. [이슈 작성 가이드](#8-이슈-작성-가이드)

---

## 1. 프로젝트 기술 스택

| 분류       | 기술                          |
|----------|-------------------------------|
| 언어       | Java 21                       |
| 프레임워크  | Spring Boot 4.0.6             |
| 웹        | Spring MVC                    |
| 영속성     | Spring Data JPA / Hibernate   |
| DB       | MySQL                         |
| 빌드      | Gradle Wrapper                |
| 코드 생성  | Lombok                        |
| 테스트     | JUnit 5 / Spring Boot Test    |
| 개발 DB 환경 | Docker Compose (`compose.yaml`) |

---

## 2. 개발 환경 설정

### 2-1. 필수 도구 설치

| 도구          | 설치 방법                                                            |
|-------------|----------------------------------------------------------------------|
| JDK 21      | [adoptium.net](https://adoptium.net) 또는 시스템 패키지 매니저          |
| Docker      | [docker.com](https://www.docker.com/get-started) (Docker Desktop 권장) |
| Git         | [git-scm.com](https://git-scm.com)                                   |

설치 후 아래 명령어로 정상 설치되었는지 확인합니다.

```bash
java --version
docker --version
git --version
```

### 2-2. 저장소 포크 (Fork)

1. [https://github.com/CMU02/PickTrip](https://github.com/CMU02/PickTrip) 에 접속합니다.
2. 우측 상단의 **Fork** 버튼을 클릭하고, 본인의 GitHub 계정을 대상으로 선택합니다.

### 2-3. 로컬 환경 설정

포크한 저장소를 클론하고 `upstream` 리모트를 등록합니다.

```bash
# 1. 포크한 저장소 클론 (your-github-username을 본인 계정으로 변경)
git clone git@github.com:<your-github-username>/PickTrip.git
cd PickTrip

# 2. 리모트 확인 (origin만 보여야 합니다)
git remote show

# 3. 원본 저장소를 upstream으로 등록
git remote add upstream git@github.com:CMU02/PickTrip.git

# 4. 리모트 재확인 (origin과 upstream이 모두 보여야 합니다)
git remote show

# 5. 모든 브랜치 정보 가져오기
git fetch --all
```

### 2-4. 환경 변수 설정

`.env` 파일을 프로젝트 루트에 생성하고 로컬 secret을 주입합니다.

```bash
# .env 예시 (실제 값은 팀원에게 문의)
DB_URL=jdbc:mysql://localhost:3306/picktrip
DB_USERNAME=root
DB_PASSWORD=secret
KAKAO_CLIENT_ID=...
GOOGLE_CLIENT_ID=...
JWT_SECRET=...
```

> `.env` 파일은 절대 Git에 커밋하지 않습니다. `.gitignore`에 등록되어 있습니다.

### 2-5. 개발 DB 실행

Docker Compose로 로컬 MySQL을 실행합니다.

```bash
docker compose up -d
```

### 2-6. 코드 동기화

작업 중 `upstream`(원본 저장소)에 변경사항이 생기면 아래 절차로 동기화합니다.

```bash
# 1. 모든 리모트 브랜치 정보 가져오기
git fetch --all

# 2. main 브랜치로 이동 후 upstream 반영
git checkout main
git pull upstream main

# 3. 작업 브랜치로 돌아가기
git checkout feat/kakao-login

# 4. main 기준으로 리베이스
git rebase main
```

> **주의**: 이미 원격(origin)에 푸시한 브랜치는 리베이스하지 않습니다.
> 푸시 이후에는 `git rebase` 대신 `git merge main`을 사용하세요.

작업이 완료되어 PR이 머지된 후에는 브랜치를 정리합니다.

```bash
# 로컬 브랜치 삭제
git branch -d feat/kakao-login

# 원격(origin) 브랜치 삭제
git push origin :feat/kakao-login
```

### 2-7. 애플리케이션 실행

```bash
# Windows
.\gradlew.bat bootRun

# macOS / Linux
./gradlew bootRun
```

### 2-8. 주요 Gradle 태스크

```bash
# 테스트 실행
./gradlew test

# 빌드
./gradlew build

# 특정 테스트 클래스만 실행
./gradlew test --tests "travel_agency.pick_trip.domain.auth.*"
```

---

## 3. 디렉토리 구조

```text
src/main/java/travel_agency/pick_trip
├── PickTripApplication.java
├── global/
│   ├── config/       # 전역 설정 (Security, Web, OpenFeign 등)
│   ├── error/        # 공통 예외 처리 및 에러 응답 모델
│   ├── security/     # JWT 유틸, 인증 필터
│   ├── filter/       # 서블릿 필터 (TraceId 등)
│   ├── scheduler/    # 배치성 스케줄러
│   └── util/         # 공통 유틸 함수
└── domain/
    ├── auth/         # OAuth 로그인, 토큰 발급·검증
    ├── user/         # 내부 사용자 계정 관리
    ├── region/       # 지역 코드 및 메타데이터
    ├── content/      # 지역 콘텐츠 저장·검색·필터
    ├── basket/       # 여행 바구니 및 우선순위 관리
    ├── itinerary/    # AI 일정 생성 연동, 저장, 수정
    └── share/        # 공유 토큰 생성 및 공개 일정 조회
```

각 도메인 패키지는 `controller`, `service`, `repository`, `entity`, `dto` 레이어로 구성됩니다.

새 파일을 추가할 때는 역할에 맞는 레이어와 도메인 디렉토리에 배치합니다.

---

## 4. 브랜치 전략

### 기본 규칙

- `main` 브랜치에는 **직접 커밋하지 않습니다.**
- 모든 작업은 새 브랜치를 생성하여 진행합니다.
- 작업이 완료되면 Pull Request를 통해 `main`에 병합합니다.
- **한 브랜치에서는 해당 기능과 관련된 작업만 진행합니다.**

### 브랜치 이름 형식

```
<타입>/<작업-내용>
```

| 타입        | 사용 시점                      | 예시                            |
|-----------|-------------------------------|-------------------------------|
| `feat`    | 새로운 기능 개발                 | `feat/kakao-login`            |
| `fix`     | 버그 수정                      | `fix/oauth-token-expiry`      |
| `refactor`| 기능 변경 없이 코드 구조 개선      | `refactor/itinerary-service`  |
| `docs`    | 문서 변경                      | `docs/spring-agent-rules`     |
| `chore`   | 빌드 설정, 패키지 관리 등 기타 작업 | `chore/mysql-compose`         |
| `hotfix`  | 배포된 버전의 긴급 버그 수정       | `hotfix/login-token-expire`   |

> `hotfix`는 `main`의 긴급 버그를 수정할 때 사용하며, 수정 후 `main`으로 즉시 머지합니다.

### 브랜치 생성 예시

```bash
# 1. main 브랜치 최신화
git checkout main
git pull origin main

# 2. 새 브랜치 생성 및 이동
git checkout -b feat/kakao-login
```

---

## 5. 커밋 메시지 규칙

### 기본 형식

```
<타입>(<범위, 선택>): <제목>

(선택) 본문 — 변경 이유와 주요 내용

(선택) 푸터 — 관련 이슈 번호 (예: Closes #12)
```

### 타입 종류

| 타입        | 설명                               |
|-----------|------------------------------------|
| `feat`    | 새로운 기능 추가                     |
| `fix`     | 버그 수정                           |
| `docs`    | 문서 변경 (README, 주석 등)          |
| `style`   | 코드 포맷 변경 (기능 변경 없음)        |
| `refactor`| 기능 변경 없이 코드 구조 개선          |
| `test`    | 테스트 추가 또는 수정                  |
| `chore`   | 빌드 설정, 패키지 관리 등 기타 작업     |
| `perf`    | 성능 개선                           |
| `ci`      | CI 설정 및 스크립트 변경              |
| `release` | 버전 릴리즈 및 태그                   |

### 작성 규칙

- 제목은 **마침표 없이** 작성합니다.
- 커밋 메시지는 **한국어**로 작성합니다.
- 하나의 커밋은 하나의 논리적 단위만 포함합니다.
- 민감한 파일(`.env`, 인증 키 등)은 절대 커밋하지 않습니다.
- DB 스키마 변경, API 변경, 테스트 변경은 커밋 본문에 이유를 적습니다.

### 커밋 절차

```bash
# 1. 변경사항 확인
git status
git diff

# 2. 관련 파일만 스테이징
git add <파일명>

# 3. 커밋
git commit -m "feat(auth): 카카오 소셜 로그인 구현"

# 4. 완료 확인
git log --oneline -10
git status
```

### 예시

```
feat(auth): 카카오 소셜 로그인 API 추가

- OAuth 리다이렉트 처리 추가
- 외부 사용자 ID와 내부 사용자 매핑 로직 구현
- 액세스 토큰 및 리프레시 토큰 발급 처리

Closes #5
```

---

## 6. 코드 스타일

전체 코드 컨벤션은 `.agents/rules/code-convention.md`를 참고하세요.
아래는 핵심 규칙만 요약합니다.

### 네이밍 규칙

| 항목        | 규칙              | 예시                                  |
|-----------|-----------------|-------------------------------------|
| 클래스       | PascalCase      | `ContentController`, `BasketService` |
| 메서드 / 변수  | camelCase       | `findContents`, `isActive`          |
| 전역 상수     | UPPER_SNAKE_CASE | `DEFAULT_PAGE_SIZE`, `MAX_RETRY`   |
| 패키지       | 소문자 또는 단어 구분 언더스코어 | `travel_agency.pick_trip`   |

### 클래스 접미사

| 레이어         | 접미사          | 예시                  |
|-------------|--------------|---------------------|
| Controller  | `*Controller` | `ContentController` |
| Service     | `*Service`    | `ItineraryService`  |
| Repository  | `*Repository` | `BasketRepository`  |
| Entity      | 도메인명 단수형    | `User`, `Itinerary` |
| Request DTO | `*Request`    | `CreateBasketRequest` |
| Response DTO | `*Response`  | `ContentDetailResponse` |
| Exception   | `*Exception`  | `ContentNotFoundException` |

### Java 규칙

```java
// Entity: @Setter 금지, NoArgsConstructor protected
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Itinerary { ... }

// 조회 전용 서비스 메서드
@Transactional(readOnly = true)
public ContentDetailResponse findContent(Long id) { ... }

// 상태 변경 서비스 메서드
@Transactional
public void createItinerary(CreateItineraryRequest request) { ... }

// DTO는 record 우선 고려
public record ContentDetailResponse(Long id, String name, String region) { }
```

### 계층 규칙

- `Controller`는 `Entity`를 직접 반환하지 않습니다. API 응답은 반드시 DTO로 변환합니다.
- `Controller`에는 트랜잭션을 두지 않습니다.
- 외부 API 호출과 DB 트랜잭션을 같은 범위에 오래 묶지 않습니다.

---

## 7. Pull Request 가이드

### PR 제출 전 체크리스트

- [ ] `./gradlew test` 실행 후 모든 테스트 통과
- [ ] 변경 사항이 현재 브랜치의 기능 범위 안에 있음
- [ ] `main` 브랜치의 최신 변경사항을 반영함 (`git rebase main`)
- [ ] 민감한 파일(`.env`, secret 등)이 포함되지 않음
- [ ] PR 제목과 설명이 명확하게 작성됨

### PR 제목 형식

```
<타입>: <변경 내용 요약>
```

예시:

- `feat: 카카오 소셜 로그인 API 추가`
- `fix: OAuth 토큰 만료 처리 오류 수정`
- `refactor: 일정 서비스 레이어 구조 개선`

### PR 본문 템플릿

```
## <PR 제목>
- [기능/버그/개선/환경...] 에 대한 한 줄 요약

---

## 변경 목적
왜 이 변경이 필요한지 한두 문장으로 작성해 주세요.
관련 이슈 번호가 있다면 함께 적어 주세요. (예: 이슈: #5)

---

## 변경 내용
주요 변경 파일과 내용을 간단히 적어 주세요.
- src/domain/auth/controller/AuthController.java: 카카오 로그인 엔드포인트 추가
- src/domain/auth/service/AuthService.java: OAuth 토큰 처리 로직 추가

---

## API 변경 여부
- [ ] API 추가 / 변경 있음 (엔드포인트와 변경 내용 명시)
- [ ] DB 스키마 변경 있음 (변경 내용 명시)
- [ ] 해당 없음

---

## 테스트 및 검증 방법
실제로 어떻게 테스트했는지 적어 주세요.
- 로컬에서 카카오 로그인 흐름 테스트: 정상 토큰 발급 확인
- AuthServiceTest 단위 테스트 통과 확인

---

## 리뷰 요청 포인트
리뷰어에게 특히 주의해서 봐달라고 하고 싶은 부분을 적어 주세요.
- AuthService의 토큰 만료 처리 로직이 안전한지
```

### 리뷰 과정

1. PR 생성 후 팀원에게 리뷰를 요청합니다.
2. 리뷰어는 코드 스타일뿐 아니라 로직, 성능, 유지보수성도 함께 검토합니다.
3. 코멘트는 구체적으로 작성하고 감정적인 표현은 지양합니다.
4. 모든 코멘트가 해결되면 `main`으로 **Merge Commit** 합니다.

### 병합 기준

- 관련 테스트가 모두 통과해야 합니다.
- 모든 리뷰 코멘트가 해결되어야 합니다.
- 기능 검증이 필요한 변경은 검증 방법을 PR에 포함합니다.
- 머지 후 관련 이슈를 닫고 작업 브랜치를 정리합니다.

---

## 8. 이슈 작성 가이드

버그를 발견하거나 새 기능을 제안할 때 이슈를 작성합니다.
새 이슈를 작성하기 전에 **기존 이슈를 먼저 검색**해주세요.

### 버그 리포트

```
## 버그 설명
어떤 문제가 발생했나요?

## 재현 방법
1. ...
2. ...

## 예상 동작
어떻게 동작해야 하나요?

## 실제 동작
실제로는 어떻게 동작했나요?

## 환경
- OS: (예: macOS 14, Windows 11)
- Java: (예: Java 21.0.3)
- Spring Boot: (예: 4.0.6)
```

### 기능 제안

```
## 기능 설명
어떤 기능을 원하나요?

## 필요한 이유
왜 이 기능이 필요한가요?

## 구현 아이디어 (선택)
어떻게 구현하면 좋을지 아이디어가 있다면 작성해주세요.
```

---

궁금한 점이 있으면 언제든지 팀 채널에 질문해주세요!
