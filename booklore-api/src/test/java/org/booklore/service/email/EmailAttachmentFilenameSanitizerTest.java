package org.booklore.service.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailAttachmentFilenameSanitizerTest {

    @Test
    void sanitize_replacesHashAndCollapsesRuns_reproducingKindleReportedFilename() {
        String input = "The Goal (Off-Campus #4) - Elle Kennedy (2016).epub";

        String result = EmailAttachmentFilenameSanitizer.sanitize(input);

        assertThat(result).isEqualTo("The Goal (Off-Campus 4) - Elle Kennedy (2016).epub");
    }

    @Test
    void sanitize_preservesExtensionCaseExactly() {
        String result = EmailAttachmentFilenameSanitizer.sanitize("Weird Title #1.EPUB");

        assertThat(result).endsWith(".EPUB");
    }

    @Test
    void sanitize_collapsesRunsOfMultipleRiskyCharacters() {
        String result = EmailAttachmentFilenameSanitizer.sanitize("A%%%&&&B.pdf");

        assertThat(result).isEqualTo("A B.pdf");
    }

    @Test
    void sanitize_trimsLeadingAndTrailingSeparators() {
        String result = EmailAttachmentFilenameSanitizer.sanitize("###Title###.pdf");

        assertThat(result).isEqualTo("Title.pdf");
    }

    @Test
    void sanitize_fallsBackToReadableNameWhenBaseIsEntirelySpecialCharacters() {
        String result = EmailAttachmentFilenameSanitizer.sanitize("####.epub");

        assertThat(result).isEqualTo("book.epub");
    }

    @Test
    void sanitize_handlesFileNameWithNoExtension() {
        String result = EmailAttachmentFilenameSanitizer.sanitize("README#file");

        assertThat(result).isEqualTo("README file");
    }

    @Test
    void sanitize_neverThrowsAndNeverReturnsEmptyOnNullInput() {
        String result = EmailAttachmentFilenameSanitizer.sanitize(null);

        assertThat(result).isEqualTo("book");
    }

    @Test
    void sanitize_neverReturnsEmptyOnBlankInput() {
        String result = EmailAttachmentFilenameSanitizer.sanitize("   ");

        assertThat(result).isEqualTo("book");
    }

    @Test
    void sanitize_preservesAccentedCharacters() {
        String result = EmailAttachmentFilenameSanitizer.sanitize("Café #1.epub");

        assertThat(result).isEqualTo("Café 1.epub");
    }

    @Test
    void sanitize_preservesCjkCharacters() {
        String result = EmailAttachmentFilenameSanitizer.sanitize("圖書 #1.epub");

        assertThat(result).isEqualTo("圖書 1.epub");
    }
}
