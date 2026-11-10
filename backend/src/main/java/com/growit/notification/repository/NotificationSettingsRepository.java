package com.growit.notification.repository;

import com.growit.notification.entity.NotificationSettings;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Long> {

	Optional<NotificationSettings> findByUserId(Long userId);

	List<NotificationSettings> findByEnabledTrue();

	void deleteByUserId(Long userId);
}
