package com.growit.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

	private String apiKey = "";
	private String model = "";
	private String baseUrl = "https://api.openai.com/v1";
	private int timeoutSeconds = 20;
	private int maxOutputTokens = 600;
	private int maxConcurrency = 2;
	private int userDailyLimit = 5;
	private int globalDailyLimit = 100;
	private int analysisUserDailyLimit = 30;
	private int simplifyUserDailyLimit = 5;
	private int viewUserDailyLimit = 20;
	private int autoUserDailyLimit = 1;

	/** 키나 모델이 비어 있으면 AI 기능 전체가 꺼진 상태다. 이때도 서버는 정상적으로 시작한다. */
	public boolean isEnabled() {
		return apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank();
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
		return baseUrl;
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
