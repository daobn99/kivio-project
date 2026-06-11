package io.kivio.domain.identity.domain;

import io.kivio.common.entity.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * ユーザーアカウントを表現します。
 */
@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class User extends SoftDeletableEntity {

    /** ユーザーID */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    /** メールアドレス */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** パスワードハッシュ */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    /** Google ID */
    @Column(name = "google_id", unique = true, length = 255)
    private String googleId;

    /** 表示名 */
    @Column(name = "display_name", nullable = false, length = 100)
    @Builder.Default
    private String displayName = "";

    /** アバター画像URL */
    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    /** ロール */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserRole role = UserRole.ROLE_BUYER;

    /** ステータス */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    /** メール確認済みフラグ */
    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    public void verifyEmail() {
        this.emailVerified = true;
    }

    public void linkGoogleId(String googleId) {
        this.googleId = googleId;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }
}
