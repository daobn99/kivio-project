package io.kivio.domain.identity.service;

import io.kivio.domain.identity.domain.EmailVerificationToken;
import io.kivio.domain.identity.domain.User;
import io.kivio.domain.identity.exception.EmailVerificationTokenExpiredException;
import io.kivio.domain.identity.exception.EmailVerificationTokenInvalidException;
import io.kivio.domain.identity.repository.EmailVerificationTokenRepository;
import io.kivio.infra.email.EmailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock private EmailVerificationTokenRepository tokenRepository;
    @Mock private EmailSender emailSender;
    @InjectMocks private EmailVerificationService emailVerificationService;

    // ============================================================
    // validateAndConsume
    // ============================================================

    @Test
    void should_mark_token_as_used_and_return_it_when_token_is_valid() {
        String rawToken = "valid-raw-token";
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        EmailVerificationToken token = EmailVerificationToken.builder()
                .userId(UUID.randomUUID())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        given(tokenRepository.findByTokenHashAndUsedAtIsNull(tokenHash)).willReturn(Optional.of(token));
        given(tokenRepository.save(token)).willReturn(token);

        EmailVerificationToken result = emailVerificationService.validateAndConsume(rawToken);

        assertThat(result.isUsed()).isTrue();
        then(tokenRepository).should().save(token);
    }

    @Test
    void should_throw_EmailVerificationTokenInvalidException_when_token_not_found_or_already_used() {
        given(tokenRepository.findByTokenHashAndUsedAtIsNull(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.validateAndConsume("bad-token"))
                .isInstanceOf(EmailVerificationTokenInvalidException.class);
    }

    @Test
    void should_throw_EmailVerificationTokenExpiredException_when_token_is_expired() {
        String rawToken = "expired-token";
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        EmailVerificationToken expired = EmailVerificationToken.builder()
                .userId(UUID.randomUUID())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().minusSeconds(1))
                .build();
        given(tokenRepository.findByTokenHashAndUsedAtIsNull(tokenHash)).willReturn(Optional.of(expired));

        assertThatThrownBy(() -> emailVerificationService.validateAndConsume(rawToken))
                .isInstanceOf(EmailVerificationTokenExpiredException.class);
    }

    // ============================================================
    // createAndSendVerificationToken
    // ============================================================

    @Test
    void should_save_hashed_token_and_send_email_when_creating_verification_token() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@example.com")
                .build();
        given(tokenRepository.save(any(EmailVerificationToken.class)))
                .willAnswer(inv -> inv.getArgument(0));

        emailVerificationService.createAndSendVerificationToken(user);

        ArgumentCaptor<EmailVerificationToken> tokenCaptor =
                ArgumentCaptor.forClass(EmailVerificationToken.class);
        then(tokenRepository).should().save(tokenCaptor.capture());
        EmailVerificationToken saved = tokenCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTokenHash()).isNotBlank();
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
        // 平文トークンはメール経由でのみ渡される
        then(emailSender).should().sendVerificationEmail(eq("user@example.com"), anyString());
    }
}
