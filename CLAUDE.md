# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

YouTube Subtitle Learning Tool: paste a YouTube link, watch the video with synced subtitles, click any word to get a context-aware translation (via Gemini). Spring Boot backend + Vue 3 frontend, MySQL for persistence, deployed as three Docker containers behind nginx.

The original design doc is at [youtube-subtitle-learning-tool-plan.md](youtube-subtitle-learning-tool-plan.md) (Vietnamese) — describes the intended architecture and API shapes; the implementation has since grown auth, saved words, and DB-backed caching beyond that doc's original "no accounts, no persistence" scope.

## Commands

### Backend (`Backend/`, Spring Boot 3.3.5, Java 17)
```
./mvnw clean package          # build + run tests
./mvnw test                   # run tests only
./mvnw test -Dtest=ClassName            # run a single test class
./mvnw test -Dtest=ClassName#methodName # run a single test method
./mvnw spring-boot:run         # run locally (port 8085)
```
Tests use an in-memory H2 database (see `YouTubeSubtitleLearningToolApplicationTests`), so `mvnw test` doesn't need MySQL running.

### Frontend (`Frontend/`, Vue 3 + Vite)
```
npm install
npm run dev        # dev server on :5173, proxies /api -> localhost:8085
npm run build
npm run preview
```
There is no frontend test suite or linter configured.

### Full stack (Docker)
```
docker compose up -d --build
```
Brings up MySQL (`:3307`), backend (`:8081` -> container `8085`), and frontend/nginx (`:8082`). Backend requires `./cookies.txt` at the repo root (YouTube auth cookies for yt-dlp, mounted read-only into the container).

## Architecture

**Request flow:** Vue SPA → axios (`Frontend/src/services/api.js`, baseURL `/api`) → nginx reverse-proxies `/api/*` to the backend container → Spring controllers.

### Backend package layout (`personal_project.personal_project`)
- `controller/` — REST layer: `SubtitleController` (`POST /api/subtitles`), `TranslateController` (`POST /api/translate`), `AuthController` (`/api/auth/register|login`), `SavedWordController`.
- `service/`:
  - `SubtitleFetcher` shells out to **yt-dlp** as a subprocess (preferred path) to pull auto/manual subtitles, converts VTT → the internal XML format; falls back to the `youtube-transcript-api` Java library if yt-dlp isn't on PATH. yt-dlp is invoked with `--remote-components ejs:github` to bypass YouTube's n-challenge bot detection, `--js-runtimes node`, and `--cookies` (from `/app/cookies.txt` if present) — this is the most fragile part of the system since it depends on YouTube's anti-bot measures.
  - `SubtitleParser` — raw XML → `SubtitleEntry` list (`{start, end, text}`).
  - `NlpService` — Apache OpenNLP `TokenizerME`, model loaded once at startup from `src/main/resources/models/en-token.bin` (singleton bean; `tokenize()` is synchronized because `TokenizerME` isn't thread-safe). Falls back to whitespace splitting if the model fails to load.
  - `TranslationService` — LangChain4j `GoogleAiGeminiChatModel`, prompted to return JSON (`{translation, explanation}`) for a word in sentence context. Results are cached in the `translations` table (`TranslationRepository.findByWordAndSentenceAndTargetLang`) to avoid repeat Gemini calls for the same word/sentence/lang triple.
- `entity/` + `repository/` — JPA: `Video` → `Subtitle` (subtitles are persisted per video and tokens stored as JSON in `tokensJson`; `SubtitleController` checks the DB by YouTube video ID before re-fetching/re-tokenizing), `Translation` (word+sentence+lang cache), `User`/`Role`, `SavedWord`.
- `config/` — `SecurityConfig` (stateless JWT auth via `JwtAuthFilter`/`JwtService`; `/api/auth/**`, `/api/subtitles/**`, `/api/translate/**` are public, everything else requires a bearer token), `CorsConfig`.

### Frontend layout (`Frontend/src/`)
Single-page app, no router. `App.vue` owns top-level state (subtitles, current video time, active sentence, selected word/translation) and composes `YouTubePlayer.vue` (YouTube IFrame API), `SubtitleList.vue` (renders sentences/tokens, highlights the active sentence by polling player time), `TranslationTooltip.vue` (click-to-translate popup), `AuthModal.vue`, and `SavedWordsModal.vue`. All backend calls go through `services/api.js`, which attaches a JWT bearer token from `localStorage` when present.

### CI/CD
`.github/workflows/cicd.yml`: on push/PR to `main` — build+test backend (Maven) and frontend (`npm ci && npm run build`) in parallel; on push to `main` only, build both Docker images and push to GHCR (`ghcr.io/<repo>/backend`, `.../frontend`), then SSH into the VPS and `docker compose pull && up -d`. The deploy target's `docker-compose.yml` and `cookies.txt` live in `/opt/sublearn` on the server, separate from this repo's copies.

## Working with yt-dlp / cookies

Subtitle fetching depends on `cookies.txt` (YouTube auth cookies) and an up-to-date yt-dlp with working n-challenge bypass — this has broken repeatedly (see recent commit history). If subtitle fetches start failing, check yt-dlp's version/remote-components cache and whether `cookies.txt` has expired before assuming an application bug.

## Checking Documentation

**Important**: when implement any lib/framework-specific feature, always check the appropriate documentation using the Context7 MCP server before writing any code
