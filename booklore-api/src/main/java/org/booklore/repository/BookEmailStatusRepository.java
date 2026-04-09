package org.booklore.repository;

import org.booklore.model.entity.BookEmailStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookEmailStatusRepository extends JpaRepository<BookEmailStatusEntity, Long> {

    Optional<BookEmailStatusEntity> findByBookIdAndUserId(Long bookId, Long userId);

    List<BookEmailStatusEntity> findByUserIdAndStatusAndBookIdIn(Long userId, String status, Collection<Long> bookIds);
}
