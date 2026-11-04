package com.aiplatform.common.ai;

/**
 * 粗略 Token 估算器，仅在供应商未返回 TokenUsage 时使用。
 * 调用方必须将结果标记为“估算”，不得当作真实计费数据。
 */
public final class TokenEstimator {

    private TokenEstimator() {
    }

    public static int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (isCjk(cp)) {
                cjk++;
            } else if (!Character.isWhitespace(cp)) {
                other++;
            }
            i += Character.charCount(cp);
        }
        return Math.max(1, cjk + (int) Math.ceil(other / 4.0));
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
