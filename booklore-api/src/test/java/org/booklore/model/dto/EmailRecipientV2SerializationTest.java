package org.booklore.model.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the wire shape of {@link EmailRecipientV2}.
 * <p>
 * {@code ownerUsername} is populated only on the two admin list paths. Every other response —
 * the non-admin list, the single GET, and the POST/PUT mutation responses — leaves it null, and
 * those bodies must stay byte-identical to what they returned before the field was introduced.
 * Without {@code @JsonInclude(NON_NULL)} on the DTO, Spring's default inclusion policy is
 * {@code ALWAYS} and every one of those responses would newly carry {@code "ownerUsername": null}.
 */
class EmailRecipientV2SerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serialize_ownerUsernameNull_omitsTheFieldEntirely() throws Exception {
        EmailRecipientV2 recipient = EmailRecipientV2.builder()
                .id(1L)
                .userId(7L)
                .email("reader@kindle.com")
                .name("Kindle")
                .defaultRecipient(true)
                .build();

        String json = objectMapper.writeValueAsString(recipient);

        assertThat(json).doesNotContain("ownerUsername");
    }

    @Test
    void serialize_ownerUsernamePresent_includesTheField() throws Exception {
        EmailRecipientV2 recipient = EmailRecipientV2.builder()
                .id(1L)
                .userId(7L)
                .ownerUsername("reader")
                .email("reader@kindle.com")
                .name("Kindle")
                .defaultRecipient(true)
                .build();

        String json = objectMapper.writeValueAsString(recipient);

        assertThat(json).contains("\"ownerUsername\":\"reader\"");
    }

    @Test
    void serialize_ownerUsernameNull_keepsEveryOtherFieldOnTheWire() throws Exception {
        EmailRecipientV2 recipient = EmailRecipientV2.builder()
                .id(1L)
                .userId(7L)
                .email("reader@kindle.com")
                .name("Kindle")
                .defaultRecipient(false)
                .build();

        String json = objectMapper.writeValueAsString(recipient);

        assertThat(json).contains("\"id\":1")
                .contains("\"userId\":7")
                .contains("\"email\":\"reader@kindle.com\"")
                .contains("\"name\":\"Kindle\"")
                .contains("\"defaultRecipient\":false");
    }
}
