package com.growit.notification.config;

import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * web-push가 쓰는 EC P-256 암호화는 BouncyCastle 프로바이더(BC)가 JVM에 등록돼 있어야 한다.
 * 등록을 안 하면 Notification 생성 시 NoClassDefFoundError/NoSuchProviderException이 난다 —
 * 서버 시작 시 한 번만(이미 등록돼 있으면 건너뛰고) 등록해 둔다.
 */
@Configuration
@EnableConfigurationProperties(WebPushProperties.class)
public class WebPushConfig {

	static {
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.addProvider(new BouncyCastleProvider());
		}
	}
}
