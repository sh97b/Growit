package com.growit;

import static org.assertj.core.api.Assertions.assertThat;

import com.growit.ai.config.AiProperties;
import com.growit.auth.oauth.config.OauthProperties;
import com.growit.notification.config.WebPushProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 전체 스프링 컨텍스트(모든 빈)가 실제로 뜨는지 확인한다. 생성자가 둘 이상인 빈에 {@code @Autowired}가
 * 빠지면(어떤 생성자를 쓸지 모호해지면) 컨텍스트 로딩 자체가 실패하는데, 개별 단위 테스트는 이를 잡지 못한다
 * (실제로 OauthStateService 등에서 이 문제로 prod 서버가 기동 실패한 사고가 있었다).
 */
class GrowitApplicationContextTest {

	/** 소셜·푸시·AI 설정이 모두 비어 있는 서버 기본값 상태(test 프로필의 기본 application.yml 그대로). */
	@SpringBootTest
	@Nested
	class WithAllOptionalFeaturesDisabled {

		@Autowired
		private AiProperties aiProperties;
		@Autowired
		private OauthProperties oauthProperties;
		@Autowired
		private WebPushProperties webPushProperties;

		@Test
		void contextLoadsWithEverythingDisabled() {
			assertThat(aiProperties.isEnabled()).isFalse();
			assertThat(oauthProperties.getKakao().isEnabled()).isFalse();
			assertThat(oauthProperties.getGoogle().isEnabled()).isFalse();
			assertThat(webPushProperties.isEnabled()).isFalse();
		}
	}

	/** 카카오·구글·푸시·AI 설정이 모두 채워진 상태(가짜 값). */
	@SpringBootTest(properties = {
			"app.ai.api-key=test-ai-key",
			"app.ai.model=gpt-test",
			"app.oauth.kakao.client-id=test-kakao-id",
			"app.oauth.kakao.client-secret=test-kakao-secret",
			"app.oauth.google.client-id=test-google-id",
			"app.oauth.google.client-secret=test-google-secret",
			"app.web-push.public-key=test-public-key",
			"app.web-push.private-key=test-private-key",
			"app.web-push.subject=mailto:test@example.com"
	})
	@Nested
	class WithAllOptionalFeaturesEnabled {

		@Autowired
		private AiProperties aiProperties;
		@Autowired
		private OauthProperties oauthProperties;
		@Autowired
		private WebPushProperties webPushProperties;

		@Test
		void contextLoadsWithEverythingEnabled() {
			assertThat(aiProperties.isEnabled()).isTrue();
			assertThat(oauthProperties.getKakao().isEnabled()).isTrue();
			assertThat(oauthProperties.getGoogle().isEnabled()).isTrue();
			assertThat(webPushProperties.isEnabled()).isTrue();
		}
	}

	/**
	 * prod 프로필로도 컨텍스트가 뜨는지 확인한다. application-prod.yml이 따로 없어 datasource·Flyway는
	 * test 프로필의 H2 설정(src/test/resources/application.yml, flyway.enabled=false)을 그대로 쓴다 —
	 * 실제 MySQL·Flyway 마이그레이션 적용까지는 이 테스트로 검증하지 못하고, 빈 구성(생성자 모호성 등)이
	 * 프로필과 무관하게 올바른지만 확인한다.
	 */
	@SpringBootTest
	@ActiveProfiles("prod")
	@Nested
	class WithProdProfile {

		@Test
		void contextLoadsWithProdProfile() {
			// 컨텍스트 로딩 실패 시 이 테스트 클래스 자체가 실패한다.
		}
	}
}
