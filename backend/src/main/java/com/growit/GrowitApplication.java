package com.growit;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 앱 시작점. {@code com.growit} 전체를 기본 스캔 범위로 쓰므로 도메인 패키지에 별도
 * {@code @ComponentScan}·{@code @EntityScan} 설정이 필요 없다.
 */
@SpringBootApplication
public class GrowitApplication {

	static {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
	}

	public static void main(String[] args) {
		SpringApplication.run(GrowitApplication.class, args);
	}
}
