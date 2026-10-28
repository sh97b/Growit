package com.growit.common;

import com.growit.auth.User;
import com.growit.auth.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * app.temp-user.enabled=true일 때, 시작할 때 users에 id=1 행이 없으면 하나 만든다.
 * password_hash는 로그인에 쓸 수 없는 값으로 채운다 (로그인은 아직 구현되지 않음).
 */
@Component
@ConditionalOnProperty(prefix = "app.temp-user", name = "enabled", havingValue = "true")
public class TempUserInitializer implements ApplicationRunner {

	private static final String TEMP_USERNAME = "temp";
	private static final String NO_LOGIN_PASSWORD_HASH = "TEMP_USER_NO_LOGIN";

	private final UserRepository userRepository;

	public TempUserInitializer(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (userRepository.findById(TempUserCurrentUserProvider.TEMP_USER_ID).isEmpty()) {
			userRepository.save(new User(TEMP_USERNAME, NO_LOGIN_PASSWORD_HASH));
		}
	}
}
