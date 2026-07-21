# Admin Role & CMS (with error-log management)

## Context

The spec (`_specs/admin-role-cms.md`) asks for a real `ADMIN` role plus an admin CMS, including an error-log viewer. Today `Role{USER,ADMIN}` exists on `User` but is inert: registration hardcodes `Role.USER`, `SecurityConfig` has no role-based rule, no admin endpoint exists, and the frontend never even receives/stores a role. There's also no error persistence anywhere — failures in `SubtitleFetcher`/`TranslationService` are only logged via SLF4J to the console, with `SubtitleNotFoundException`/`TranslationException` messages already redacted of proxy credentials but nowhere an admin could see without SSH-ing into the VPS.

This plan wires up the whole loop: bootstrap one admin account on startup, protect `/api/admin/**`, expose CRUD-ish admin endpoints for users/videos/translations/error-logs, persist error logs from the two fragile flows plus a global catch-all, and build a `/admin` Vue route (via new `vue-router` dependency, per user's choice) gated by role, with tabs for each resource.

Decisions baked into this plan (reasonable defaults for the spec's open questions — flag if you want something different):
- **First admin**: a `CommandLineRunner` seeds one admin from `ADMIN_USERNAME`/`ADMIN_PASSWORD` env vars on startup if no enabled admin exists yet; skips (with a warning log) if the password env var is blank, so a prod deploy never silently creates a blank-password admin.
- **Self-protection**: an admin can't demote or disable their own account, and the last remaining enabled admin can't be demoted/disabled by anyone — enforced in a new `AdminUserService`.
- **Error-log capture scope**: explicit `ErrorLogService.record(...)` calls at the existing catch sites in `SubtitleController`/`TranslateController` (richer context: videoId/word), plus a new `@RestControllerAdvice` global handler as a catch-all safety net for everything else (e.g. `SavedWordController`, new admin controllers) — not literally every exception, but every flow that can realistically fail.
- **Retention**: manual delete only via CMS (single + bulk-by-filter); no automatic retention/cron job — out of scope for v1.
- **No MODERATOR role, no dashboard/stats** — out of scope, matches spec's stated scope.
- **JWT stays claim-less**: `JwtAuthFilter` already reloads `UserDetails` fresh from DB per request, so role/enabled changes take effect on the very next request without needing to touch token generation — no changes needed to `JwtService`.

## Backend changes

### 1. User entity gets a real `enabled` flag
`entity/User.java`: add `@Column(nullable = false) @Builder.Default private boolean enabled = true;`, change `isEnabled()` to `return enabled;` (currently hardcoded `true`). Spring Security's `DaoAuthenticationProvider` already checks `UserDetails.isEnabled()` before password validation, so disabling a user blocks login for free — no changes needed in `AuthController.login`'s existing generic `catch (Exception e)` → "Invalid username or password" (keeps behavior non-leaking; matches the spec's "chỉ cần từ chối đăng nhập" default).

`repository/UserRepository.java`: add `long countByRoleAndEnabledTrue(Role role)` (used for the last-admin guard) and rely on inherited `findAll(Pageable)` / `Page<User> findByUsernameContainingIgnoreCase(String, Pageable)` for the admin list+search.

### 2. Registration/login expose role to the frontend
`dto/AuthResponse.java`: add `private String role;`. `AuthController.java` register (line ~36-42) and login (~44-61): return `new AuthResponse(jwtToken, user.getUsername(), user.getRole().name())` instead of the 2-arg form.

### 3. Admin bootstrap
New `config/AdminSeeder.java` (`@Component implements CommandLineRunner`, `@Slf4j`), constructor-injects `UserRepository` + `PasswordEncoder`. On `run()`:
- if `userRepository.countByRoleAndEnabledTrue(Role.ADMIN) > 0`, no-op.
- else read `${admin.seed.username:admin}` / `${admin.seed.password:}` via `@Value`; if password blank, `log.warn(...)` and return.
- if a user with that username exists, promote it (`role=ADMIN, enabled=true`) and save; else build+save a new `User` with the encoded password.

`application.properties`: add
```
admin.seed.username=${ADMIN_USERNAME:admin}
admin.seed.password=${ADMIN_PASSWORD:}
```
`docker-compose.yml` backend service `environment:` block: add `- ADMIN_USERNAME=${ADMIN_USERNAME:-admin}` and `- ADMIN_PASSWORD=${ADMIN_PASSWORD:-}` following the exact same `.env`/shell-var passthrough pattern already used for `YTDLP_PROXY_URL`, with an inline comment explaining first-boot seeding.

### 4. Self-protection + role/status changes
New `service/AdminUserService.java` (`@Service`, constructor-injects `UserRepository`):
- `changeRole(Long targetId, Role newRole, User actingAdmin)`: throws `AdminActionException` if `targetId.equals(actingAdmin.getId())`, or if demoting the last enabled admin (`countByRoleAndEnabledTrue(ADMIN) <= 1` and target is currently an enabled ADMIN being set to USER). Otherwise updates+saves.
- `setEnabled(Long targetId, boolean enabled, User actingAdmin)`: same self-protection + last-admin guard, otherwise updates+saves.
- New `service/AdminUserService.AdminActionException` (nested `RuntimeException`, single message ctor) — matches the existing `SubtitleNotFoundException`/`TranslationException` nested-exception convention.

### 5. Error log persistence
New `entity/ErrorSeverity.java`: `public enum ErrorSeverity { WARN, ERROR }` (top-level enum file, matching `Role.java`'s style).

New `entity/ErrorLog.java` (same Lombok/IDENTITY/`@Table` conventions as other entities): `id`, `occurredAt` (`LocalDateTime`, set via `@PrePersist` exactly like `SavedWord.onCreate()`), `severity` (`@Enumerated(STRING)`), `source` (String, e.g. `"SUBTITLE_FETCH"`/`"TRANSLATION"`/`"UNCAUGHT"`), `message` (TEXT), `stackTrace` (TEXT, nullable), `context` (String, nullable — videoId or word). Table `error_logs`.

New `repository/ErrorLogRepository.java` extends `JpaRepository<ErrorLog, Long>` **and `JpaSpecificationExecutor<ErrorLog>`** — the one deliberate deviation from this codebase's "derived-methods-only" convention, because filtering by an arbitrary combination of optional severity/source/date-range is exactly what Specifications are for; enumerating every combination as derived method names would be worse.

New `service/CredentialRedactor.java`: extract the regex+replace body currently in `SubtitleFetcher.redactCredentials` (lines 226-230) into `public static String redact(String text)`. Change `SubtitleFetcher.redactCredentials` to a one-line delegate to `CredentialRedactor.redact(...)` — preserves the existing package-private call sites (lines 112, 122, 222) and `SubtitleFetcherTest`'s direct test of it, while making the same redaction available to error-log persistence (per CLAUDE.md: don't bypass this redaction when adding new logging).

New `service/ErrorLogService.java` (`@Slf4j @Service`, constructor-injects `ErrorLogRepository`):
- `void record(ErrorSeverity severity, String source, String message, Throwable throwable, String context)`: builds a stack trace string (`StringWriter`/`PrintWriter`, no new dependency), redacts `message`/`stackTrace` via `CredentialRedactor.redact`, saves an `ErrorLog`. Entire body wrapped in its own `try/catch(Exception e) { log.error("Failed to persist error log", e); }` so a DB hiccup while logging an error never breaks the request that triggered it (spec's edge case).
- `Page<ErrorLog> search(ErrorSeverity severity, String source, LocalDateTime from, LocalDateTime to, Pageable pageable)` — builds a `Specification` combining whichever filters are non-null.
- `void delete(Long id)` and `void deleteMatching(ErrorSeverity, String source, LocalDateTime from, LocalDateTime to)` for single/bulk delete.

Wire calls into existing catch blocks (additive, one line each — no control-flow changes):
- `SubtitleController.java` catch `SubtitleFetcher.SubtitleNotFoundException` (~121-123): `errorLogService.record(WARN, "SUBTITLE_FETCH", e.getMessage(), e, videoId)`.
- same file, catch generic `Exception` (~124-128): `errorLogService.record(ERROR, "SUBTITLE_FETCH", e.getMessage(), e, videoId)`.
- `TranslateController.java` catch `TranslationService.TranslationException` (~52-55): `errorLogService.record(ERROR, "TRANSLATION", e.getMessage(), e, request.getWord())`.

New `config/GlobalExceptionHandler.java` (`@RestControllerAdvice`, `@Slf4j`): `@ExceptionHandler(Exception.class)` → `log.error(...)`, `errorLogService.record(ERROR, "UNCAUGHT", e.getMessage(), e, null)`, return `ResponseEntity.internalServerError().body(new ErrorResponse("An unexpected error occurred."))`. This only fires for exceptions not already caught locally (e.g. anything from `SavedWordController` or the new admin controllers), so it doesn't duplicate the two controllers above.

### 6. Admin REST API (`controller/admin/`, all under `/api/admin/**`)
DTOs in `dto/` (same `@Data @NoArgsConstructor @AllArgsConstructor` style as `ErrorResponse`): `AdminUserResponse{id,username,role,enabled}`, `UpdateRoleRequest{role}`, `UpdateStatusRequest{enabled}`, `AdminVideoResponse{id,youtubeId,subtitleCount}`, `AdminTranslationResponse{id,word,sentence,targetLang,translatedText}`, `ErrorLogResponse{id,occurredAt,severity,source,message,stackTrace,context}`.

- `AdminUserController` — `GET /api/admin/users?search=&page=&size=`, `PATCH /api/admin/users/{id}/role`, `PATCH /api/admin/users/{id}/status`. Role/status endpoints resolve the acting admin from `Authentication` principal, delegate to `AdminUserService`, catch `AdminActionException` → 400 `ErrorResponse`.
- `AdminVideoController` — `GET /api/admin/videos?page=&size=` (maps `Video`+`subtitles.size()` → `AdminVideoResponse`), `DELETE /api/admin/videos/{id}` (existing `cascade=ALL, orphanRemoval=true` on `Video.subtitles` already deletes subtitles).
- `AdminTranslationController` — `GET /api/admin/translations?word=&page=&size=`, `DELETE /api/admin/translations/{id}`.
- `AdminErrorLogController` — `GET /api/admin/error-logs?severity=&source=&from=&to=&page=&size=` (delegates to `ErrorLogService.search`), `DELETE /api/admin/error-logs/{id}`, `DELETE /api/admin/error-logs?severity=&source=&from=&to=` (bulk, delegates to `deleteMatching`).

### 7. Authorization
`SecurityConfig.java`: add `.requestMatchers("/api/admin/**").hasAuthority("ADMIN")` before `anyRequest().authenticated()` (using `hasAuthority`, not `hasRole`, since `User.getAuthorities()` returns the raw enum name with no `ROLE_` prefix — matches existing convention, no changes to `getAuthorities()` needed).

## Frontend changes

### 1. Router (new `vue-router` dependency, per your choice)
`npm install vue-router@4` in `Frontend/`. New `Frontend/src/router/index.js`: `createRouter({ history: createWebHistory(), routes: [{path:'/', component: HomeView}, {path:'/admin', component: AdminView}] })` with a `beforeEach` guard: if target is `/admin` and `localStorage.getItem('role') !== 'ADMIN'`, redirect to `/`.

`Frontend/src/main.js`: install the router.

`Frontend/src/App.vue`: current entire template/script becomes `Frontend/src/views/HomeView.vue` (near-verbatim move — no behavior change). `App.vue` itself becomes a thin shell: `<template><router-view /></template>`.

### 2. Role awareness
`services/api.js`: no interceptor changes needed (JWT attach already works); add new admin functions following the existing `api.<verb>(path) → res.data` pattern: `getUsers`, `updateUserRole`, `updateUserStatus`, `getVideos`, `deleteVideo`, `getTranslations`, `deleteTranslation`, `getErrorLogs`, `deleteErrorLog`.

`AuthModal.vue`: after login/register, also `localStorage.setItem('role', data.role)` and include role in the `auth-success` emit payload.

`HomeView.vue` (moved `App.vue`): on mount, also hydrate a `currentRole` ref from `localStorage.getItem('role')`; `logout()` also clears the `role` key; header shows a `<router-link to="/admin">Quản trị</router-link>` only `v-if="currentRole === 'ADMIN'"`.

### 3. CMS screen
New `Frontend/src/views/AdminView.vue`: Bootstrap nav-tabs (matching existing utility-class-only styling, no new UI library) switching between 4 child components:
- `Frontend/src/components/admin/AdminUsers.vue` — table of users (username/role/enabled), role-change dropdown + enable/disable toggle per row, each destructive/state-changing action behind a native `confirm(...)` per the spec's confirmation requirement, calling the new api.js functions and re-fetching on success; surfaces `AdminActionException` messages (e.g. "can't demote yourself") inline.
- `Frontend/src/components/admin/AdminVideos.vue` — table of videos + subtitle count, delete button with confirm.
- `Frontend/src/components/admin/AdminTranslations.vue` — searchable table of cached translations, delete button with confirm.
- `Frontend/src/components/admin/AdminErrorLogs.vue` — filterable (severity/source/date range) paginated table, row-expand to show full message/stack trace, delete-one and delete-matching-filter buttons with confirm.

## Testing

The spec says "create tests in ./tests" but this repo has no such folder — backend tests live in `Backend/src/test/java/...` (Maven convention) and that's what I'll follow, matching the existing `YouTubeSubtitleLearningToolApplicationTests` (`@SpringBootTest` + inline H2 properties) style, extended with `@AutoConfigureMockMvc` (no new dependency — already in `spring-boot-starter-test`) since this is the first feature needing real HTTP-level authorization checks:

- `AdminAuthorizationTest` — seeds one USER and one ADMIN via `UserRepository` in `@BeforeEach`, logs in via the real `/api/auth/login` flow to get JWTs, asserts `/api/admin/users` returns 403 for the USER token and 200 for the ADMIN token.
- `AdminUserServiceTest` — role change persists; self-demotion throws `AdminActionException`; demoting the last enabled admin throws; disabling a user blocks subsequent login (via `/api/auth/login` → non-200).
- `ErrorLogServiceTest` — `record(...)` persists a row with redacted message; hitting `/api/subtitles` with a bad video id produces a WARN `error_logs` row with source `SUBTITLE_FETCH`.
- `SubtitleFetcherTest` — no changes needed (still exercises `redactCredentials`, now a delegate — same behavior).

## Verification

- `./mvnw test` — full backend suite including new tests.
- `./mvnw spring-boot:run` locally with `ADMIN_USERNAME`/`ADMIN_PASSWORD` env vars set, confirm the admin user is seeded on first boot (check `users` table), confirm a second boot doesn't duplicate/reset it.
- `npm run dev` in `Frontend/`, log in as the seeded admin, confirm the "Quản trị" link appears and `/admin` loads the CMS tabs; log in as a normal registered user, confirm the link is absent and navigating to `/admin` directly redirects to `/`.
- Manually trigger a subtitle-fetch failure (bad video ID) and confirm it shows up in the Error Logs tab with a redacted message.
