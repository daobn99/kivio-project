package io.kivio.domain.identity.service;

import io.kivio.domain.audit.annotation.Auditable;
import io.kivio.domain.identity.domain.User;
import io.kivio.domain.identity.dto.request.ChangePasswordRequest;
import io.kivio.domain.identity.dto.request.UpdateProfileRequest;
import io.kivio.domain.identity.dto.response.UserResponse;
import io.kivio.domain.identity.exception.PasswordChangeFailedException;
import io.kivio.domain.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * ユーザー情報のアプリケーションサービスを表現します。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 指定 ID のユーザープロフィールを取得します。
     *
     * @throws io.kivio.common.exception.ResourceNotFoundException ユーザーが存在しない場合
     */
    @Transactional(readOnly = true)
    public UserResponse getById(UUID userId) {
        User user = userRepository.findByIdOrThrow(userId);
        return UserResponse.from(user);
    }

    /**
     * プロフィール（表示名・アバター）を部分更新します。
     *
     * <p>
     * 未送信（{@code null}）のフィールドは更新しません。
     *
     * @throws io.kivio.common.exception.ResourceNotFoundException ユーザーが存在しない場合
     */
    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findByIdOrThrow(userId);
        user.updateProfile(request.displayName(), request.avatarUrl());
        return UserResponse.from(user);
    }

    /**
     * パスワードを変更します。
     *
     * <p>
     * 現在のパスワードを BCrypt で照合し、一致した場合のみ再ハッシュして更新します。
     * Google ログイン専用ユーザー（{@code passwordHash} が null）および
     * 現在のパスワード不一致はいずれも {@link PasswordChangeFailedException}（400）とします。
     *
     * @throws io.kivio.common.exception.ResourceNotFoundException ユーザーが存在しない場合
     * @throws PasswordChangeFailedException                       現在のパスワード不一致 / パスワード未設定ユーザー
     */
    @Transactional
    @Auditable(action = "USER_PASSWORD_CHANGED", entityType = "USER", entityIdParam = "userId")
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findByIdOrThrow(userId);
        if (!user.hasPassword()
                || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new PasswordChangeFailedException();
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    /**
     * アカウントを退会（論理削除）します。
     *
     * <p>
     * {@code deleted_at} を設定し、以降 {@code @SQLRestriction} により通常クエリから除外されます。
     * Shop の連動削除（{@code DB_DESIGN.md §3.2}）は Shop 未実装のため本スライスでは扱いません（OQ-3）。
     *
     * @throws io.kivio.common.exception.ResourceNotFoundException ユーザーが存在しない場合
     */
    @Transactional
    @Auditable(action = "USER_WITHDRAWN", entityType = "USER", entityIdParam = "userId")
    public void withdraw(UUID userId) {
        User user = userRepository.findByIdOrThrow(userId);
        user.softDelete();
    }
}
