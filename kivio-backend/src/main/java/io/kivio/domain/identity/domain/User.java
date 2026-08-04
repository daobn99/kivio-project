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
    private String displayName;

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

    public void linkGoogleId(String googleId) {
        this.googleId = googleId;
    }

    /**
     * プロフィールを部分更新します。
     *
     * <p>
     * {@code null} のフィールドは更新しません（{@code PATCH} の「未送信＝不変」セマンティクス）。
     * 値の検証は DTO（Bean Validation）側で済んでいる前提です。
     */
    public void updateProfile(String displayName, String avatarUrl) {
        if (displayName != null) {
            this.displayName = displayName;
        }
        if (avatarUrl != null) {
            // 空文字はクリア要求。JSON では未送信と明示的 null を区別できないため空文字に割り当てている
            this.avatarUrl = avatarUrl.isBlank() ? null : avatarUrl;
        }
    }

    /**
     * パスワードハッシュを差し替えます（照合・エンコードはサービス層の責務）。
     */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }
}
