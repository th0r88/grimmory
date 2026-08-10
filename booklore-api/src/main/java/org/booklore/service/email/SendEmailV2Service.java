package org.booklore.service.email;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.SendBookByEmailRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.EmailProviderV2Entity;
import org.booklore.model.entity.EmailRecipientV2Entity;
import org.booklore.model.entity.UserEmailProviderPreferenceEntity;
import org.booklore.model.websocket.LogNotification;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.BookRepository;
import org.booklore.repository.EmailProviderV2Repository;
import org.booklore.repository.EmailRecipientV2Repository;
import org.booklore.repository.UserEmailProviderPreferenceRepository;
import org.booklore.service.NotificationService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.audit.AuditService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

@Slf4j
@Service
@AllArgsConstructor
public class SendEmailV2Service {

    private final EmailProviderV2Repository emailProviderRepository;
    private final UserEmailProviderPreferenceRepository preferenceRepository;
    private final BookRepository bookRepository;
    private final EmailRecipientV2Repository emailRecipientRepository;
    private final NotificationService notificationService;
    private final AuthenticationService authenticationService;
    private final AuditService auditService;
    private final Executor taskExecutor;
    private final EmailSenderHelper emailSenderHelper;

    public void emailBookQuick(Long bookId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        BookEntity book = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        EmailProviderV2Entity defaultEmailProvider = getDefaultEmailProvider();
        EmailRecipientV2Entity defaultEmailRecipient = emailRecipientRepository.findDefaultEmailRecipientByUserId(user.getId()).orElseThrow(ApiError.DEFAULT_EMAIL_RECIPIENT_NOT_FOUND::createException);
        BookFileEntity bookFile = book.getPrimaryBookFile();
        sendEmailInVirtualThread(defaultEmailProvider, defaultEmailRecipient.getEmail(), book, bookFile);
    }

    public void emailBook(SendBookByEmailRequest request) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        EmailProviderV2Entity emailProvider = emailProviderRepository.findByIdAndUserId(request.getProviderId(), user.getId())
                .orElseGet(() ->
                        emailProviderRepository.findSharedProviderById(request.getProviderId())
                                .orElseThrow(() -> ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(request.getProviderId()))
                );
        BookEntity book = bookRepository.findByIdWithBookFiles(request.getBookId()).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(request.getBookId()));
        EmailRecipientV2Entity emailRecipient = emailRecipientRepository.findByIdAndUserId(request.getRecipientId(), user.getId()).orElseThrow(() -> ApiError.EMAIL_RECIPIENT_NOT_FOUND.createException(request.getRecipientId()));
        BookFileEntity bookFile = resolveBookFile(book, request.getBookFileId());
        sendEmailInVirtualThread(emailProvider, emailRecipient.getEmail(), book, bookFile);
    }

    private void sendEmailInVirtualThread(EmailProviderV2Entity emailProvider, String recipientEmail, BookEntity book, BookFileEntity bookFile) {
        String bookTitle = book.getMetadata().getTitle();
        String logMessage = "Email dispatch initiated for book: " + bookTitle + " to " + recipientEmail;
        notificationService.sendMessage(Topic.LOG, LogNotification.info(logMessage));
        log.info(logMessage);
        taskExecutor.execute(() -> {
            try {
                emailSenderHelper.sendEmail(emailProvider, recipientEmail, book, bookFile);
                auditService.log(AuditAction.BOOK_SENT, "Book", book.getId(), "Sent book: " + bookTitle + " to " + recipientEmail);
                String successMessage = "The book: " + bookTitle + " has been successfully sent to " + recipientEmail;
                notificationService.sendMessage(Topic.LOG, LogNotification.info(successMessage));
                log.info(successMessage);
            } catch (Exception e) {
                String userMessage = "Failed to send book: " + bookTitle + " to " + recipientEmail + ". " + emailSenderHelper.extractUserFriendlyMessage(e);
                notificationService.sendMessage(Topic.LOG, LogNotification.error(userMessage));
                log.error("Email send failed for book '{}' to {}: {}", bookTitle, recipientEmail, e.getMessage(), e);
            }
        });
    }

    private BookFileEntity resolveBookFile(BookEntity book, Long bookFileId) {
        if (bookFileId == null) {
            return book.getPrimaryBookFile();
        }
        return book.getBookFiles().stream()
                .filter(bf -> bf.getId().equals(bookFileId))
                .findFirst()
                .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException(bookFileId));
    }

    private EmailProviderV2Entity getDefaultEmailProvider() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();

        Optional<Long> defaultProviderId = preferenceRepository.findByUserId(user.getId())
                .map(UserEmailProviderPreferenceEntity::getDefaultProviderId);

        if (defaultProviderId.isEmpty()) {
            List<EmailProviderV2Entity> accessibleProviders = new ArrayList<>(emailProviderRepository.findAllByUserId(user.getId()));
            accessibleProviders.addAll(emailProviderRepository.findAllSharedByOtherAdmins(user.getId()));
            if (accessibleProviders.size() == 1) {
                return accessibleProviders.getFirst();
            }
            throw ApiError.DEFAULT_EMAIL_PROVIDER_NOT_FOUND.createException();
        }

        return emailProviderRepository.findAccessibleProvider(defaultProviderId.get(), user.getId())
                .orElseThrow(ApiError.DEFAULT_EMAIL_PROVIDER_NOT_FOUND::createException);
    }
}
