package io.kivio.infra.email;

import io.kivio.config.EmailProperties;
import io.kivio.config.ResendProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ResendEmailSender} の Resend HTTP API 契約を検証します。
 *
 * <p>{@code baseUrl} を最小限の HTTP スタブ（{@link ServerSocket}）へ向けることで、
 * 外部サービスへ到達せずにリクエスト内容（パス・認証ヘッダー・ボディ形式）と
 * エラー時の振る舞いを検証する。追加の依存ライブラリは使用しない。
 */
class ResendEmailSenderTest {

    private static final EmailProperties EMAIL_PROPERTIES =
            new EmailProperties("noreply@kivio.example.com", "Kivio");
    private static final EmailMessage MESSAGE = new EmailMessage(
            "user@example.com", "【Kivio】認証コード: 123456", "<html>body</html>");

    private ServerSocket serverSocket;
    private Thread stubThread;
    private String baseUrl;
    /** スタブが返すステータスコード。テストごとに差し替える。 */
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicReference<String> capturedRequestLine = new AtomicReference<>();
    private final AtomicReference<String> capturedAuthorization = new AtomicReference<>();
    private final AtomicReference<String> capturedBody = new AtomicReference<>();
    private final AtomicReference<String> capturedHeaders = new AtomicReference<>();
    private final CountDownLatch requestHandled = new CountDownLatch(1);

    @BeforeEach
    void startStubServer() throws IOException {
        // ポート 0 で空きポートを OS に割り当てさせる（テスト並列実行時の衝突回避）
        serverSocket = new ServerSocket(0);
        baseUrl = "http://localhost:" + serverSocket.getLocalPort();
        stubThread = new Thread(this::handleSingleRequest);
        stubThread.setDaemon(true);
        stubThread.start();
    }

    @AfterEach
    void stopStubServer() throws IOException {
        serverSocket.close();
        stubThread.interrupt();
    }

    /**
     * リクエストを 1 件だけ受け付け、内容を記録して固定レスポンスを返します。
     *
     * <p>{@code Content-Length} は UTF-8 バイト長で付与されるため、文字単位ではなく
     * バイト単位で読み取る（日本語の件名では文字数とバイト数が一致しない）。
     */
    private void handleSingleRequest() {
        try (Socket socket = serverSocket.accept()) {
            InputStream in = socket.getInputStream();

            // ヘッダー終端（CRLF CRLF）まで 1 バイトずつ読む。ボディを読み過ぎないため
            ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
            int consecutive = 0;
            int b;
            while (consecutive < 2 && (b = in.read()) >= 0) {
                headerBytes.write(b);
                if (b == '\n') {
                    consecutive++;
                } else if (b != '\r') {
                    consecutive = 0;
                }
            }

            String headers = headerBytes.toString(StandardCharsets.UTF_8);
            capturedHeaders.set(headers);
            String[] lines = headers.split("\r\n");
            capturedRequestLine.set(lines.length > 0 ? lines[0] : null);
            int contentLength = 0;
            for (String line : lines) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.startsWith("authorization:")) {
                    capturedAuthorization.set(line.substring("authorization:".length()).trim());
                } else if (lower.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
                }
            }

            byte[] body = in.readNBytes(contentLength);
            capturedBody.set(new String(body, StandardCharsets.UTF_8));

            OutputStream out = socket.getOutputStream();
            out.write(("HTTP/1.1 " + responseStatus.get() + " X\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: 13\r\n\r\n"
                    + "{\"id\":\"stub\"}").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            // テスト終了時のソケットクローズによる例外は無視してよい
        } finally {
            requestHandled.countDown();
        }
    }

    @Test
    void should_post_rendered_message_to_resend_with_bearer_auth() throws Exception {
        ResendEmailSender sender = new ResendEmailSender(
                new ResendProperties("re_test_key", baseUrl), EMAIL_PROPERTIES, new ObjectMapper());

        sender.send(MESSAGE);

        assertThat(requestHandled.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(capturedRequestLine.get()).startsWith("POST /emails ");
        assertThat(capturedAuthorization.get()).isEqualTo("Bearer re_test_key");
        // ボディをオブジェクトのまま渡すと chunked 転送になる。互換性のため
        // Content-Length 付きで送ることを固定する（ResendEmailSender の事前文字列化）
        assertThat(capturedHeaders.get())
                .containsIgnoringCase("Content-Length:")
                .doesNotContainIgnoringCase("Transfer-Encoding: chunked");
        assertThat(capturedBody.get())
                .as("headers=[%s]", capturedHeaders.get())
                .contains("\"from\":\"Kivio <noreply@kivio.example.com>\"")
                .contains("\"to\":[\"user@example.com\"]")
                .contains("\"subject\":\"【Kivio】認証コード: 123456\"")
                .contains("\"html\":\"<html>body</html>\"");
    }

    @Test
    void should_throw_when_resend_returns_error_status() {
        responseStatus.set(422);
        ResendEmailSender sender = new ResendEmailSender(
                new ResendProperties("re_test_key", baseUrl), EMAIL_PROPERTIES, new ObjectMapper());

        assertThatThrownBy(() -> sender.send(MESSAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("メールの送信に失敗しました");
    }

    @Test
    void should_fail_fast_when_api_key_is_missing() {
        // 本番の設定漏れを「メールだけ届かない」ではなく起動失敗として検知させる
        assertThatThrownBy(() -> new ResendEmailSender(
                new ResendProperties("  ", baseUrl), EMAIL_PROPERTIES, new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESEND_API_KEY");
    }

    @Test
    void should_fall_back_to_default_base_url_when_not_configured() {
        // baseUrl 未設定でも生成できること（本番では既定の https://api.resend.com を使う）
        ResendEmailSender sender = new ResendEmailSender(
                new ResendProperties("re_test_key", null), EMAIL_PROPERTIES, new ObjectMapper());

        assertThat(sender).isNotNull();
    }
}
