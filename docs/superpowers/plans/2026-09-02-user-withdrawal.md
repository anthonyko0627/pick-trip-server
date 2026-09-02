# 회원 탈퇴 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `DELETE /api/v1/users/me` 로 소프트 삭제하고, 30일 지난 탈퇴 계정을 Spring Batch Job 으로 매일 하드 삭제한다.

**Architecture:** 탈퇴는 `User.deleted`/`deletedAt` 플래그로 표현하고 리프레시 토큰만 즉시 폐기한다. 재로그인은 `CustomOAuth2UserService` 에서 복구한다. 하드 삭제는 `userPurgeJob`(chunk step) 이 만료 uid 목록을 읽어 `UserPurgeService.purge` 로 소유 데이터를 순서대로 지운다.

**Tech Stack:** Spring Boot 4.0.6, Spring Batch 6.0.3 (JDBC JobRepository), Flyway, Testcontainers MySQL

**Spec:** `docs/superpowers/specs/2026-09-02-user-withdrawal-design.md`

## Global Constraints

- Entity 에 `@Setter`/`@Data` 금지. 상태 변경은 도메인 메서드(`withdraw()`, `restore()`)로만.
- DDL 은 Flyway 마이그레이션으로만 (`ddl-auto: validate`). `spring.batch.jdbc.initialize-schema: never` (main) / `always` (test).
- 커밋은 사용자가 요청할 때만 만든다.

---

### Task 1: 소프트 삭제 (엔티티·마이그레이션·서비스·API)

**Files:**
- Modify: `domain/user/entity/User.java` — `deletedAt` 필드, `withdraw()` 확장, `restore()` 추가
- Create: `db/migration/V3__add_users_deleted_at.sql`
- Modify: `domain/user/service/UserService.java` — `withdraw(UUID uid)`
- Modify: `domain/user/controller/UserController.java` — `DELETE /me` → 204
- Modify: `gloal/security/SecurityConfig.java` — `/api/v1/users/**` authenticated
- Test: `UserServiceTest`, `UserControllerTest`

**Interfaces:**
- Produces: `User.withdraw()` (멱등, `deletedAt` 유지), `User.restore()`, `UserService.withdraw(UUID)`

- [ ] `UserServiceTest.Withdraw` 작성: 플래그·시각 기록, 토큰 삭제, 재호출 시 `deletedAt` 유지, 없는 uid → `USER_NOT_FOUND`
- [ ] `UserControllerTest.Withdraw` 작성: 204
- [ ] 구현 후 `./gradlew.bat test --tests '*UserServiceTest' --tests '*UserControllerTest'` 통과

### Task 2: 재로그인 복구

**Files:**
- Modify: `domain/auth/oauth2/CustomOAuth2UserService.java` — 기존 사용자가 `deleted` 면 `restore()`
- Test: `CustomOAuth2UserServiceTest.ExistingUser`

- [ ] 탈퇴 사용자 재로그인 → `deleted=false`, `deletedAt=null` 테스트 작성 후 구현

### Task 3: 하드 삭제 서비스

**Files:**
- Modify: `domain/user/repository/UserRepository.java` — `findUidsWithdrawnBefore(LocalDateTime)`
- Modify: `domain/share/repository/ShareTokenRepository.java` — `deleteAllByItineraryIdIn(Collection<UUID>)` (bulk)
- Modify: `domain/favorite/repository/FavoriteRepository.java` — `deleteAllByUserId(UUID)` (bulk)
- Create: `domain/user/service/UserPurgeService.java` — `purge(UUID uid)`
- Test: `UserPurgeServiceTest` (Mockito, InOrder)

**Interfaces:**
- Produces: `UserPurgeService.purge(UUID)` — share_tokens → itineraries → basket → favorites → refresh_tokens → users 순서

### Task 4: Spring Batch Job + 스케줄러 + 설정

**Files:**
- Modify: `build.gradle` — `spring-boot-starter-batch`
- Create: `db/migration/V4__create_spring_batch_tables.sql` — spring-batch-core 6.0.3 `schema-mysql.sql`
- Create: `domain/user/batch/UserPurgeJobConfig.java` — `userPurgeJob`, `userPurgeStep`, `@StepScope` reader
- Create: `gloal/scheduler/UserPurgeScheduler.java` — `@Scheduled(cron = "${user.purge.cron}")`
- Modify: `application.yaml` — `spring.batch.job.enabled=false`, `initialize-schema=never`, `user.purge.*`
- Modify: `src/test/resources/application.yaml` — `initialize-schema=always`, `job.enabled=false`
- Test: `UserPurgeJobTest` (`@SpringBootTest` + Testcontainers)

- [ ] 만료 사용자 + 소유 데이터 삭제, 유예 중·활성 사용자 유지, Job `COMPLETED` 검증

### Task 5: 문서

- Modify: `.agents/docs/api-endpoints.md` 사용자 섹션
- Modify: `AGENTS.md` 결정 로그
