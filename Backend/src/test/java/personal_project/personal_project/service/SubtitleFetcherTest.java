package personal_project.personal_project.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the process-free parts of SubtitleFetcher (command building, exit-code
 * handling, credential redaction). None of these spawn a real yt-dlp process, so they run
 * fine in CI where yt-dlp isn't installed.
 */
class SubtitleFetcherTest {

    private SubtitleFetcher newFetcher(String proxyUrl) {
        SubtitleFetcher fetcher = new SubtitleFetcher();
        ReflectionTestUtils.setField(fetcher, "proxyUrl", proxyUrl);
        return fetcher;
    }

    @Test
    void buildYtDlpCommand_includesProxyAndNoCheckCertificate_whenProxyUrlSet() {
        SubtitleFetcher fetcher = newFetcher("http://scraperapi:secretkey@proxy-server.scraperapi.com:8001");

        List<String> cmd = fetcher.buildYtDlpCommand("abc123", "/tmp/out.%(ext)s");

        int proxyIndex = cmd.indexOf("--proxy");
        assertTrue(proxyIndex >= 0, "expected --proxy flag to be present");
        assertEquals("http://scraperapi:secretkey@proxy-server.scraperapi.com:8001", cmd.get(proxyIndex + 1));
        assertTrue(cmd.contains("--no-check-certificate"), "expected --no-check-certificate when proxy is set");
    }

    @Test
    void buildYtDlpCommand_omitsProxyFlags_whenProxyUrlBlank() {
        SubtitleFetcher fetcher = newFetcher("");

        List<String> cmd = fetcher.buildYtDlpCommand("abc123", "/tmp/out.%(ext)s");

        assertFalse(cmd.contains("--proxy"), "no proxy configured locally, --proxy must be absent");
        assertFalse(cmd.contains("--no-check-certificate"));
    }

    @Test
    void checkExitCode_throwsSubtitleNotFoundException_onNonZeroExit() {
        SubtitleFetcher fetcher = newFetcher("");

        SubtitleFetcher.SubtitleNotFoundException ex = assertThrows(
                SubtitleFetcher.SubtitleNotFoundException.class,
                () -> fetcher.checkExitCode(1, "ERROR: fake failure", "vid123")
        );

        assertTrue(ex.getMessage().contains("vid123"));
        assertTrue(ex.getMessage().contains("1"));
        assertTrue(ex.getMessage().contains("fake failure"));
    }

    @Test
    void checkExitCode_doesNothing_onZeroExit() {
        SubtitleFetcher fetcher = newFetcher("");

        fetcher.checkExitCode(0, "all good", "vid123");
        // no exception -> success
    }

    @Test
    void redactCredentials_scrubsProxyCredentials() {
        String redacted = SubtitleFetcher.redactCredentials(
                "Connecting via http://scraperapi:secretkey@proxy-server.scraperapi.com:8001 ...");

        assertFalse(redacted.contains("secretkey"), "API key must not appear in redacted output");
        assertTrue(redacted.contains("://***@"));
    }

    @Test
    @Disabled("Requires a live ScraperAPI proxy + network access — run manually before deploying")
    void fetchSubtitles_returnsNonEmptyXml_throughLiveProxy() {
        String proxyUrl = System.getenv("YTDLP_PROXY_URL");
        SubtitleFetcher fetcher = newFetcher(proxyUrl);

        String xml = fetcher.fetchSubtitles("dQw4w9WgXcQ");

        assertTrue(xml != null && xml.contains("<text"));
    }
}
