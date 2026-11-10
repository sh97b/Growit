package com.growit.notification.repository;

import com.growit.notification.entity.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

	List<PushSubscription> findByUserId(Long userId);

	Optional<PushSubscription> findByUserIdAndEndpoint(Long userId, String endpoint);

	Optional<PushSubscription> findByEndpoint(String endpoint);

	void deleteByUserId(Long userId);

	void deleteByUserIdAndEndpoint(Long userId, String endpoint);

	void deleteByEndpoint(String endpoint);
}
