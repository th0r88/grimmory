-- Global admin toggle for auto-emailing books when added to a library
INSERT INTO app_settings (name, val)
VALUES ('auto_email_on_book_add', 'false');

-- Track email send status per book per user
CREATE TABLE book_email_status
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id       BIGINT       NOT NULL,
    user_id       BIGINT       NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    error_message VARCHAR(500) NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_book_email_status_book_user (book_id, user_id),
    CONSTRAINT fk_book_email_status_book FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE,
    CONSTRAINT fk_book_email_status_user FOREIGN KEY (user_id) REFERENCES book_lore_user (id) ON DELETE CASCADE
);
