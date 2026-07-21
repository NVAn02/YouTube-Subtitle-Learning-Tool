package personal_project.personal_project.service;

import java.util.regex.Pattern;

/** Strips embedded credentials (e.g. a proxy API key in a URL) from text before logging/persisting it. */
public final class CredentialRedactor {

    private static final Pattern CREDENTIALS_PATTERN = Pattern.compile("://[^/@\\s]+@");

    private CredentialRedactor() {
    }

    public static String redact(String text) {
        if (text == null) return null;
        return CREDENTIALS_PATTERN.matcher(text).replaceAll("://***@");
    }
}
