# Spec for fix-youtube-ip-block

branch: claude/feature/fix-youtube-ip-block

## Summary

- API `POST /api/subtitles` fails on the Oracle free-tier VPS because YouTube blocks datacenter/cloud IP ranges. Webshare residential proxy was attempted but also proved insufficient.
- The fix must route yt-dlp outbound requests through an IP that YouTube does not block, **without relying on Webshare**.
- Acceptable alternative solutions include: ScraperAPI, BrightData, Oxylabs, self-hosted residential proxy via a home router/VPN exit node, or fetching subtitles through a third-party subtitle mirror API (e.g. `subtitles.love`, `downsub.com`, or a `youtube-transcript` micro-service).
- The solution must be configurable via environment variables so the same Docker image works locally (no proxy) and on the VPS (proxy enabled).

## Functional Requirements

- `SubtitleFetcher` must successfully return subtitles when called from the Oracle VPS.
- The proxy/alternative provider must be configurable through an environment variable (e.g. `YTDLP_PROXY_URL`) — no hard-coded credentials in source.
- If the proxy/alternative call fails, the service logs a meaningful error and throws `SubtitleNotFoundException` (not a generic 500).
- Existing local development workflow must be unaffected when no proxy env var is set.
- `cookies.txt` support must be preserved alongside whichever fix is chosen.
- Solution must not increase average subtitle fetch latency beyond 10 seconds under normal conditions.

## Possible Edge Cases

- Proxy provider IP also gets blocked by YouTube over time → need easy swap via env var without rebuild.
- Proxy provider requires authentication (username:password or API key) embedded in the proxy URL — must not be logged.
- Third-party subtitle mirror may not have auto-generated subtitles for all videos (less coverage than yt-dlp direct).
- Proxy cost/quota exceeded → should degrade gracefully with a clear error, not a silent hang.
- yt-dlp `--remote-components ejs:github` may fail if the VPS has no outbound GitHub access.
- Race condition when multiple requests arrive simultaneously and each spawns a yt-dlp subprocess through the proxy.

## Acceptance Criteria

- [ ] `POST /api/subtitles` returns a valid subtitle list for a public YouTube video when called from the Oracle VPS.
- [ ] The proxy URL (or alternative provider key) is read exclusively from environment variables / `application.properties` — zero secrets in committed code.
- [ ] `docker-compose.yml` updated with a commented-out `YTDLP_PROXY_URL` env var placeholder.
- [ ] The fix is documented in `CLAUDE.md` under "Working with yt-dlp / cookies": which env var to set, how to obtain credentials for the chosen provider.
- [ ] Local `mvnw test` still passes (H2 in-memory, no real YouTube calls).
- [ ] Manual smoke test: run `docker compose up` on the VPS and successfully fetch subtitles for at least two different videos.

## Open Questions

- **Which proxy/alternative provider to use?** Candidates in rough priority:
  1. **ScraperAPI** — free tier 1,000 req/mo, simple HTTP proxy URL, known to bypass YouTube. using this option
  2. **BrightData / Oxylabs** — paid residential, higher reliability, higher cost.
  3. **Home router as SOCKS5 exit node** — free if a home device has a public IP.
  4. **Third-party subtitle REST API** — avoids yt-dlp entirely but coverage may be limited.
  - Decision needed from developer before implementation starts.
- **Fallback chain?** e.g. `direct → proxy → third-party API`. Or fail fast and require a working proxy? fail fast
- **VPS outbound ports**: does the Oracle security list allow outbound SOCKS5 (port 1080) or only HTTP/HTTPS? allowed port 22, 8082, 8081

## Testing Guidelines

Create test file(s) in `Backend/src/test/` for the following cases:

- Unit test: `SubtitleFetcher` correctly appends `--proxy <url>` to the yt-dlp command when `ytdlp.proxy-url` is set, and omits it when blank.
- Unit test: a non-zero yt-dlp exit code with proxy set throws `SubtitleNotFoundException` with a descriptive message.
- Integration / smoke test (manual, annotated `@Disabled("Requires live proxy")`): calls real YouTube via the configured proxy and asserts a non-empty subtitle list is returned.
