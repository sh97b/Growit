package com.growit.auth.oauth.controller;

import com.growit.auth.oauth.dto.OauthExchangeRequest;
import com.growit.auth.oauth.dto.OauthExchangeResponse;
import com.growit.auth.oauth.dto.OauthProviderStatus;
import com.growit.auth.oauth.entity.SocialProvider;
import com.growit.auth.oauth.service.OauthService;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토큰을 주소에 담지 않으려고 콜백은 1회용 로그인 코드를 fragment로만 건네고, 실제 토큰은
 * /exchange(본문)로만 돌려준다. state는 HttpOnly·Secure·SameSite=Lax 쿠키로 왕복 검증한다.
 */
@Tag(name = "로그인", description = "카카오·구글 소셜 로그인")
@RestController
@RequestMapping("/api/auth/oauth")
public class OauthController {

	private static final String STATE_COOKIE_NAME = "oauth_state";
	private static final String STATE_COOKIE_PATH = "/api/auth/oauth";

	private final OauthService oauthService;

	public OauthController(OauthService oauthService) {
		this.oauthService = oauthService;
	}

	@Operation(summary = "켜져 있는 소셜 로그인 제공자 목록")
	@GetMapping("/providers")
	public List<OauthProviderStatus> providers() {
		return oauthService.providers();
	}

	@Operation(summary = "제공자 인증 화면으로 이동")
	@GetMapping("/{provider}/start")
	public ResponseEntity<Void> start(@PathVariable String provider) {
		SocialProvider parsed = oauthService.parseProvider(provider);
		if (!oauthService.isEnabled(parsed)) {
			throw new ApiException(ErrorCode.NOT_FOUND, "꺼져 있는 로그인 방식이에요");
		}
		String state = oauthService.issueState();
		String authorizeUrl = oauthService.authorizeUrl(parsed, state);
		ResponseCookie cookie = stateCookie(state, Duration.ofMinutes(5));
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create(authorizeUrl))
				.header(HttpHeaders.SET_COOKIE, cookie.toString())
				.build();
	}

	@Operation(summary = "제공자 콜백 (state 검증 후 프론트의 /auth/callback으로 1회용 코드와 함께 이동)")
	@GetMapping("/{provider}/callback")
	public ResponseEntity<Void> callback(@PathVariable String provider,
			@RequestParam(required = false) String code, @RequestParam(required = false) String state,
			@CookieValue(name = STATE_COOKIE_NAME, required = false) String cookieState) {
		ResponseCookie clearCookie = stateCookie("", Duration.ZERO);

		SocialProvider parsed;
		try {
			parsed = oauthService.parseProvider(provider);
		} catch (ApiException e) {
			return redirectTo(failureUrl(), clearCookie);
		}

		if (code == null || !oauthService.verifyState(cookieState, state)) {
			return redirectTo(failureUrl(), clearCookie);
		}

		try {
			String loginCode = oauthService.handleCallback(parsed, code);
			return redirectTo(oauthService.frontendBaseUrl() + "/auth/callback#code=" + loginCode, clearCookie);
		} catch (ApiException e) {
			return redirectTo(failureUrl(), clearCookie);
		}
	}

	@Operation(summary = "1회용 로그인 코드를 우리 JWT로 교환 (코드는 한 번만, 60초 안에)")
	@PostMapping("/exchange")
	public OauthExchangeResponse exchange(@Valid @RequestBody OauthExchangeRequest request,
			HttpServletRequest httpRequest) {
		return oauthService.exchange(request.code(), httpRequest.getRemoteAddr());
	}

	private String failureUrl() {
		return oauthService.frontendBaseUrl() + "/login?error=social_failed";
	}

	private ResponseEntity<Void> redirectTo(String url, ResponseCookie cookie) {
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create(url))
				.header(HttpHeaders.SET_COOKIE, cookie.toString())
				.build();
	}

	private ResponseCookie stateCookie(String value, Duration maxAge) {
		return ResponseCookie.from(STATE_COOKIE_NAME, value)
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path(STATE_COOKIE_PATH)
				.maxAge(maxAge)
				.build();
	}
}
