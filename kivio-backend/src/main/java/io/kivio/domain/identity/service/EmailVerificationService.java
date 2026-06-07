package io.kivio.domain.identity.service;

import io.kivio.domain.identity.domain.EmailVerificationToken;
import io.kivio.domain.identity.domain.User;
import io.kivio.domain.identity.exception.EmailVerificationTokenExpiredException;
import io.kivio.domain.identity.exception.EmailVerificationTokenInvalidException;
import io.kivio.domain.identity.repository.EmailVerificationTokenRepository;
import io.kivio.infra.email.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * メールアドレス確認トークン管理サービスを表現します。
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final long TOKEN_TTL_HOURS = 24;

    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailSender emailSender;

    /**
     * 確認トークンを生成・保存してメールを送信します。
     *
     * <p>DB にはトークンの SHA-256 ハッシュ値のみ保存します。
     * 平文のトークンはメール本文にのみ含め、ログや DB に記録しません。
     */
    public void createAndSendVerificationToken(User user) {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);

        EmailVerificationToken token = EmailVerificationToken.builder()
                .userId(user.getId())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(TOKEN_TTL_HOURS, ChronoUnit.HOURS))
                .build();

        tokenRepository.save(token);

        // メール送信は既存トランザクションが完了する前に呼ばれる。
        // 本番導入時は @TransactionalEventListener(AFTER_COMMIT) + 非同期送信に切り替えること
        emailSender.sendVerificationEmail(user.getEmail(), rawToken);
        log.info("email_verification_token_created userId={}", user.getId());
    }

    /**
     * トークンを検証して使用済みにマークします。
     *
     * @throws EmailVerificationTokenInvalidException トークンが存在しないか使用済みの場合
     * @throws EmailVerificationTokenExpiredException トークンの有効期限が切れている場合
     */
    public EmailVerificationToken validateAndConsume(String rawToken) {
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        EmailVerificationToken token = tokenRepository.findByTokenHashAndUsedAtIsNull(tokenHash)
                .orElseThrow(EmailVerificationTokenInvalidException::new);

        if (token.isExpired()) {
            throw new EmailVerificationTokenExpiredException();
        }

        token.markAsUsed();
        return tokenRepository.save(token);
    }
}
