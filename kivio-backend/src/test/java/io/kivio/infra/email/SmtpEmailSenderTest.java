package io.kivio.infra.email;

import io.kivio.config.EmailProperties;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * {@link SmtpEmailSender} の {@link EmailMessage} → {@link MimeMessage} 詰め替え契約を検証します。
 *
 * <p>dev プロファイル限定のトランスポートのため、本番品質ではなく
 * 差出人・宛先・件名のマッピングが静かに壊れないことを固定する happy-path のみを対象とする。
 * 実 SMTP 送信は Mailpit 目視（DoD）で別途検証済み。
 */
@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void should_map_message_fields_and_delegate_to_mail_sender() throws Exception {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        given(mailSender.createMimeMessage()).willReturn(mimeMessage);
        EmailProperties properties = new EmailProperties("noreply@kivio.example.com", "Kivio");
        SmtpEmailSender sender = new SmtpEmailSender(mailSender, properties);

        sender.send(new EmailMessage(
                "user@example.com", "【Kivio】認証コード: 123456", "<html>body</html>"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.captor();
        then(mailSender).should().send(captor.capture());
        MimeMessage sent = captor.getValue();

        InternetAddress from = (InternetAddress) sent.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("noreply@kivio.example.com");
        assertThat(from.getPersonal()).isEqualTo("Kivio");
        assertThat(sent.getRecipients(Message.RecipientType.TO)[0].toString())
                .isEqualTo("user@example.com");
        assertThat(sent.getSubject()).isEqualTo("【Kivio】認証コード: 123456");
    }
}
