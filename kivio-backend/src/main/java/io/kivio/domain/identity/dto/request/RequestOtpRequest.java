package io.kivio.domain.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 認証コード（OTP）送信リクエスト（登録ステップ1）を表現します。
 */
public record RequestOtpRequest(
        /** メールアドレス */
        @NotBlank(message = "メールアドレスは必須です")
        @Email(message = "メールアドレスの形式が正しくありません")
        @Size(max = 255, message = "メールアドレスは255文字以内で入力してください")
        String email
) {}
