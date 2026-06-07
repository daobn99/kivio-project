package io.kivio.infra.google;

/**
 * Google ID Token検証後に抽出したユーザー情報を表現します。
 */
public record GoogleUserInfo(String subject, String email) {
}
