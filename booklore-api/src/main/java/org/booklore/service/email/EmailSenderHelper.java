package org.booklore.service.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.EmailProviderV2Entity;
import org.booklore.util.FileUtils;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

@Slf4j
@Component
public class EmailSenderHelper {

    public void sendEmail(EmailProviderV2Entity emailProvider, String recipientEmail, BookEntity book, BookFileEntity bookFileEntity) throws MessagingException {
        JavaMailSenderImpl dynamicMailSender = setupMailSender(emailProvider);
        MimeMessage message = dynamicMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom(StringUtils.firstNonEmpty(emailProvider.getFromAddress(), emailProvider.getUsername()));
        helper.setTo(recipientEmail);
        helper.setSubject("Your Book from Booklore: " + book.getMetadata().getTitle());
        helper.setText(generateEmailBody(book.getMetadata().getTitle()));
        File bookFile = FileUtils.getBookFullPath(book, bookFileEntity).toFile();
        helper.addAttachment(bookFile.getName(), bookFile);
        dynamicMailSender.send(message);
        log.info("Book sent successfully to {}", recipientEmail);
    }

    public JavaMailSenderImpl setupMailSender(EmailProviderV2Entity emailProvider) {
        JavaMailSenderImpl dynamicMailSender = new JavaMailSenderImpl();
        dynamicMailSender.setHost(emailProvider.getHost());
        dynamicMailSender.setPort(emailProvider.getPort());
        dynamicMailSender.setUsername(emailProvider.getUsername());
        dynamicMailSender.setPassword(emailProvider.getPassword());

        Properties mailProps = dynamicMailSender.getJavaMailProperties();
        mailProps.put("mail.smtp.auth", emailProvider.isAuth());

        ConnectionType connectionType = determineConnectionType(emailProvider);
        configureConnectionType(mailProps, connectionType, emailProvider);
        configureTimeouts(mailProps);

        String debugMode = System.getProperty("mail.debug", "false");
        mailProps.put("mail.debug", debugMode);

        log.info("Email configuration: Host={}, Port={}, Type={}, Timeouts=60s", emailProvider.getHost(), emailProvider.getPort(), connectionType);

        return dynamicMailSender;
    }

    public ConnectionType determineConnectionType(EmailProviderV2Entity emailProvider) {
        if (emailProvider.getPort() == 465) {
            return ConnectionType.SSL;
        } else if (emailProvider.getPort() == 587 && emailProvider.isStartTls()) {
            return ConnectionType.STARTTLS;
        } else if (emailProvider.isStartTls()) {
            return ConnectionType.STARTTLS;
        } else {
            return ConnectionType.PLAIN;
        }
    }

    public void configureConnectionType(Properties mailProps, ConnectionType connectionType, EmailProviderV2Entity emailProvider) {
        switch (connectionType) {
            case SSL -> {
                mailProps.put("mail.transport.protocol", "smtps");
                mailProps.put("mail.smtp.ssl.enable", "true");
                mailProps.put("mail.smtp.ssl.trust", emailProvider.getHost());
                mailProps.put("mail.smtp.starttls.enable", "false");
                mailProps.put("mail.smtp.ssl.protocols", "TLSv1.2,TLSv1.3");
                mailProps.put("mail.smtp.ssl.checkserveridentity", "false");
                mailProps.put("mail.smtp.ssl.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                mailProps.put("mail.smtp.ssl.socketFactory.fallback", "false");
            }
            case STARTTLS -> {
                mailProps.put("mail.transport.protocol", "smtp");
                mailProps.put("mail.smtp.starttls.enable", "true");
                mailProps.put("mail.smtp.starttls.required", "true");
                mailProps.put("mail.smtp.ssl.enable", "false");
            }
            case PLAIN -> {
                mailProps.put("mail.transport.protocol", "smtp");
                mailProps.put("mail.smtp.starttls.enable", "false");
                mailProps.put("mail.smtp.ssl.enable", "false");
            }
        }
    }

    public void configureTimeouts(Properties mailProps) {
        String connectionTimeout = System.getProperty("mail.smtp.connectiontimeout", "60000");
        String socketTimeout = System.getProperty("mail.smtp.timeout", "60000");
        String writeTimeout = System.getProperty("mail.smtp.writetimeout", "60000");

        mailProps.put("mail.smtp.connectiontimeout", connectionTimeout);
        mailProps.put("mail.smtp.timeout", socketTimeout);
        mailProps.put("mail.smtp.writetimeout", writeTimeout);
    }

    public String generateEmailBody(String bookTitle) {
        return String.format("""
                Hello,

                You have received a book from Booklore. Please find the attached file titled '%s' for your reading pleasure.

                Thank you for using Booklore! Hope you enjoy your book.
                """, bookTitle);
    }

    public String extractUserFriendlyMessage(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof IOException) {
                return "The email provider rejected or dropped the connection during transfer. This often happens when the attachment exceeds the provider's size limit.";
            }
            cause = cause.getCause();
        }
        if (e instanceof MessagingException) {
            return "The email could not be sent due to a mail server error. Please verify your email provider settings.";
        }
        return "An unexpected error occurred: " + e.getMessage();
    }

    public enum ConnectionType {
        SSL,
        STARTTLS,
        PLAIN
    }
}
