package com.growit.user.controller;

import com.growit.common.exception.ErrorResponse;
import com.growit.user.dto.ChangePasswordRequest;
import com.growit.user.dto.ChangePasswordResponse;
import com.growit.user.dto.DeleteAccountRequest;
import com.growit.user.dto.MeResponse;
import com.growit.user.dto.UpdateMeRequest;
import com.growit.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "로그인", description = "회원가입·로그인")
@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@Operation(summary = "내 정보 조회")
	@GetMapping("/me")
	public MeResponse me() {
		return userService.me();
	}

	@Operation(summary = "내 이름 수정 (1~30자, 앞뒤 공백은 지움), aiEnabled로 AI 기록 정리 켜고 끄기")
	@PatchMapping("/me")
	public MeResponse updateMe(@RequestBody UpdateMeRequest request) {
		return userService.updateMe(request);
	}

	/**
	 * 현재 비밀번호가 틀리면 UNAUTHORIZED(401)이고 로그인 시도 제한에도 반영된다. 새 비밀번호가 비밀번호
	 * 정책을 어기거나 현재 비밀번호와 같으면 VALIDATION_ERROR(400). 많이 틀리면 LOGIN_LOCKED(429).
	 * 성공하면 다른 기기의 토큰은 무효화되고, 이 응답의 accessToken으로 이어서 로그인 상태를 쓴다.
	 */
	@Operation(summary = "비밀번호 변경")
	@ApiResponse(responseCode = "401", description = "UNAUTHORIZED: 현재 비밀번호가 틀림",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "SOCIAL_ACCOUNT: 소셜 로그인 계정은 비밀번호가 없어 바꿀 수 없음",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "429", description = "LOGIN_LOCKED: 로그인 시도가 너무 많음",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PatchMapping("/me/password")
	public ChangePasswordResponse changePassword(@Valid @RequestBody ChangePasswordRequest request,
			HttpServletRequest httpRequest) {
		return userService.changePassword(request, httpRequest.getRemoteAddr());
	}

	/**
	 * 비밀번호 계정은 password가 틀리면 UNAUTHORIZED(401), 소셜 전용 계정은 confirmation이 "계정 삭제"와
	 * 다르면 VALIDATION_ERROR(400). 내 ai_results·ai_logs·records·hobbies·social_accounts·계정을
	 * 한 번에 지운다(되돌릴 수 없음).
	 */
	@Operation(summary = "계정 삭제")
	@ApiResponse(responseCode = "401", description = "UNAUTHORIZED: 비밀번호가 틀림",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@DeleteMapping("/me")
	public ResponseEntity<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest request) {
		userService.deleteAccount(request);
		return ResponseEntity.noContent().build();
	}
}
