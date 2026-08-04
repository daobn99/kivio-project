package io.kivio.domain.identity.service;

import io.kivio.infra.email.EmailMessage;
import io.kivio.infra.email.EmailSender;
import io.kivio.infra.email.template.EmailTemplateFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * {@link AuthEmailService} のテンプレート選択・件名生成・トランスポート委譲を単体検証します。
 */
@ExtendWith(MockitoExtension.class)
class AuthEmailServiceTest {

    @Mock
    private EmailTemplateFormatter templateFormatter;
    @Mock
    private EmailSender emailSender;
    @InjectMocks
    private AuthEmailService authEmailService;

    @Test
    void should_render_otp_template_and_delegate_to_sender() {
        given(templateFormatter.render(eq("emails/ja/registration-otp.html"), any()))
                .willReturn("<html>rendered</html>");

        authEmailService.sendRegistrationOtp("user@example.com", "123456");

        // テンプレートに OTP と有効期限が渡されること
        ArgumentCaptor<Map<String, String>> varsCaptor = ArgumentCaptor.captor();
        then(templateFormatter).should()
                .render(eq("emails/ja/registration-otp.html"), varsCaptor.capture());
        assertThat(varsCaptor.getValue())
                .containsEntry("otpCode", "123456")
                .containsEntry("expiresIn", "10分");

        // 描画済みメッセージがそのまま送信トランスポートへ渡されること
        ArgumentCaptor<EmailMessage> messageCaptor = ArgumentCaptor.captor();
        then(emailSender).should().send(messageCaptor.capture());
        EmailMessage sent = messageCaptor.getValue();
        assertThat(sent.to()).isEqualTo("user@example.com");
        assertThat(sent.subject()).isEqualTo("【Kivio】認証コード: 123456");
        assertThat(sent.htmlBody()).isEqualTo("<html>rendered</html>");
    }
}
