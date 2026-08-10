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
    // filesystem-reserved characters on common platforms plus control/format characters.
    //
    // \p{Cc} (control) + \p{Cf} (format) catch Unicode control/format code points beyond the
    // ASCII-only \p{Cntrl} — notably U+202E RIGHT-TO-LEFT OVERRIDE, a real filename-spoofing
    // vector, and zero-width characters. Deliberately not the broader \p{C}: that also matches
    // surrogates, private-use, and unassigned code points, and stripping unassigned ones would
    // mangle filenames using scripts newer than this JVM's Unicode tables. Ordinary accented and
    // CJK characters are outside \p{Cc}/\p{Cf} and are left untouched.
    private static final Pattern RISKY_CHARS = Pattern.compile("[#%&+=?\\\\/:*\"<>|{}]|[\\p{Cc}\\p{Cf}]");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    public String sanitize(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return FALLBACK_BASE_NAME;
        }

        int lastDot = fileName.lastIndexOf('.');
        // lastDot == 0 (a leading-dot name such as ".epub") still counts as having an
        // extension: the base is empty rather than the dot being treated as part of the
        // base name, so the empty-base fallback below kicks in and yields "book.epub"
        // instead of passing ".epub" straight through.
        boolean hasExtension = lastDot >= 0 && lastDot < fileName.length() - 1;

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
