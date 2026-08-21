package com.minioms.application.auth;

/**
 * パスワードのハッシュ化と照合のポート。実装は infrastructure 層が担う。
 *
 * <p>Why not: Spring Security の {@code PasswordEncoder} を application 層で直接使わない。
 * 認証というユースケースの本質は「送られた文字列が保存済みの資格情報と一致するか」だけで、
 * どのハッシュ方式を使うかはインフラの選択。ポートで隔てておくと、
 * ハッシュ方式を変えてもユースケースのテストが影響を受けない。</p>
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
