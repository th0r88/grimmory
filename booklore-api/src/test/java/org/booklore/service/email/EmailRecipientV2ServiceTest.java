package org.booklore.service.email;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailRecipientV2ServiceTest {

    private static final Long CALLER_ID = 1L;
    private static final Long ADMIN_ID = 2L;
    private static final Long TARGET_ID = 99L;

    @Mock
    private EmailRecipientV2Repository repository;

    @Mock
    private EmailRecipientV2Mapper mapper;

    @Mock
    private AuthenticationService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private EmailRecipientV2Service emailRecipientV2Service;

    private BookLoreUser user;
    private BookLoreUser adminUser;

    @BeforeEach
    void setUp() {
        user = BookLoreUser.builder()
                .id(CALLER_ID)
                .username("testuser")
                .permissions(nonAdminPermissions())
                .build();
        adminUser = BookLoreUser.builder()
                .id(ADMIN_ID)
                .username("admin")
                .permissions(adminPermissions())
                .build();
    }

    private static BookLoreUser.UserPermissions nonAdminPermissions() {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(false);
        return permissions;
    }

    private static BookLoreUser.UserPermissions adminPermissions() {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(true);
        return permissions;
    }

    @Test
    void createEmailRecipient_firstRecipientForUser_becomesDefault() {
        CreateEmailRecipientRequest request = CreateEmailRecipientRequest.builder()
                .email("recipient@test.com")
                .name("Test Recipient")
                .defaultRecipient(false)
                .build();

        EmailRecipientV2Entity mappedEntity = EmailRecipientV2Entity.builder()
                .email("recipient@test.com")
                .name("Test Recipient")
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(repository.countByUserId(CALLER_ID)).thenReturn(0L);
        when(mapper.toEntity(request)).thenReturn(mappedEntity);
        when(repository.save(any())).thenReturn(mappedEntity);
        when(mapper.toDTO(any())).thenReturn(EmailRecipientV2.builder().build());

        emailRecipientV2Service.createEmailRecipient(request);

        ArgumentCaptor<EmailRecipientV2Entity> captor = ArgumentCaptor.forClass(EmailRecipientV2Entity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isDefaultRecipient()).isTrue();
    }

    @Test
    void deleteEmailRecipient_promotesDefaultOnlyWithinSameOwner() {
        EmailRecipientV2Entity toDelete = EmailRecipientV2Entity.builder()
                .id(200L)
                .userId(CALLER_ID)
                .email("primary@test.com")
                .name("Primary")
                .defaultRecipient(true)
                .build();

        EmailRecipientV2Entity sameOwnerRecipient = EmailRecipientV2Entity.builder()
                .id(201L)
                .userId(CALLER_ID)
                .email("secondary@test.com")
                .name("Secondary")
                .defaultRecipient(false)
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByIdAndUserId(200L, CALLER_ID)).thenReturn(Optional.of(toDelete));
        when(repository.findAllByUserId(CALLER_ID)).thenReturn(new ArrayList<>(List.of(toDelete, sameOwnerRecipient)));

        emailRecipientV2Service.deleteEmailRecipient(200L);

        ArgumentCaptor<EmailRecipientV2Entity> captor = ArgumentCaptor.forClass(EmailRecipientV2Entity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(201L);
        assertThat(captor.getValue().isDefaultRecipient()).isTrue();
        verify(repository).deleteById(200L);
    }

    @Test
    void createEmailRecipient_adminAssignsToAnotherUser_ownsRecipientOnTargetUser() {
        CreateEmailRecipientRequest request = CreateEmailRecipientRequest.builder()
                .email("kindle@test.com")
                .name("Kindle")
                .userId(TARGET_ID)
                .build();

        BookLoreUserEntity targetEntity = BookLoreUserEntity.builder().id(TARGET_ID).username("targetuser").build();
        EmailRecipientV2Entity mappedEntity = EmailRecipientV2Entity.builder()
                .email("kindle@test.com")
                .name("Kindle")
                .build();
        EmailRecipientV2Entity savedEntity = EmailRecipientV2Entity.builder()
                .id(500L)
                .userId(TARGET_ID)
                .email("kindle@test.com")
                .name("Kindle")
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(targetEntity));
        when(repository.countByUserId(TARGET_ID)).thenReturn(1L);
        when(mapper.toEntity(request)).thenReturn(mappedEntity);
        when(repository.save(any())).thenReturn(savedEntity);
        when(mapper.toDTO(savedEntity)).thenReturn(EmailRecipientV2.builder().id(500L).userId(TARGET_ID).build());

        emailRecipientV2Service.createEmailRecipient(request);

        ArgumentCaptor<EmailRecipientV2Entity> captor = ArgumentCaptor.forClass(EmailRecipientV2Entity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(TARGET_ID);
    }

    @Test
    void createEmailRecipient_adminAssignsToTargetWithNoRecipients_marksItDefault() {
        CreateEmailRecipientRequest request = CreateEmailRecipientRequest.builder()
                .email("kindle@test.com")
                .name("Kindle")
                .userId(TARGET_ID)
                .build();

        BookLoreUserEntity targetEntity = BookLoreUserEntity.builder().id(TARGET_ID).username("targetuser").build();
        EmailRecipientV2Entity mappedEntity = EmailRecipientV2Entity.builder()
                .email("kindle@test.com")
                .name("Kindle")
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(targetEntity));
        when(repository.countByUserId(TARGET_ID)).thenReturn(0L);
        when(mapper.toEntity(request)).thenReturn(mappedEntity);
        when(repository.save(any())).thenReturn(mappedEntity);
        when(mapper.toDTO(any())).thenReturn(EmailRecipientV2.builder().build());

        emailRecipientV2Service.createEmailRecipient(request);

        ArgumentCaptor<EmailRecipientV2Entity> captor = ArgumentCaptor.forClass(EmailRecipientV2Entity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isDefaultRecipient()).isTrue();
    }

    @Test
    void createEmailRecipient_adminAssignsToTargetUser_undefaultsOnlyTargetsRows() {
        CreateEmailRecipientRequest request = CreateEmailRecipientRequest.builder()
                .email("kindle@test.com")
                .name("Kindle")
                .defaultRecipient(true)
                .userId(TARGET_ID)
                .build();

        BookLoreUserEntity targetEntity = BookLoreUserEntity.builder().id(TARGET_ID).username("targetuser").build();
        EmailRecipientV2Entity mappedEntity = EmailRecipientV2Entity.builder()
                .email("kindle@test.com")
                .name("Kindle")
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(targetEntity));
        when(repository.countByUserId(TARGET_ID)).thenReturn(1L);
        when(mapper.toEntity(request)).thenReturn(mappedEntity);
        when(repository.save(any())).thenReturn(mappedEntity);
        when(mapper.toDTO(any())).thenReturn(EmailRecipientV2.builder().build());

        emailRecipientV2Service.createEmailRecipient(request);

        verify(repository).updateAllRecipientsToNonDefault(TARGET_ID);
        verify(repository, never()).updateAllRecipientsToNonDefault(ADMIN_ID);
    }

    @Test
    void updateEmailRecipient_adminSetsAnotherUsersRecipientAsDefault_undefaultsOnlyThatUsersRows() {
        EmailRecipientV2Entity existing = EmailRecipientV2Entity.builder()
                .id(300L)
                .userId(TARGET_ID)
                .email("old@test.com")
                .name("Old")
                .defaultRecipient(false)
                .build();

        CreateEmailRecipientRequest request = CreateEmailRecipientRequest.builder()
                .email("new@test.com")
                .name("New")
                .defaultRecipient(true)
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(repository.findById(300L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(mapper.toDTO(existing)).thenReturn(EmailRecipientV2.builder().build());

        emailRecipientV2Service.updateEmailRecipient(300L, request);

        verify(repository).updateAllRecipientsToNonDefault(TARGET_ID);
        verify(repository, never()).updateAllRecipientsToNonDefault(ADMIN_ID);
    }

    @Test
    void setDefaultRecipient_adminSetsAnotherUsersRecipient_undefaultsOnlyThatUsersRows() {
        EmailRecipientV2Entity existing = EmailRecipientV2Entity.builder()
                .id(301L)
                .userId(TARGET_ID)
                .email("kindle@test.com")
                .name("Kindle")
                .defaultRecipient(false)
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(repository.findById(301L)).thenReturn(Optional.of(existing));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(BookLoreUserEntity.builder().id(TARGET_ID).username("targetuser").build()));
        when(repository.save(existing)).thenReturn(existing);

        emailRecipientV2Service.setDefaultRecipient(301L);

        verify(repository).updateAllRecipientsToNonDefault(TARGET_ID);
        verify(repository, never()).updateAllRecipientsToNonDefault(ADMIN_ID);
    }

    @Test
    void deleteEmailRecipient_adminDeletesAnotherUsersDefault_promotesRecipientOwnedByThatUser() {
        EmailRecipientV2Entity toDelete = EmailRecipientV2Entity.builder()
                .id(400L)
                .userId(TARGET_ID)
                .email("primary@test.com")
                .name("Primary")
                .defaultRecipient(true)
                .build();

        EmailRecipientV2Entity sameOwnerRecipient = EmailRecipientV2Entity.builder()
                .id(401L)
                .userId(TARGET_ID)
                .email("secondary@test.com")
                .name("Secondary")
                .defaultRecipient(false)
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(repository.findById(400L)).thenReturn(Optional.of(toDelete));
        when(repository.findAllByUserId(TARGET_ID)).thenReturn(new ArrayList<>(List.of(toDelete, sameOwnerRecipient)));

        emailRecipientV2Service.deleteEmailRecipient(400L);

        verify(repository).save(argThat(r -> TARGET_ID.equals(r.getUserId())));
        verify(repository).deleteById(400L);
    }

    @Test
    void createEmailRecipient_nonAdminPostsForeignUserId_rejectedWithForbidden() {
        CreateEmailRecipientRequest request = CreateEmailRecipientRequest.builder()
                .email("kindle@test.com")
                .name("Kindle")
                .userId(TARGET_ID)
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(user);

        assertThatThrownBy(() -> emailRecipientV2Service.createEmailRecipient(request))
                .isInstanceOf(APIException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createEmailRecipient_nonAdminPostsOwnUserIdExplicitly_succeeds() {
        CreateEmailRecipientRequest request = CreateEmailRecipientRequest.builder()
                .email("kindle@test.com")
                .name("Kindle")
                .userId(CALLER_ID)
                .build();

        EmailRecipientV2Entity mappedEntity = EmailRecipientV2Entity.builder()
                .email("kindle@test.com")
                .name("Kindle")
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(repository.countByUserId(CALLER_ID)).thenReturn(1L);
        when(mapper.toEntity(request)).thenReturn(mappedEntity);
        when(repository.save(any())).thenReturn(mappedEntity);
        when(mapper.toDTO(any())).thenReturn(EmailRecipientV2.builder().build());

        emailRecipientV2Service.createEmailRecipient(request);

        ArgumentCaptor<EmailRecipientV2Entity> captor = ArgumentCaptor.forClass(EmailRecipientV2Entity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(CALLER_ID);
    }

    @Test
    void updateEmailRecipient_nonAdminOnAnotherUsersRecipient_notFound() {
        CreateEmailRecipientRequest request = CreateEmailRecipientRequest.builder()
                .email("new@test.com")
                .name("New")
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByIdAndUserId(300L, CALLER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailRecipientV2Service.updateEmailRecipient(300L, request))
                .isInstanceOf(APIException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void setDefaultRecipient_nonAdminOnAnotherUsersRecipient_notFound() {
        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByIdAndUserId(301L, CALLER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailRecipientV2Service.setDefaultRecipient(301L))
                .isInstanceOf(APIException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteEmailRecipient_nonAdminOnAnotherUsersRecipient_notFound() {
        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByIdAndUserId(400L, CALLER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailRecipientV2Service.deleteEmailRecipient(400L))
                .isInstanceOf(APIException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getEmailRecipients_nonAdminWithForeignUserIdFilter_returnsOwnListOnly() {
        EmailRecipientV2Entity ownEntity = EmailRecipientV2Entity.builder().id(1L).userId(CALLER_ID).build();

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findAllByUserId(CALLER_ID)).thenReturn(List.of(ownEntity));
        when(mapper.toDTO(ownEntity)).thenReturn(EmailRecipientV2.builder().id(1L).userId(CALLER_ID).build());

        List<EmailRecipientV2> result = emailRecipientV2Service.getEmailRecipients(TARGET_ID, false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(CALLER_ID);
    }

    @Test
    void getEmailRecipients_nonAdminWithScopeAll_returnsOwnListOnly() {
        EmailRecipientV2Entity ownEntity = EmailRecipientV2Entity.builder().id(1L).userId(CALLER_ID).build();

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findAllByUserId(CALLER_ID)).thenReturn(List.of(ownEntity));
        when(mapper.toDTO(ownEntity)).thenReturn(EmailRecipientV2.builder().id(1L).userId(CALLER_ID).build());

        List<EmailRecipientV2> result = emailRecipientV2Service.getEmailRecipients(null, true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(CALLER_ID);
    }

    @Test
    void getEmailRecipients_adminWithNoParameters_returnsOnlyAdminsOwnRecipients() {
        EmailRecipientV2Entity ownEntity = EmailRecipientV2Entity.builder().id(1L).userId(ADMIN_ID).build();

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(repository.findAllByUserId(ADMIN_ID)).thenReturn(List.of(ownEntity));
        when(mapper.toDTO(ownEntity)).thenReturn(EmailRecipientV2.builder().id(1L).userId(ADMIN_ID).build());

        List<EmailRecipientV2> result = emailRecipientV2Service.getEmailRecipients(null, false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(ADMIN_ID);
    }

    @Test
    void getEmailRecipients_adminScopeAll_returnsRecipientsAcrossOwnersWithOwnerUsername() {
        EmailRecipientV2Entity adminEntity = EmailRecipientV2Entity.builder().id(1L).userId(ADMIN_ID).name("A").build();
        EmailRecipientV2Entity targetEntity = EmailRecipientV2Entity.builder().id(2L).userId(TARGET_ID).name("B").build();

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(repository.findAllByOrderByUserIdAscNameAsc()).thenReturn(List.of(adminEntity, targetEntity));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                BookLoreUserEntity.builder().id(ADMIN_ID).username("admin").build(),
                BookLoreUserEntity.builder().id(TARGET_ID).username("targetuser").build()
        ));
        when(mapper.toDTO(adminEntity)).thenReturn(EmailRecipientV2.builder().id(1L).userId(ADMIN_ID).build());
        when(mapper.toDTO(targetEntity)).thenReturn(EmailRecipientV2.builder().id(2L).userId(TARGET_ID).build());

        List<EmailRecipientV2> result = emailRecipientV2Service.getEmailRecipients(null, true);

        assertThat(result).hasSize(2);
        assertThat(result).filteredOn(dto -> dto.getUserId().equals(ADMIN_ID))
                .extracting(EmailRecipientV2::getOwnerUsername).containsExactly("admin");
        assertThat(result).filteredOn(dto -> dto.getUserId().equals(TARGET_ID))
                .extracting(EmailRecipientV2::getOwnerUsername).containsExactly("targetuser");
    }

    @Test
    void getEmailRecipients_adminWithUserIdFilter_returnsThatUsersRowsWithOwnerUsername() {
        EmailRecipientV2Entity targetEntity = EmailRecipientV2Entity.builder().id(2L).userId(TARGET_ID).build();

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(BookLoreUserEntity.builder().id(TARGET_ID).username("targetuser").build()));
        when(repository.findAllByUserId(TARGET_ID)).thenReturn(List.of(targetEntity));
        when(mapper.toDTO(targetEntity)).thenReturn(EmailRecipientV2.builder().id(2L).userId(TARGET_ID).build());

        List<EmailRecipientV2> result = emailRecipientV2Service.getEmailRecipients(TARGET_ID, false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOwnerUsername()).isEqualTo("targetuser");
    }

    @Test
    void createEmailRecipient_adminTargetsNonexistentUser_userNotFound() {
        CreateEmailRecipientRequest request = CreateEmailRecipientRequest.builder()
                .email("kindle@test.com")
                .name("Kindle")
                .userId(TARGET_ID)
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailRecipientV2Service.createEmailRecipient(request))
                .isInstanceOf(APIException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getEmailRecipients_adminWithNonexistentUserIdFilter_userNotFound() {
        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailRecipientV2Service.getEmailRecipients(TARGET_ID, false))
                .isInstanceOf(APIException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getEmailRecipient_adminRequestsAnotherUsersRecipient_notFound() {
        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(repository.findByIdAndUserId(500L, ADMIN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailRecipientV2Service.getEmailRecipient(500L))
                .isInstanceOf(APIException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createEmailRecipient_adminAssignsToAnotherUser_auditsWithTargetOwnerAndNotLogForUser() {
        CreateEmailRecipientRequest request = CreateEmailRecipientRequest.builder()
                .email("kindle@test.com")
                .name("Kindle")
                .userId(TARGET_ID)
                .build();

        BookLoreUserEntity targetEntity = BookLoreUserEntity.builder().id(TARGET_ID).username("targetuser").build();
        EmailRecipientV2Entity mappedEntity = EmailRecipientV2Entity.builder()
                .email("kindle@test.com")
                .name("Kindle")
                .build();
        EmailRecipientV2Entity savedEntity = EmailRecipientV2Entity.builder()
                .id(600L)
                .userId(TARGET_ID)
                .email("kindle@test.com")
                .name("Kindle")
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(targetEntity));
        when(repository.countByUserId(TARGET_ID)).thenReturn(1L);
        when(mapper.toEntity(request)).thenReturn(mappedEntity);
        when(repository.save(any())).thenReturn(savedEntity);
        when(mapper.toDTO(savedEntity)).thenReturn(EmailRecipientV2.builder().id(600L).userId(TARGET_ID).build());

        emailRecipientV2Service.createEmailRecipient(request);

        verify(auditService).log(eq(AuditAction.EMAIL_RECIPIENT_CREATED), eq("EmailRecipient"), any(), contains("targetuser"));
        verify(auditService, never()).logForUser(any(), any(), any(), any(), any(), any());
    }

    @Test
    void setDefaultRecipient_adminSetsAnotherUsersRecipient_auditsWithTargetOwnerAndNotLogForUser() {
        EmailRecipientV2Entity existing = EmailRecipientV2Entity.builder()
                .id(700L)
                .userId(TARGET_ID)
                .email("kindle@test.com")
                .name("Kindle")
                .defaultRecipient(false)
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(repository.findById(700L)).thenReturn(Optional.of(existing));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(BookLoreUserEntity.builder().id(TARGET_ID).username("targetuser").build()));
        when(repository.save(existing)).thenReturn(existing);

        emailRecipientV2Service.setDefaultRecipient(700L);

        verify(auditService).log(eq(AuditAction.EMAIL_RECIPIENT_UPDATED), eq("EmailRecipient"), any(), contains("targetuser"));
        verify(auditService, never()).logForUser(any(), any(), any(), any(), any(), any());
    }
}
