package org.booklore.service.email;

import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.EmailProviderV2Entity;
import org.booklore.util.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class EmailSenderHelperTest {

    private final EmailSenderHelper emailSenderHelper = new EmailSenderHelper();

    @Test
    void populateMessage_declaresEpubContentTypeInsteadOfOctetStreamDefault(@TempDir Path tempDir) throws Exception {
        Path bookPath = tempDir.resolve("The Goal - Elle Kennedy (2016).epub");
        Files.writeString(bookPath, "fake epub bytes for MIME header assertions");

        BookEntity book = bookWithTitle("The Goal");
        BookFileEntity bookFileEntity = new BookFileEntity();
        EmailProviderV2Entity provider = emailProvider();

        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.getBookFullPath(book, bookFileEntity)).thenReturn(bookPath);

            emailSenderHelper.populateMessage(helper, provider, "reader@example.com", book, bookFileEntity);
        }
        message.saveChanges();

        MimeMultipart multipart = (MimeMultipart) message.getContent();
        Part attachmentPart = multipart.getBodyPart(1);

        assertThat(attachmentPart.getContentType()).startsWith("application/epub+zip");

        ByteArrayOutputStream rawMessage = new ByteArrayOutputStream();
        message.writeTo(rawMessage);
        String raw = rawMessage.toString(StandardCharsets.UTF_8);

        assertThat(raw).contains("Content-Type: application/epub+zip");
    }

    @Test
    void populateMessage_fallsBackToOctetStreamForUnknownExtension(@TempDir Path tempDir) throws Exception {
        Path filePath = tempDir.resolve("notes.txt");
        Files.writeString(filePath, "plain text content");

        BookEntity book = bookWithTitle("Notes");
        BookFileEntity bookFileEntity = new BookFileEntity();
        EmailProviderV2Entity provider = emailProvider();

        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.getBookFullPath(book, bookFileEntity)).thenReturn(filePath);

            emailSenderHelper.populateMessage(helper, provider, "reader@example.com", book, bookFileEntity);
        }
        message.saveChanges();

        MimeMultipart multipart = (MimeMultipart) message.getContent();
        Part attachmentPart = multipart.getBodyPart(1);

        assertThat(attachmentPart.getContentType()).startsWith("application/octet-stream");
    }

    private BookEntity bookWithTitle(String title) {
        BookMetadataEntity metadata = BookMetadataEntity.builder().title(title).build();
        BookEntity book = new BookEntity();
        book.setId(1L);
        book.setMetadata(metadata);
        return book;
    }

    private EmailProviderV2Entity emailProvider() {
        return EmailProviderV2Entity.builder()
                .id(100L)
                .userId(1L)
                .host("smtp.test.com")
                .port(587)
                .username("user@test.com")
                .password("password")
                .fromAddress("user@test.com")
                .auth(true)
                .startTls(true)
                .build();
    }
}
