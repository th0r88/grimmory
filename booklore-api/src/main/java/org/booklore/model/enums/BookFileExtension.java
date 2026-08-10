package org.booklore.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

@RequiredArgsConstructor
@Getter
public enum BookFileExtension {
    PDF("pdf", BookFileType.PDF, "application/pdf"),
    EPUB("epub", BookFileType.EPUB, "application/epub+zip"),
    CBZ("cbz", BookFileType.CBX, "application/vnd.comicbook+zip"),
    CBR("cbr", BookFileType.CBX, "application/vnd.comicbook-rar"),
    CB7("cb7", BookFileType.CBX, "application/x-cb7"),
    MOBI("mobi", BookFileType.MOBI, "application/x-mobipocket-ebook"),
    AZW3("azw3", BookFileType.AZW3, "application/vnd.amazon.ebook"),
    AZW("azw", BookFileType.AZW3, "application/vnd.amazon.ebook"),
    FB2("fb2", BookFileType.FB2, "application/x-fictionbook+xml"),
    M4B("m4b", BookFileType.AUDIOBOOK, "audio/mp4"),
    M4A("m4a", BookFileType.AUDIOBOOK, "audio/mp4"),
    MP3("mp3", BookFileType.AUDIOBOOK, "audio/mpeg"),
    OPUS("opus", BookFileType.AUDIOBOOK, "audio/opus");

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final String extension;
    private final BookFileType type;
    private final String contentType;

    public static Optional<BookFileExtension> fromFileName(String fileName) {
        String lower = fileName.toLowerCase();
        return Arrays.stream(values())
                .filter(e -> lower.endsWith("." + e.extension))
                .findFirst();
    }

    /**
     * Resolves the media type to declare for this file when it is sent as an email attachment.
     * Unknown or unmapped extensions fall back to {@code application/octet-stream} rather than throwing.
     */
    public static String contentTypeFor(String fileName) {
        if (fileName == null) {
            return DEFAULT_CONTENT_TYPE;
        }
        return fromFileName(fileName).map(BookFileExtension::getContentType).orElse(DEFAULT_CONTENT_TYPE);
    }
}
