package com.growit.auth.oauth.repository;

import com.growit.auth.oauth.entity.SocialAccount;
import com.growit.auth.oauth.entity.SocialProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

	Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

	void deleteByUserId(Long userId);
}
