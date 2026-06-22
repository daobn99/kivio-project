plugins {
	java
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
	jacoco
	checkstyle
}

group = "io.kivio"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

checkstyle {
	toolVersion = "10.21.0"
	configFile = file("config/checkstyle/checkstyle.xml")
	isIgnoreFailures = false
	maxWarnings = 0
}

repositories {
	mavenCentral()
}

dependencies {
	// Spring Boot Starters
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	// Redis（登録 OTP・登録セッションの TTL 一時ストレージ）
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	// Lettuce プーリング（application.yaml の lettuce.pool.enabled=true に必要。starter には推移的に含まれない）
	implementation("org.apache.commons:commons-pool2")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	// メール送信（dev: SMTP→Mailpit。prod は Resend HTTP 実装に差し替え予定）
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-websocket")

	// AOP（@Auditable AOP、@Aspect）
	// spring-boot-starter-aop は Spring Boot 4.x では廃止。aspectjweaver は他スターターから推移的に提供される
	implementation("org.aspectj:aspectjweaver")

	// Database
	runtimeOnly("org.postgresql:postgresql")
	implementation("org.springframework.boot:spring-boot-flyway") // Spring Boot 4.x Flyway autoconfiguration
	implementation("org.flywaydb:flyway-core")
	runtimeOnly("org.flywaydb:flyway-database-postgresql")

	// JWT（HS256 / RS256 トークン生成・検証）
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

	// Rate Limiting（SECURITY.md § 4）
	implementation("com.bucket4j:bucket4j-core:8.10.1")

	// API ドキュメント（Swagger UI）
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")

	// Lombok
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

	// Test
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	// Testcontainers 2.x ではモジュール名が "testcontainers-" プレフィックス付きに変更
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	// Redis は core の GenericContainer を使用（image 名 "redis" は Spring Boot の @ServiceConnection が認識する）
	testImplementation("org.testcontainers:testcontainers")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
	finalizedBy(tasks.jacocoTestReport)
}

// DTO・設定クラス・エントリポイントをカバレッジ対象から除外する単一の定義。
// jacocoTestReport（表示）と jacocoTestCoverageVerification（ゲート）の母数を一致させる。
val coverageExclusions = listOf(
	"**/dto/**",
	"**/config/**",
	"**/KivioBackendApplication*"
)

// レポートと検証の双方に同じ除外を適用するヘルパ
fun excludedClassDirs(dirs: FileCollection): FileCollection =
	files(dirs.files.map { fileTree(it) { exclude(coverageExclusions) } })

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required.set(true)
		html.required.set(true)
	}
	classDirectories.setFrom(excludedClassDirs(classDirectories))
}

// テストカバレッジゲート。母数はレポートと同一（coverageExclusions を適用）。
tasks.jacocoTestCoverageVerification {
	dependsOn(tasks.test)
	classDirectories.setFrom(excludedClassDirs(classDirectories))
	violationRules {
		rule {
			limit {
				counter = "INSTRUCTION"
				value = "COVEREDRATIO"
				minimum = "0.80".toBigDecimal()
			}
		}
	}
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	jvmArgs("-Duser.timezone=Asia/Tokyo")
}

tasks.withType<JavaCompile> {
	options.compilerArgs.addAll(listOf("-parameters", "-Xlint:deprecation"))
}
