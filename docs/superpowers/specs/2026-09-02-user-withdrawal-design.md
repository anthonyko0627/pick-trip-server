# 회원 탈퇴 (소프트 삭제 + 30일 후 Spring Batch 하드 삭제) 설계

- 이슈: #76
- 브랜치: `feat/76`
- 날짜: 2026-09-02

## 배경

`User` 엔티티에 `deleted` 플래그와 `withdraw()` 메서드는 있으나 탈퇴 API 가 없고, 탈퇴 시각도
기록하지 않는다. `TokenService.refresh` 는 이미 탈퇴 사용자의 토큰 재발급을 거부한다.
사용자 소유 데이터(refresh_tokens, baskets, favorites, itineraries, share_tokens)는 FK 없이
`user_id` UUID 로만 연결되어 있어 DB 의 ON DELETE CASCADE 를 쓸 수 없다.

## 목표

- 사용자가 스스로 탈퇴할 수 있다. 탈퇴 즉시 로그인 세션(리프레시 토큰)은 폐기된다.
- 탈퇴 후 30일 동안은 소프트 삭제 상태로 보관하며, 그 사이 재로그인하면 계정을 복구한다.
- 30일이 지난 계정과 소유 데이터는 Spring Batch Job 으로 매일 일괄 하드 삭제한다.
- 삭제 실행 이력(언제, 몇 건)이 Spring Batch 메타 테이블에 남는다.

## 비목표

- 탈퇴 직후 아직 유효한 액세스 토큰(최대 1시간)의 즉시 차단. 리프레시가 거부되므로 자연 만료에 맡긴다.
- 관리자 수동 트리거 API. 스케줄 실행만 제공한다.
- 탈퇴 사유 수집, 탈퇴 안내 메일.

## 소프트 삭제

### API

| 메서드 | URL                | 인증 | 응답 |
| ------ | ------------------ | :--: | ---- |
| DELETE | `/api/v1/users/me` | O    | 204  |

- `UserService.withdraw(uid)`
  1. 사용자를 조회한다. 없으면 `USER_NOT_FOUND`.
  2. `user.withdraw()` 로 `deleted=true`, `deletedAt=now` 를 기록한다. 이미 탈퇴 상태면
     `deletedAt` 을 덮어쓰지 않는다 (멱등, 유예 기간이 늘어나지 않게).
  3. `refreshTokenRepository.deleteById(uid)` 로 리프레시 토큰을 즉시 폐기한다.

### 엔티티·스키마

- `User.deletedAt : LocalDateTime` (nullable) 추가.
- `User.withdraw()` 가 `deletedAt` 도 기록하도록 확장. `User.restore()` 추가(플래그·시각 초기화).
- Flyway `V3__add_users_deleted_at.sql`: `ALTER TABLE users ADD COLUMN deleted_at datetime(6) NULL`.

### 재로그인 복구

`CustomOAuth2UserService.loadUser` 에서 기존 사용자를 찾았을 때 `deleted` 이면 `restore()` 를
호출한 뒤 프로필 동기화를 이어간다. 하드 삭제가 끝난 뒤라면 행이 없으므로 신규 가입 경로를 탄다.

## 하드 삭제 배치

### 의존성·설정

- `spring-boot-starter-batch` 추가. Boot 자동설정이 DataSource 기반 JDBC `JobRepository` 를 구성한다.
- 메타 테이블 DDL 은 프로젝트 규칙대로 Flyway 로만 나간다:
  `V4__create_spring_batch_tables.sql` (spring-batch-core 의 `schema-mysql.sql` 내용).
  `spring.batch.jdbc.initialize-schema: never`.
- 테스트 프로파일은 Flyway 가 꺼져 있으므로 `spring.batch.jdbc.initialize-schema: always`
  (Boot 가 continue-on-error 로 실행해 이미 있는 테이블은 건너뛴다).
- `spring.batch.job.enabled: false` 로 부팅 시 자동 실행을 막는다.
- 설정값

  ```yaml
  user:
    purge:
      retention-days: 30          # 탈퇴 후 보관 일수
      cron: "0 0 4 * * *"         # 매일 04:00 (SchedulingConfig 가 켜진 prod 에서만 동작)
  ```

### Job 구성 (`domain/user/batch`)

```
userPurgeJob
└── userPurgeStep  (chunk 10)
    ├── reader : ListItemReader<UUID>  — @StepScope. step 시작 시
    │            userRepository.findUidsWithdrawnBefore(now - retentionDays) 를 한 번 읽는다.
    │            삭제하면서 페이징하면 항목이 밀리므로 페이징 리더를 쓰지 않는다.
    └── writer : uid 마다 UserPurgeService.purge(uid)
```

- `UserPurgeService.purge(uid)` 는 `@Transactional` 이며 다음 순서로 지운다.
  1. `share_tokens` — 사용자 일정 id 목록 기준 bulk delete
  2. `itineraries` — 엔티티 로드 후 `deleteAll` (cascade 로 `itinerary_days`, `itinerary_items`)
  3. `baskets` — 엔티티 로드 후 `delete` (cascade 로 `basket_items`, `basket_companions`)
  4. `favorites` — `deleteByUserId`
  5. `refresh_tokens` — `deleteById`
  6. `users` — `deleteById`
- 한 사용자 삭제가 실패하면 그 chunk 는 롤백되고 Job 은 FAILED 로 끝난다. 다음 날 스케줄이
  다시 시도하며, 이미 지워진 사용자는 조회에 잡히지 않으므로 재실행이 안전하다.

### 스케줄러

`UserPurgeScheduler` (`gloal/scheduler`) 가 `@Scheduled(cron = "${user.purge.cron}")` 로
`jobOperator.start(userPurgeJob, run.time=now)` 를 호출한다. 실행마다 `run.time` 파라미터가
달라 새 JobInstance 가 만들어진다. 예외는 잡아서 로그로 남긴다(기존 `ContentSyncScheduler` 와 동일).

## 데이터 흐름 요약

```
DELETE /users/me ──► withdraw(): deleted=true, deletedAt=now, refresh token 삭제
        │
        ├─ 30일 내 재로그인 ──► restore(): deleted=false, deletedAt=null
        │
        └─ 30일 경과 ──► [매일 04:00] userPurgeJob ──► purge(uid) ──► 소유 데이터 + users 행 삭제
```

## 에러 처리

- 탈퇴 API: 사용자 없음 → `USER_NOT_FOUND`(404). 그 외 신규 에러 코드 없음.
- 배치: 개별 사용자 삭제 예외는 chunk 롤백 후 Job FAILED. 스케줄러는 예외를 로그로만 남긴다.

## 테스트

| 테스트                          | 검증 내용                                                          |
| ------------------------------- | ------------------------------------------------------------------ |
| `UserServiceTest.withdraw`      | 플래그·시각 기록, 리프레시 토큰 삭제, 재호출 시 `deletedAt` 유지     |
| `UserControllerTest`            | `DELETE /me` 가 204                                                |
| `CustomOAuth2UserServiceTest`   | 탈퇴 사용자 재로그인 시 `restore()` 로 복구                          |
| `UserPurgeServiceTest`          | 삭제 호출 범위·순서 (Mockito)                                       |
| `UserPurgeJobTest`              | Testcontainers MySQL 로 Job 실행. 30일 지난 사용자와 소유 데이터만 삭제, 유예 중 사용자는 유지 |

## 문서

- `.agents/docs/api-endpoints.md` 사용자 섹션에 `DELETE /api/v1/users/me` 추가.
- `AGENTS.md` 결정 로그에 Spring Batch 채택 이유 추가.
