package io.kivio.infra.google;

/**
 * Google ID Token検証後に抽出したユーザー情報を表現します。
 *
 * @param subject Google アカウントの一意識別子（sub クレーム）
 * @param email   検証済みメールアドレス
 * @param name    プロフィール表示名（name クレーム）。profile スコープ未付与時は null になりうる
 */
public record GoogleUserInfo(String subject, String email, String name) {
}
