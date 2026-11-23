package com.growit.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

	private String apiKey = "";
	private String model = "";
	/** openai(기본) 또는 xai. 그 밖의 값은 openai로 본다. 요청·응답 형식은 둘 다 OpenAI 호환이라 주소와 로그 표기만 다르다. */
	private String provider = "openai";
	/** 비어 있으면 업체의 기본 주소를 쓴다. */
	private String baseUrl = "";
	private int timeoutSeconds = 20;
	private int maxOutputTokens = 1500;
	private int maxConcurrency = 2;
	private int userDailyLimit = 5;
	private int globalDailyLimit = 100;
	private int analysisUserDailyLimit = 30;
	private int simplifyUserDailyLimit = 5;
	private int viewUserDailyLimit = 20;
	private int autoUserDailyLimit = 1;
	private int roadmapUserDailyLimit = 10;

	/** 키나 모델이 비어 있으면 AI 기능 전체가 꺼진 상태다. 이때도 서버는 정상적으로 시작한다. */
	public boolean isEnabled() {
		return apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank();
	}

	public static final String PROVIDER_OPENAI = "openai";
	public static final String PROVIDER_XAI = "xai";

	public String getProvider() {
		return provider != null && PROVIDER_XAI.equalsIgnoreCase(provider.trim()) ? PROVIDER_XAI : PROVIDER_OPENAI;
	}

	/** 설정된 원래 값(알 수 없는 값 경고용). */
	public String getRawProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getBaseUrl() {
		if (baseUrl != null && !baseUrl.isBlank()) {
			return baseUrl.trim();
		}
		return PROVIDER_XAI.equals(getProvider()) ? "https://api.x.ai/v1" : "https://api.openai.com/v1";
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public int getTimeoutSeconds() {
		return timeoutSeconds;
	}

	public void setTimeoutSeconds(int timeoutSeconds) {
		this.timeoutSeconds = timeoutSeconds;
	}

	public int getMaxOutputTokens() {
		return maxOutputTokens;
	}

	public void setMaxOutputTokens(int maxOutputTokens) {
		this.maxOutputTokens = maxOutputTokens;
	}

	public int getMaxConcurrency() {
		return maxConcurrency;
	}

	public void setMaxConcurrency(int maxConcurrency) {
		this.maxConcurrency = maxConcurrency;
	}

	public int getUserDailyLimit() {
		return userDailyLimit;
	}

	public void setUserDailyLimit(int userDailyLimit) {
		this.userDailyLimit = userDailyLimit;
	}

	public int getGlobalDailyLimit() {
		return globalDailyLimit;
	}

	public void setGlobalDailyLimit(int globalDailyLimit) {
		this.globalDailyLimit = globalDailyLimit;
	}

	public int getRoadmapUserDailyLimit() {
		return roadmapUserDailyLimit;
	}

	public void setRoadmapUserDailyLimit(int roadmapUserDailyLimit) {
		this.roadmapUserDailyLimit = roadmapUserDailyLimit;
	}

	public int getAnalysisUserDailyLimit() {
		return analysisUserDailyLimit;
	}

	public void setAnalysisUserDailyLimit(int analysisUserDailyLimit) {
		this.analysisUserDailyLimit = analysisUserDailyLimit;
	}

	public int getSimplifyUserDailyLimit() {
		return simplifyUserDailyLimit;
	}

	public void setSimplifyUserDailyLimit(int simplifyUserDailyLimit) {
		this.simplifyUserDailyLimit = simplifyUserDailyLimit;
	}

	public int getViewUserDailyLimit() {
		return viewUserDailyLimit;
	}

	public void setViewUserDailyLimit(int viewUserDailyLimit) {
		this.viewUserDailyLimit = viewUserDailyLimit;
	}

	public int getAutoUserDailyLimit() {
		return autoUserDailyLimit;
	}

	public void setAutoUserDailyLimit(int autoUserDailyLimit) {
		this.autoUserDailyLimit = autoUserDailyLimit;
	}
}
