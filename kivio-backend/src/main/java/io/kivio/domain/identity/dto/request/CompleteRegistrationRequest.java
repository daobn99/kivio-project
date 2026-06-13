package io.kivio.domain.identity.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * パスワード設定・登録完了リクエスト（登録ステップ3）を表現します。
 */
public record CompleteRegistrationRequest(
        /** 登録セッショントークン（verify-otp で発行された UUID） */
        @NotBlank(message = "登録セッショントークンは必須です")
        String registrationToken,

        /** パスワード */
        @NotBlank(message = "パスワードは必須です")
        @Size(min = 8, max = 72, message = "パスワードは8文字以上72文字以内で入力してください")
        String password,

        /** 確認用パスワード */
        @NotBlank(message = "確認用パスワードは必須です")
        String passwordConfirm,

        /** 表示名（任意。省略時は空文字、後でプロフィールで設定可能） */
        @Size(max = 100, message = "表示名は100文字以内で入力してください")
        String displayName
) {
    @AssertTrue(message = "パスワードと確認用パスワードが一致しません")
    public boolean isPasswordsMatch() {
        return password != null && password.equals(passwordConfirm);
    }
}
