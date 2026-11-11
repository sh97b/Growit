package com.growit.auth.oauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.oauth")
public class OauthProperties {

	private final Provider kakao = new Provider();
	private final Provider google = new Provider();
	private String redirectBaseUrl = "http://localhost:8080";
	private String frontendBaseUrl = "http://localhost:5173";

	public Provider getKakao() {
		return kakao;
	}

	public Provider getGoogle() {
		return google;
	}

	public String getRedirectBaseUrl() {
		return redirectBaseUrl;
	}

	public void setRedirectBaseUrl(String redirectBaseUrl) {
		this.redirectBaseUrl = redirectBaseUrl;
	}

	public String getFrontendBaseUrl() {
		return frontendBaseUrl;
	}

	public void setFrontendBaseUrl(String frontendBaseUrl) {
		this.frontendBaseUrl = frontendBaseUrl;
	}

	/** 제공자별로 id·secret이 비어 있으면 그 제공자는 꺼짐이다(서버는 정상 시작). */
	public static class Provider {
		private String clientId = "";
		private String clientSecret = "";

		public String getClientId() {
			return clientId;
		}

		public void setClientId(String clientId) {
			this.clientId = clientId;
		}

		public String getClientSecret() {
			return clientSecret;
		}

		public void setClientSecret(String clientSecret) {
			this.clientSecret = clientSecret;
		}

		public boolean isEnabled() {
			return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
		}
	}
}
