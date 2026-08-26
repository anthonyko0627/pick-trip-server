# Git Commit Message Naming Convention

> 명시적으로 요청받기 전까지 Git commit을 만들지 않는다.

## Commit Type

- `feat`: 새로운 기능 추가
- `fix`: 버그 수정
- `docs`: 문서 변경
- `style`: 동작과 무관한 포맷 변경
- `refactor`: 동작 변경 없는 구조 개선
- `test`: 테스트 추가 또는 수정
- `chore`: 빌드, 설정, 기타 작업
- `perf`: 성능 개선
- `ci`: CI 설정 변경
- `release`: 릴리스 작업

## Commit Message Format

```text
<type>(optional-scope): <title>

<body>

<footer>
```

커밋 제목은 기본적으로 한국어로 작성한다.

## 예시

```text
feat(auth): 카카오 OAuth 로그인 API 추가
fix(content): 지역 코드 검증 누락 수정
docs: Spring 서버 문서 재정리
test(itinerary): 일정 생성 입력 검증 테스트 추가
chore: MySQL Docker Compose 설정 추가
```

## Commit By Logical Units

- 서로 다른 목적의 변경은 별도 커밋으로 나눈다.
- 문서 변경과 코드 변경은 가능하면 분리한다.
- DB 스키마 변경, API 변경, 테스트 변경은 커밋 메시지 본문에 이유를 적는다.
- 민감 파일은 커밋하지 않는다.

## Branch Naming Convention

- 브랜치는 `<type>/<issue_number>` 형식으로 만든다.
  - `type`은 위 [Commit Type](#commit-type)을 따른다 (`feat`, `fix`, `refactor`, `docs`, `chore`, `hotfix` 등).
  - `issue_number`는 해당 작업과 연관된 GitHub 이슈 번호다.
- 예시
  - 기능 추가: `feat/42`
  - 버그 수정: `fix/57`
  - 리팩터링: `refactor/13`
  - 문서 변경: `docs/9`
  - 환경 작업: `chore/24`
  - 긴급 수정: `hotfix/61`
- 연관된 이슈가 없으면 먼저 이슈를 생성한 뒤 그 번호로 브랜치를 만든다.

`type`은 소문자를 사용한다.

## Pull Request Guidelines

PR 설명에는 다음 내용을 포함한다.

- 변경 목적
- 주요 변경 내용
- API 변경 여부
- DB 스키마 변경 여부
- 테스트 및 검증 방법
- 리뷰 요청 포인트

## Merge Criteria

- 필요한 테스트가 통과해야 한다.
- 기능 검증이 필요한 변경은 검증 방법을 남긴다.
- 병합 방식은 **Merge Commit**을 사용한다.
- 모든 리뷰 코멘트가 해결된 후 병합한다.

## Review Policy

현재 프로젝트는 3인 개발이므로 PR 병합 전 반드시 다른 팀원 1명 이상의 승인을 받아야 한다.

병합 전 다음 조건을 확인한다.

- 관련 테스트가 통과하는가
- 민감 파일(`.env`, secret 등)이 포함되지 않았는가
- PR 설명에 변경 목적과 주요 내용이 작성되어 있는가
- 최소 1명의 팀원이 Approve 하였는가
