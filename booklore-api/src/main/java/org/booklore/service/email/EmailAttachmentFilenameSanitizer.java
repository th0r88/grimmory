package org.booklore.service.email;

import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

/**
 * Sanitises the display filename used when a book file is attached to an outgoing email.
 * <p>
 * This exists solely for the attachment header — it must never be used to rename or resolve
 * the file on disk. Some downstream mail consumers (notably Amazon's Send to Kindle) mishandle
 * "URL-ish" characters such as {@code #} in attachment filenames, so those are replaced with a
 * plain space before the message is built.
 */
@UtilityClass
public class EmailAttachmentFilenameSanitizer {

    private static final String FALLBACK_BASE_NAME = "book";

    // Characters known (or strongly suspected, per the Send-to-Kindle failure this fixes) to
    // confuse downstream mail/URL handling: '#' is a URL fragment delimiter (the root cause here),
    // '%%' triggers percent-decoding, '&' '=' '?' '+' are URL query syntax, the remainder are
    // filesystem-reserved characters on common platforms plus raw control characters.
    private static final Pattern RISKY_CHARS = Pattern.compile("[#%&+=?\\\\/:*\"<>|{}]|\\p{Cntrl}");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    public String sanitize(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return FALLBACK_BASE_NAME;
        }

        int lastDot = fileName.lastIndexOf('.');
        boolean hasExtension = lastDot > 0 && lastDot < fileName.length() - 1;

        String base = hasExtension ? fileName.substring(0, lastDot) : fileName;
        String extension = hasExtension ? fileName.substring(lastDot) : "";

        String sanitizedBase = collapseAndTrim(RISKY_CHARS.matcher(base).replaceAll(" "));
        if (sanitizedBase.isEmpty()) {
            sanitizedBase = FALLBACK_BASE_NAME;
        }

        return sanitizedBase + extension;
    }

    private String collapseAndTrim(String value) {
        return WHITESPACE_RUN.matcher(value).replaceAll(" ").trim();
    }
}
