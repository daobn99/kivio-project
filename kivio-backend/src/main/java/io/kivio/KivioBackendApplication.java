package io.kivio;

import io.kivio.config.jwt.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Kivio バックエンドアプリケーションのエントリポイントを表現します。
 */
@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class KivioBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(KivioBackendApplication.class, args);
	}

}
