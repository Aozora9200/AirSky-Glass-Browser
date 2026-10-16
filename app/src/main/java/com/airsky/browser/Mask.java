package com.airsky.browser;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 保存した情報を、本人確認の前に画面へ出すための伏せ字。
 * 長さが分からないよう、伏せる部分の「•」の数は固定にしている。
 */
final class Mask {

    static final String DOTS = "••••••••";

    private Mask() {
    }

    /** パスワード・カード番号の本体など、まったく出さない値。 */
    @NonNull
    static String hidden() {
        return DOTS;
    }

    /** ユーザー名: 先頭の 1〜2 文字だけ出す(メールアドレスはドメインの先頭 1 文字も)。 */
    @NonNull
    static String username(@Nullable String u) {
        if (u == null || u.isEmpty()) return Lang.t("(ユーザー名なし)");
        int at = u.indexOf('@');
        String local = at > 0 ? u.substring(0, at) : u;
        int len = local.codePointCount(0, local.length());
        // 短いユーザー名は先頭も出さない(ほぼ全部見えてしまうため)
        StringBuilder sb = new StringBuilder(head(local, len > 4 ? 2 : len > 2 ? 1 : 0));
        sb.append("•••••");
        if (at > 0 && at < u.length() - 1) {
            sb.append('@').append(head(u.substring(at + 1), 1)).append("•••");
        }
        return sb.toString();
    }

    /** 名前: 先頭 1 文字だけ。 */
    @NonNull
    static String name(@Nullable String initial) {
        if (initial == null || initial.isEmpty()) return "•••••";
        return initial + "••••";
    }

    /** カード: 「VISA •••• 1234」。 */
    @NonNull
    static String card(@NonNull CredentialVault.Record r) {
        String brand = r.brand.isEmpty() ? Lang.t("カード") : r.brand;
        return brand + " •••• " + (r.last4.isEmpty() ? "••••" : r.last4);
    }

    /** 住所: 「青•••• の住所」。 */
    @NonNull
    static String address(@NonNull CredentialVault.Record r) {
        return r.nameInitial.isEmpty() ? Lang.t("住所 •••••") : name(r.nameInitial) + Lang.t(" の住所");
    }

    /** カード番号からブランド名(先頭の番号で判定)。 */
    @NonNull
    static String cardBrand(@NonNull String digits) {
        if (digits.startsWith("4")) return "VISA";
        if (digits.startsWith("34") || digits.startsWith("37")) return "American Express";
        if (digits.startsWith("35")) return "JCB";
        if (digits.startsWith("36") || digits.startsWith("38") || digits.startsWith("300")
                || digits.startsWith("301") || digits.startsWith("302") || digits.startsWith("303")
                || digits.startsWith("304") || digits.startsWith("305")) return "Diners Club";
        if (digits.startsWith("6011") || digits.startsWith("65")) return "Discover";
        if (digits.startsWith("62")) return "UnionPay";
        if (digits.length() >= 2) {
            int two = Integer.parseInt(digits.substring(0, 2));
            if (two >= 51 && two <= 55) return "Mastercard";
            if (digits.length() >= 4) {
                int four = Integer.parseInt(digits.substring(0, 4));
                if (four >= 2221 && four <= 2720) return "Mastercard";
            }
        }
        return "";
    }

    /** カード番号を 4 桁ごとに区切る(本人確認後の表示用)。 */
    @NonNull
    static String groupedNumber(@Nullable String digits) {
        if (digits == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && i % 4 == 0) sb.append(' ');
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }

    private static String head(String s, int codePoints) {
        int n = Math.min(codePoints, s.codePointCount(0, s.length()));
        return s.substring(0, s.offsetByCodePoints(0, n));
    }
}
