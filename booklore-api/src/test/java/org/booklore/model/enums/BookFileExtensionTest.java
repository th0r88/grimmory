package org.booklore.model.enums;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class BookFileExtensionTest {

    private final Locale originalDefaultLocale = Locale.getDefault();

    @AfterEach
    void restoreDefaultLocale() {
        Locale.setDefault(originalDefaultLocale);
    }

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

    @Test
    void contentTypeFor_isCaseInsensitiveUnderTurkishDefaultLocale() {
        // Under a Turkish/Azeri default locale, String#toLowerCase() maps 'I' to dotless
        // 'ı' rather than 'i', so "MOBI".toLowerCase() becomes "mobı" and would no longer
        // match the "mobi" extension unless the lookup is locale-independent.
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));

        assertThat(BookFileExtension.contentTypeFor("The Book.MOBI")).isEqualTo("application/x-mobipocket-ebook");
    }
}
