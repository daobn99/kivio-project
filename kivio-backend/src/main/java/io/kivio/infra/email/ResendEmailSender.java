package io.kivio.infra.email;

import io.kivio.config.EmailProperties;
import io.kivio.config.ResendProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * 本番環境向けの Resend HTTP API メール送信トランスポートです（prod プロファイル限定）。
 *
 * <p>dev では {@code SmtpEmailSender}（SMTP→Mailpit）が使われるため、本実装は
 * 本番でのみ有効化される。差出人は {@code EmailProperties} から組み立てる。
 *
 * <p>SECURITY: リクエストボディには OTP を含む件名・本文が載るため、
 * 失敗時も含めてボディをログへ出力しない。
 */
@Slf4j
@Component
@Profile("prod")
public class ResendEmailSender implements EmailSender {

    /** Resend のメール送信エンドポイント（ベース URL からの相対パス）。 */
    private static final String SEND_ENDPOINT = "/emails";
    private static final String DEFAULT_BASE_URL = "https://api.resend.com";
    /** 外部 API 障害時にスレッドを占有し続けないためのタイムアウト。 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String fromHeader;

    public ResendEmailSender(
            ResendProperties resendProperties, EmailProperties emailProperties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // prod でのみ生成されるため、ここでの必須チェックは本番設定漏れの起動時検知になる
        if (resendProperties.apiKey() == null || resendProperties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "RESEND_API_KEY が未設定です。prod プロファイルでは必須です");
        }
        String baseUrl = resendProperties.baseUrl() == null || resendProperties.baseUrl().isBlank()
                ? DEFAULT_BASE_URL
                : resendProperties.baseUrl();

        // JDK HttpClient ベースの実装を使う。リクエストボディをバッファして Content-Length を
        // 付与するため、chunked 転送を受け付けない HTTP API でも安全に送信できる
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + resendProperties.apiKey())
                .build();
        // Resend は "表示名 <アドレス>" 形式を受け付ける
        this.fromHeader = "%s <%s>".formatted(emailProperties.fromName(), emailProperties.fromAddress());
    }

    @Override
    public void send(EmailMessage message) {
        SendEmailRequest request = new SendEmailRequest(
                fromHeader, List.of(message.to()), message.subject(), message.htmlBody());
        // JSON を事前に文字列化して送る。オブジェクトのまま渡すとストリーミング送信となり
        // Content-Length ではなく chunked 転送になるため、受け側の互換性を優先する
        String payload = objectMapper.writeValueAsString(request);
        try {
            restClient.post()
                    .uri(SEND_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // SECURITY: 例外メッセージにレスポンス本文が含まれ得るため、ログには出さず送信先のみ記録する
            log.warn("resend_send_failed to={}", message.to());
            throw new IllegalStateException("メールの送信に失敗しました", e);
        }
        log.debug("Sent email to={}", message.to());
    }

    /**
     * Resend の送信 API リクエストボディを表現します。
     *
     * @param from    差出人（{@code 表示名 <アドレス>} 形式）
     * @param to      送信先メールアドレス
     * @param subject 件名（描画済み）
     * @param html    本文 HTML（描画済み）
     */
    private record SendEmailRequest(String from, List<String> to, String subject, String html) {
    }
}
