package io.kivio.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA 監査機能の設定を表現します。
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
