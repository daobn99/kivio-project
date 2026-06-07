package io.kivio.domain.identity.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ユーザー登録リクエストを表現します。
 */
public record RegisterRequest(
        /** メールアドレス */
        @NotBlank(message = "メールアドレスは必須です")
        @Email(message = "メールアドレスの形式が正しくありません")
        @Size(max = 255, message = "メールアドレスは255文字以内で入力してください")
        String email,

        /** パスワード */
        @NotBlank(message = "パスワードは必須です")
        @Size(min = 8, max = 100, message = "パスワードは8文字以上100文字以内で入力してください")
        String password,

        /** 確認用パスワード */
        @NotBlank(message = "確認用パスワードは必須です")
        String passwordConfirm
) {
    @AssertTrue(message = "パスワードと確認用パスワードが一致しません")
    public boolean isPasswordsMatch() {
        return password != null && password.equals(passwordConfirm);
    }
}
