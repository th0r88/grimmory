package org.booklore.model.enums;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookFileExtensionTest {

    @ParameterizedTest
    @CsvSource({
            "book.pdf,                application/pdf",
            "book.epub,               application/epub+zip",
            "book.cbz,                application/vnd.comicbook+zip",
            "book.cbr,                application/vnd.comicbook-rar",
            "book.cb7,                application/x-cb7",
            "book.mobi,               application/x-mobipocket-ebook",
            "book.azw3,               application/vnd.amazon.ebook",
            "book.azw,                application/vnd.amazon.ebook",
            "book.fb2,                application/x-fictionbook+xml",
            "book.m4b,                audio/mp4",
            "book.m4a,                audio/mp4",
            "book.mp3,                audio/mpeg",
            "book.opus,               audio/opus",
    })
    void contentTypeFor_resolvesRegisteredExtensions(String fileName, String expectedContentType) {
        assertThat(BookFileExtension.contentTypeFor(fileName)).isEqualTo(expectedContentType);
    }

    @Test
    void contentTypeFor_isCaseInsensitiveOnExtension() {
        assertThat(BookFileExtension.contentTypeFor("The Goal.EPUB")).isEqualTo("application/epub+zip");
    }

    @Test
    void contentTypeFor_fallsBackToOctetStreamForUnknownExtension() {
        assertThat(BookFileExtension.contentTypeFor("notes.txt")).isEqualTo("application/octet-stream");
    }

    @Test
    void contentTypeFor_fallsBackToOctetStreamForMissingExtension() {
        assertThat(BookFileExtension.contentTypeFor("README")).isEqualTo("application/octet-stream");
    }

    @Test
    void contentTypeFor_neverThrowsOnNullFileName() {
        assertThat(BookFileExtension.contentTypeFor(null)).isEqualTo("application/octet-stream");
    }
}
