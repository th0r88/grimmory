package org.booklore.mapper;

import org.booklore.model.dto.request.CreateEmailRecipientRequest;
import org.booklore.model.entity.EmailRecipientV2Entity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailRecipientV2MapperTest {

    private final EmailRecipientV2Mapper mapper = new EmailRecipientV2MapperImpl();

    @Test
    void updateEntityFromRequest_nullUserId_leavesExistingOwnerUnchanged() {
        CreateEmailRecipientRequest request = CreateEmailRecipientRequest.builder()
                .email("recipient@test.com")
                .name("Test Recipient")
                .userId(null)
                .build();
        EmailRecipientV2Entity entity = EmailRecipientV2Entity.builder()
                .userId(5L)
                .build();

        mapper.updateEntityFromRequest(request, entity);

        assertThat(entity.getUserId()).isEqualTo(5L);
    }

    @Test
    void updateEntityFromRequest_foreignUserId_leavesExistingOwnerUnchanged() {
        CreateEmailRecipientRequest request = CreateEmailRecipientRequest.builder()
                .email("recipient@test.com")
                .name("Test Recipient")
                .userId(999L)
                .build();
        EmailRecipientV2Entity entity = EmailRecipientV2Entity.builder()
                .userId(5L)
                .build();

        mapper.updateEntityFromRequest(request, entity);

        assertThat(entity.getUserId()).isEqualTo(5L);
    }

    @Test
    void toEntity_fromCreateRequest_ignoresUserId() {
        CreateEmailRecipientRequest request = CreateEmailRecipientRequest.builder()
                .email("recipient@test.com")
                .name("Test Recipient")
                .userId(9L)
                .build();

        EmailRecipientV2Entity entity = mapper.toEntity(request);

        assertThat(entity.getUserId()).isNull();
    }
}
