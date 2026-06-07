package io.kivio.domain.identity.repository;

import io.kivio.domain.identity.domain.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * メールアドレス確認トークンリポジトリを表現します。
 */
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHashAndUsedAtIsNull(String tokenHash);

    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.userId = :userId AND t.usedAt IS NULL")
    void deleteUnusedByUserId(@Param("userId") UUID userId);
}
