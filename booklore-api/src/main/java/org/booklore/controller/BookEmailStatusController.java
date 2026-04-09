package org.booklore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.entity.BookEmailStatusEntity;
import org.booklore.repository.BookEmailStatusRepository;
import org.booklore.service.email.AutoEmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/email/status")
@Tag(name = "Book Email Status", description = "Endpoints for querying and retrying auto-email status")
public class BookEmailStatusController {

    private final BookEmailStatusRepository bookEmailStatusRepository;
    private final AutoEmailService autoEmailService;
    private final AuthenticationService authenticationService;

    @Operation(summary = "Get email status for books", description = "Returns email send status for the given book IDs for the current user")
    @PreAuthorize("@securityUtil.canEmailBook() or @securityUtil.isAdmin()")
    @GetMapping
    public ResponseEntity<Map<Long, String>> getEmailStatuses(
            @Parameter(description = "Comma-separated book IDs") @RequestParam List<Long> bookIds) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        List<BookEmailStatusEntity> statuses = bookEmailStatusRepository.findByUserIdAndStatusAndBookIdIn(user.getId(), "FAILED", bookIds);
        Map<Long, String> result = statuses.stream()
                .collect(Collectors.toMap(
                        s -> s.getBook().getId(),
                        BookEmailStatusEntity::getStatus
                ));
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Retry auto-email for a book", description = "Retries sending a book via auto-email for the current user")
    @PreAuthorize("@securityUtil.canEmailBook() or @securityUtil.isAdmin()")
    @PostMapping("/retry/{bookId}")
    public ResponseEntity<Void> retryAutoEmail(
            @Parameter(description = "ID of the book to retry") @PathVariable Long bookId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        autoEmailService.retryAutoEmail(bookId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
