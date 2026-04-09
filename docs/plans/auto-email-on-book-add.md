# Auto-Email Books on Library Addition

## Context

When a book is added to a library (via BookDrop, library scan, or file watcher), users want it automatically emailed to their default recipient using their default email provider. This requires a global admin toggle + per-user opt-in. On SMTP failure, a WebSocket notification and book card indicator should inform the user.

## Data Flow

```
BookAddedEvent → BookAddedEventListener (async, after commit)
  → AutoEmailService.autoEmailBookToEligibleUsers(bookId, libraryId)
    → check global toggle (AppSettingKey)
    → find opted-in users (UserSettingKey)
    → filter by library access
    → for each user: resolve default provider + recipient
      → skip silently if either missing
      → send via EmailSenderHelper (virtual thread)
      → on success: LogNotification.info + record SENT in book_email_status
      → on failure: LogNotification.error + record FAILED in book_email_status
```

## Implementation Steps

### 1. Flyway Migration V137

**New:** `booklore-api/src/main/resources/db/migration/V137__Add_auto_email_on_book_add.sql`

- Insert default `app_settings` row: `auto_email_on_book_add = false`
- Create `book_email_status` table: `(id, book_id, user_id, status VARCHAR(20), error_message VARCHAR(500), created_at)` with unique constraint on `(book_id, user_id)` and foreign keys to `book` and `book_lore_user` with `ON DELETE CASCADE`

### 2. Backend Settings

**Modify `AppSettingKey.java`** — add `AUTO_EMAIL_ON_BOOK_ADD("auto_email_on_book_add", false, false, List.of(PermissionType.ADMIN))`

**Modify `AppSettings.java`** — add `boolean autoEmailOnBookAdd` field

**Modify `AppSettingService.java`** — wire it in `buildAppSettings()` like other boolean toggles

**Modify `UserSettingKey.java`** — add `AUTO_EMAIL_ON_BOOK_ADD("autoEmailOnBookAdd", false)`

### 3. Extract EmailSenderHelper

**New:** `booklore-api/src/main/java/org/booklore/service/email/EmailSenderHelper.java`

Extract from `SendEmailV2Service`: `sendEmail()`, `setupMailSender()`, `determineConnectionType()`, `configureConnectionType()`, `configureTimeouts()`, `generateEmailBody()`, `extractUserFriendlyMessage()`. Make it a `@Component`.

**Modify `SendEmailV2Service.java`** — delegate to `EmailSenderHelper` instead of private methods.

### 4. BookEmailStatus Entity + Repository

**New:** `booklore-api/src/main/java/org/booklore/model/entity/BookEmailStatusEntity.java` — JPA entity for `book_email_status`

**New:** `booklore-api/src/main/java/org/booklore/repository/BookEmailStatusRepository.java`
- `findByBookIdAndUserId(Long, Long)`
- `findByUserIdAndStatusAndBookIdIn(Long, String, Collection<Long>)` — for batch loading failed statuses on book cards

### 5. AutoEmailService

**New:** `booklore-api/src/main/java/org/booklore/service/email/AutoEmailService.java`

Core method: `autoEmailBookToEligibleUsers(Long bookId, Long libraryId)`
1. Check `AppSettingKey.AUTO_EMAIL_ON_BOOK_ADD` global toggle
2. Query users with `UserSettingKey.AUTO_EMAIL_ON_BOOK_ADD = true`
3. Filter to users with library access (admin or assigned)
4. For each: resolve default provider via `UserEmailProviderPreferenceRepository` + `EmailProviderV2Repository.findAccessibleProvider()`, resolve default recipient via `EmailRecipientV2Repository.findDefaultEmailRecipientByUserId()` — skip silently if either missing
5. Dispatch email via `EmailSenderHelper` in virtual thread
6. On success: `notificationService.sendMessageToUser()` with `LogNotification.info()`, upsert `book_email_status` as SENT
7. On failure: `notificationService.sendMessageToUser()` with `LogNotification.error()`, upsert `book_email_status` as FAILED

Key: no authenticated user context needed — accepts IDs directly, uses `sendMessageToUser(username, ...)`.

