package org.booklore.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.settings.AppSettingKey;
import org.booklore.model.dto.settings.UserSettingKey;
import org.booklore.model.entity.*;
import org.booklore.model.websocket.LogNotification;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.*;
import org.booklore.service.NotificationService;
import org.booklore.service.appsettings.AppSettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoEmailService {

    private final AppSettingService appSettingService;
    private final UserSettingRepository userSettingRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final UserEmailProviderPreferenceRepository preferenceRepository;
    private final EmailProviderV2Repository emailProviderRepository;
    private final EmailRecipientV2Repository emailRecipientRepository;
    private final BookEmailStatusRepository bookEmailStatusRepository;
    private final EmailSenderHelper emailSenderHelper;
    private final NotificationService notificationService;
    private final Executor taskExecutor;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoEmailBookToEligibleUsers(Long bookId, Long libraryId) {
        if (!appSettingService.getAppSettings().isAutoEmailOnBookAdd()) {
            log.debug("Auto-email on book add is disabled globally");
            return;
        }

        BookEntity book = bookRepository.findByIdWithBookFiles(bookId).orElse(null);
        if (book == null) {
            log.warn("Book {} not found for auto-email", bookId);
            return;
        }

        BookFileEntity bookFile = book.getPrimaryBookFile();
        if (bookFile == null) {
            log.debug("Book {} has no primary file, skipping auto-email", bookId);
            return;
        }

        List<Long> optedInUserIds = findOptedInUserIds();
        if (optedInUserIds.isEmpty()) {
            log.debug("No users opted in for auto-email on book add");
            return;
        }

        for (Long userId : optedInUserIds) {
            BookLoreUserEntity userEntity = userRepository.findByIdWithLibraries(userId).orElse(null);
            if (userEntity == null) {
                continue;
            }

            if (!hasLibraryAccess(userEntity, libraryId)) {
                log.debug("User {} does not have access to library {}, skipping auto-email", userId, libraryId);
                continue;
            }

            sendAutoEmailForUser(userEntity, book, bookFile);
        }
    }

    public void retryAutoEmail(Long bookId, Long userId) {
        BookEntity book = bookRepository.findByIdWithBookFiles(bookId).orElse(null);
        if (book == null) {
            log.warn("Book {} not found for auto-email retry", bookId);
            return;
        }

        BookFileEntity bookFile = book.getPrimaryBookFile();
        if (bookFile == null) {
            log.warn("Book {} has no primary file for auto-email retry", bookId);
            return;
        }

        BookLoreUserEntity userEntity = userRepository.findByIdWithLibraries(userId).orElse(null);
        if (userEntity == null) {
            log.warn("User {} not found for auto-email retry", userId);
            return;
        }

        sendAutoEmailForUser(userEntity, book, bookFile);
    }

    private List<Long> findOptedInUserIds() {
        return userSettingRepository
                .findBySettingKeyAndSettingValue(UserSettingKey.AUTO_EMAIL_ON_BOOK_ADD.getDbKey(), "true")
                .stream()
                .map(UserSettingEntity::getUserId)
                .toList();
    }

    private boolean hasLibraryAccess(BookLoreUserEntity user, Long libraryId) {
        if (user.getPermissions() != null && user.getPermissions().isPermissionAdmin()) {
            return true;
        }
        return user.getLibraries().stream()
                .anyMatch(lib -> lib.getId().equals(libraryId));
    }

    private void sendAutoEmailForUser(BookLoreUserEntity userEntity, BookEntity book, BookFileEntity bookFile) {
        Long userId = userEntity.getId();
        String username = userEntity.getUsername();
        String bookTitle = book.getMetadata().getTitle();

        Optional<Long> defaultProviderId = preferenceRepository.findByUserId(userId)
                .map(UserEmailProviderPreferenceEntity::getDefaultProviderId);
        if (defaultProviderId.isEmpty()) {
            log.debug("User {} has no default email provider, skipping auto-email for book {}", userId, book.getId());
            return;
        }

        Optional<EmailProviderV2Entity> provider = emailProviderRepository.findAccessibleProvider(defaultProviderId.get(), userId);
        if (provider.isEmpty()) {
            log.debug("Default email provider not accessible for user {}, skipping auto-email for book {}", userId, book.getId());
            return;
        }

        Optional<EmailRecipientV2Entity> recipient = emailRecipientRepository.findDefaultEmailRecipientByUserId(userId);
        if (recipient.isEmpty()) {
            List<EmailRecipientV2Entity> allRecipients = emailRecipientRepository.findAllByUserId(userId);
            if (allRecipients.size() == 1) {
                recipient = Optional.of(allRecipients.getFirst());
                log.debug("User {} has no default recipient, falling back to sole recipient", userId);
            } else {
                log.debug("User {} has no default email recipient and {} total recipients, skipping auto-email for book {}", userId, allRecipients.size(), book.getId());
                return;
            }
        }

        String recipientEmail = recipient.get().getEmail();
        log.info("Auto-emailing book '{}' to {} for user {}", bookTitle, recipientEmail, username);

        taskExecutor.execute(() -> {
            try {
                emailSenderHelper.sendEmail(provider.get(), recipientEmail, book, bookFile);
                upsertStatus(book, userEntity, "SENT", null);
                notificationService.sendMessageToUser(username, Topic.LOG,
                        LogNotification.info("Auto-email: Book '" + bookTitle + "' sent to " + recipientEmail));
                log.info("Auto-email succeeded for book '{}' to user {}", bookTitle, username);
            } catch (Exception e) {
                String errorMsg = emailSenderHelper.extractUserFriendlyMessage(e);
                upsertStatus(book, userEntity, "FAILED", errorMsg);
                notificationService.sendMessageToUser(username, Topic.LOG,
                        LogNotification.error("Auto-email failed for book '" + bookTitle + "': " + errorMsg));
                log.error("Auto-email failed for book '{}' to user {}: {}", bookTitle, username, e.getMessage(), e);
            }
        });
    }

    private void upsertStatus(BookEntity book, BookLoreUserEntity user, String status, String errorMessage) {
        BookEmailStatusEntity entity = bookEmailStatusRepository.findByBookIdAndUserId(book.getId(), user.getId())
                .orElseGet(() -> BookEmailStatusEntity.builder()
                        .book(book)
                        .user(user)
                        .build());
        entity.setStatus(status);
        entity.setErrorMessage(errorMessage);
        bookEmailStatusRepository.save(entity);
    }
}
