package com.growit.user.controller;

import com.growit.user.dto.MeResponse;
import com.growit.user.dto.UpdateMeRequest;
import com.growit.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

	@Operation(summary = "내 이름 수정 (1~30자, 앞뒤 공백은 지움)")
	@PatchMapping("/me")
	public MeResponse updateMe(@RequestBody UpdateMeRequest request) {
		return userService.updateMe(request);
	}
}
