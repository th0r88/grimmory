package org.booklore.service.email;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.mapper.EmailRecipientV2Mapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.EmailRecipientV2;
import org.booklore.model.dto.request.CreateEmailRecipientRequest;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.EmailRecipientV2Entity;
import org.booklore.model.enums.AuditAction;
import org.booklore.repository.EmailRecipientV2Repository;
import org.booklore.repository.UserRepository;
import org.booklore.service.audit.AuditService;
import org.springframework.transaction.annotation.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class EmailRecipientV2Service {

    private final EmailRecipientV2Repository repository;
    private final EmailRecipientV2Mapper mapper;
    private final AuthenticationService authService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    private record TargetOwner(Long userId, String username) {
    }

    public List<EmailRecipientV2> getEmailRecipients(Long userId, boolean scopeAll) {
        BookLoreUser user = authService.getAuthenticatedUser();
        if (user.getPermissions().isAdmin()) {
            if (userId != null) {
                BookLoreUserEntity target = userRepository.findById(userId)
                        .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));
                return repository.findAllByUserId(userId).stream()
                        .map(entity -> withOwnerUsername(mapper.toDTO(entity), target.getUsername()))
                        .toList();
            }
            if (scopeAll) {
                List<EmailRecipientV2Entity> allRecipients = repository.findAllByOrderByUserIdAscNameAsc();
                Map<Long, String> usernameByUserId = userRepository.findAllById(
                                allRecipients.stream().map(EmailRecipientV2Entity::getUserId).distinct().toList())
                        .stream()
                        .collect(Collectors.toMap(BookLoreUserEntity::getId, BookLoreUserEntity::getUsername));
                return allRecipients.stream()
                        .map(entity -> withOwnerUsername(mapper.toDTO(entity), usernameByUserId.get(entity.getUserId())))
                        .toList();
            }
        }
        return repository.findAllByUserId(user.getId()).stream()
                .map(mapper::toDTO)
                .toList();
    }

    private static EmailRecipientV2 withOwnerUsername(EmailRecipientV2 dto, String ownerUsername) {
        dto.setOwnerUsername(ownerUsername);
        return dto;
    }

    public EmailRecipientV2 getEmailRecipient(Long id) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailRecipientV2Entity emailRecipient = repository.findByIdAndUserId(id, user.getId()).orElseThrow(() -> ApiError.EMAIL_RECIPIENT_NOT_FOUND.createException(id));
        return mapper.toDTO(emailRecipient);
    }

    @Transactional
    public EmailRecipientV2 createEmailRecipient(CreateEmailRecipientRequest request) {
        TargetOwner target = resolveTargetOwner(request);
        boolean isFirstRecipient = repository.countByUserId(target.userId()) == 0;
        if (request.isDefaultRecipient() || isFirstRecipient) {
            repository.updateAllRecipientsToNonDefault(target.userId());
        }
        EmailRecipientV2Entity entity = mapper.toEntity(request);
        entity.setDefaultRecipient(request.isDefaultRecipient() || isFirstRecipient);
        entity.setUserId(target.userId());
        EmailRecipientV2Entity savedEntity = repository.save(entity);
        auditService.log(AuditAction.EMAIL_RECIPIENT_CREATED, "EmailRecipient", savedEntity.getId(),
                "Created email recipient for user " + target.username() + " (id=" + target.userId() + "): " + savedEntity.getEmail());
        return mapper.toDTO(savedEntity);
    }

    /**
     * Resolves who a recipient in {@code request} belongs to. Branches are total and
     * non-overlapping and must be evaluated in this order:
     * 1. no userId in the request -> the caller
     * 2. userId echoes the caller's own id -> the caller (must not 403)
     * 3. caller is admin -> the validated target user
     * 4. otherwise -> forbidden
     */
    private TargetOwner resolveTargetOwner(CreateEmailRecipientRequest request) {
        BookLoreUser caller = authService.getAuthenticatedUser();
        if (request.getUserId() == null) {
            return new TargetOwner(caller.getId(), caller.getUsername());
        }
        if (request.getUserId().equals(caller.getId())) {
            return new TargetOwner(caller.getId(), caller.getUsername());
        }
        if (caller.getPermissions().isAdmin()) {
            BookLoreUserEntity target = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(request.getUserId()));
            return new TargetOwner(target.getId(), target.getUsername());
        }
        throw ApiError.FORBIDDEN.createException("Cannot assign a recipient to another user");
    }

    private EmailRecipientV2Entity resolveOwnedRecipient(Long id, BookLoreUser user) {
        if (user.getPermissions().isAdmin()) {
            return repository.findById(id).orElseThrow(() -> ApiError.EMAIL_RECIPIENT_NOT_FOUND.createException(id));
        }
        return repository.findByIdAndUserId(id, user.getId()).orElseThrow(() -> ApiError.EMAIL_RECIPIENT_NOT_FOUND.createException(id));
    }

    private String resolveOwnerUsername(Long ownerId, BookLoreUser caller) {
        if (ownerId.equals(caller.getId())) {
            return caller.getUsername();
        }
        return userRepository.findById(ownerId)
                .map(BookLoreUserEntity::getUsername)
                .orElse(String.valueOf(ownerId));
    }

    @Transactional
    public EmailRecipientV2 updateEmailRecipient(Long id, CreateEmailRecipientRequest request) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailRecipientV2Entity existingRecipient = resolveOwnedRecipient(id, user);
        Long ownerId = existingRecipient.getUserId();
        if (request.isDefaultRecipient()) {
            repository.updateAllRecipientsToNonDefault(ownerId);
        }
        mapper.updateEntityFromRequest(request, existingRecipient);
        EmailRecipientV2Entity updatedEntity = repository.save(existingRecipient);
        auditService.log(AuditAction.EMAIL_RECIPIENT_UPDATED, "EmailRecipient", id,
                "Updated email recipient (id=" + id + ") for user id " + ownerId + ": " + updatedEntity.getEmail());
        return mapper.toDTO(updatedEntity);
    }

    @Transactional
    public void setDefaultRecipient(Long id) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailRecipientV2Entity emailRecipient = resolveOwnedRecipient(id, user);
        Long ownerId = emailRecipient.getUserId();
        repository.updateAllRecipientsToNonDefault(ownerId);
        emailRecipient.setDefaultRecipient(true);
        repository.save(emailRecipient);
        auditService.log(AuditAction.EMAIL_RECIPIENT_UPDATED, "EmailRecipient", id,
                "Set default email recipient for user " + resolveOwnerUsername(ownerId, user) + " (id=" + ownerId + "): " + emailRecipient.getEmail());
    }

    @Transactional
    public void deleteEmailRecipient(Long id) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailRecipientV2Entity emailRecipientToDelete = resolveOwnedRecipient(id, user);
        Long ownerId = emailRecipientToDelete.getUserId();
        boolean isDefaultRecipient = emailRecipientToDelete.isDefaultRecipient();
        if (isDefaultRecipient) {
            List<EmailRecipientV2Entity> allRecipients = repository.findAllByUserId(ownerId);
            if (allRecipients.size() > 1) {
                allRecipients.remove(emailRecipientToDelete);
                int randomIndex = ThreadLocalRandom.current().nextInt(allRecipients.size());
                EmailRecipientV2Entity newDefaultRecipient = allRecipients.get(randomIndex);
                newDefaultRecipient.setDefaultRecipient(true);
                repository.save(newDefaultRecipient);
            }
        }
        repository.deleteById(id);
        auditService.log(AuditAction.EMAIL_RECIPIENT_DELETED, "EmailRecipient", id,
                "Deleted email recipient (id=" + id + ") for user id " + ownerId);
    }
}