### 6. Hook into BookAddedEventListener

**Modify `BookAddedEventListener.java`** — inject `AutoEmailService`, call `autoEmailService.autoEmailBookToEligibleUsers(book.getId(), book.getLibraryId())` after existing Kobo shelf call.

### 7. Book Card Failed-Send Indicator (Backend)

**New:** `booklore-api/src/main/java/org/booklore/controller/BookEmailStatusController.java`
- `GET /api/v1/email/status?bookIds=1,2,3` — returns `Map<Long, String>` of bookId→status for current user
- `POST /api/v1/email/retry/{bookId}` — retries auto-email for one book for current user
- Both require `@PreAuthorize("@securityUtil.canEmailBook() or @securityUtil.isAdmin()")`

### 8. Frontend — Admin Global Toggle

**Modify `app-settings.model.ts`** — add `autoEmailOnBookAdd: boolean` to `AppSettings` interface

**Modify `global-preferences.component.html`** — add `p-toggleswitch` for auto-email (same pattern as `autoBookSearch` toggle)

**Modify `global-preferences.component.ts`** — wire toggle to save `AUTO_EMAIL_ON_BOOK_ADD` setting

### 9. Frontend — Per-User Toggle in Email Settings

**Modify `email-v2.component.html`** — add `p-toggleswitch` at top of email settings page before provider/recipient sections

**Modify `email-v2.component.ts`** — load/save `autoEmailOnBookAdd` user setting

### 10. Frontend — Book Card Failed-Send Indicator

**New:** `frontend/src/app/features/settings/email-v2/email-status.service.ts` — service to call `GET /api/v1/email/status` and `POST /api/v1/email/retry/{bookId}`

**Modify `book-card.component.html`** — add conditional red envelope icon with tooltip when send failed

**Modify `book-card.component.ts`** — check email status for displayed books (batch request on load)

### 11. Translations

Add keys to all language files in `frontend/src/i18n/`:
- Global preferences: auto-email toggle label + description
- Email settings: per-user auto-send toggle label + description  
- Book card: email send failed tooltip, retry label

## Files Summary

| Action | File |
|--------|------|
| New | `booklore-api/src/main/resources/db/migration/V137__Add_auto_email_on_book_add.sql` |
| New | `booklore-api/src/main/java/org/booklore/service/email/EmailSenderHelper.java` |
| New | `booklore-api/src/main/java/org/booklore/service/email/AutoEmailService.java` |
| New | `booklore-api/src/main/java/org/booklore/model/entity/BookEmailStatusEntity.java` |
| New | `booklore-api/src/main/java/org/booklore/repository/BookEmailStatusRepository.java` |
| New | `booklore-api/src/main/java/org/booklore/controller/BookEmailStatusController.java` |
| New | `frontend/src/app/features/settings/email-v2/email-status.service.ts` |
| Modify | `booklore-api/.../settings/AppSettingKey.java` |
| Modify | `booklore-api/.../settings/AppSettings.java` |
| Modify | `booklore-api/.../appsettings/AppSettingService.java` |
| Modify | `booklore-api/.../settings/UserSettingKey.java` |
| Modify | `booklore-api/.../email/SendEmailV2Service.java` |
| Modify | `booklore-api/.../event/BookAddedEventListener.java` |
| Modify | `frontend/.../global-preferences/global-preferences.component.{ts,html}` |
| Modify | `frontend/.../email-v2/email-v2.component.{ts,html}` |
| Modify | `frontend/.../book-card/book-card.component.{ts,html}` |
| Modify | `frontend/src/i18n/*.json` (all languages) |

## Verification

1. **Backend tests:** Write unit test for `AutoEmailService` — mock repositories, verify eligible user filtering, verify silent skip when no provider/recipient, verify status recording on success/failure
2. Run `just api test` for full backend suite
3. **Frontend:** Run `just ui check` for lint + typecheck + tests
4. **Manual E2E:** Enable global toggle as admin → enable per-user toggle → add book via BookDrop → verify email received and LogNotification shown → verify book card shows no error indicator. Then break SMTP config → add another book → verify error notification and red indicator on card → use retry button → verify it works after fixing config
