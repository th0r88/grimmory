package org.booklore.repository;

import org.booklore.model.entity.UserSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserSettingRepository extends JpaRepository<UserSettingEntity, Long> {
    long countBySettingKeyAndSettingValue(String settingKey, String settingValue);

    List<UserSettingEntity> findBySettingKeyAndSettingValue(String settingKey, String settingValue);
}
