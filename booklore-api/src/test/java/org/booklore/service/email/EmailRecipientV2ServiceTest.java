package org.booklore.service.email;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.EmailRecipientV2Mapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.CreateEmailRecipientRequest;
import org.booklore.model.entity.EmailRecipientV2Entity;
import org.booklore.repository.EmailRecipientV2Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailRecipientV2ServiceTest {

    @Mock
    private EmailRecipientV2Repository repository;

    @Mock
    private EmailRecipientV2Mapper mapper;

    @Mock
    private AuthenticationService authService;

    @InjectMocks
    private EmailRecipientV2Service emailRecipientV2Service;

    private BookLoreUser user;

    @BeforeEach
    void setUp() {
        user = BookLoreUser.builder().id(1L).username("testuser").build();
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
        when(repository.countByUserId(1L)).thenReturn(0L);
        when(mapper.toEntity(request)).thenReturn(mappedEntity);

        emailRecipientV2Service.createEmailRecipient(request);

        ArgumentCaptor<EmailRecipientV2Entity> captor = ArgumentCaptor.forClass(EmailRecipientV2Entity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isDefaultRecipient()).isTrue();
    }
}
