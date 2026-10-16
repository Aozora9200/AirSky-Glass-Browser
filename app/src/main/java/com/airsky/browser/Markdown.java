package com.airsky.browser;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.ReplacementSpan;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * メモ用の小さな Markdown 表示。
 * 見出し(#〜###)・太字・斜体・取り消し線・インラインコード・コードブロック・
 * 箇条書き・番号付きリスト・チェックボックス・引用・リンク・区切り線に対応する。
 */
final class Markdown {

    private static final Pattern URL = Pattern.compile("(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*|__(.+?)__");
    private static final Pattern ITALIC = Pattern.compile("(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?!\\*)|(?<![_\\w])_(?!\\s)(.+?)(?<!\\s)_(?!\\w)");
    private static final Pattern STRIKE = Pattern.compile("~~(.+?)~~");
    private static final Pattern CODE = Pattern.compile("`([^`]+)`");

    private Markdown() {}

    /** 本文に含まれる URL(重複なし、出てきた順) */
    static List<String> findUrls(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = URL.matcher(text);
        while (m.find()) {
            String u = m.group(1);
            // 文末の句読点やかっこは URL に含めない
            while (u.length() > 0 && ".,)]、。".indexOf(u.charAt(u.length() - 1)) >= 0) {
                u = u.substring(0, u.length() - 1);
            }
            if (!out.contains(u)) out.add(u);
        }
        return out;
    }

    static CharSequence render(@NonNull String src, @ColorInt int accent, @ColorInt int codeBg) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        String[] lines = src.split("\n", -1);
        boolean inCode = false;
        for (int li = 0; li < lines.length; li++) {
            String line = lines[li];
            int start = out.length();
            if (line.trim().startsWith("```")) {
                inCode = !inCode;
                continue;
            }
            if (inCode) {
                out.append(line);
                int end = out.length();
                out.setSpan(new TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new BackgroundColorSpan(codeBg), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.append('\n');
                continue;
            }
            String t = line;
            if (t.matches("^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$")) {
                out.append(" ");
                out.setSpan(new RuleSpan(accent), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.append('\n');
                continue;
            }
            int heading = 0;
            while (heading < t.length() && heading < 6 && t.charAt(heading) == '#') heading++;
            if (heading > 0 && heading < t.length() && t.charAt(heading) == ' ') {
                appendInline(out, t.substring(heading + 1), accent, codeBg);
                float size = heading == 1 ? 1.5f : heading == 2 ? 1.3f : 1.15f;
                out.setSpan(new RelativeSizeSpan(size), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (t.startsWith("> ") || t.equals(">")) {
                appendInline(out, t.length() > 2 ? t.substring(2) : "", accent, codeBg);
                out.setSpan(new QuoteSpan(accent), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (t.matches("^\\s*[-*+] \\[( |x|X)] .*")) {
                int indent = t.indexOf(t.trim().charAt(0));
                boolean done = t.charAt(indent + 3) != ' ';
                out.append(done ? "☑ " : "☐ ");
                int textStart = out.length();
                appendInline(out, t.substring(indent + 6), accent, codeBg);
                if (done) {
                    out.setSpan(new StrikethroughSpan(), textStart, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                out.setSpan(new LeadingMarginSpan.Standard(indent * 12), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (t.matches("^\\s*[-*+] .*")) {
                int indent = t.indexOf(t.trim().charAt(0));
                out.append(indent >= 2 ? "◦ " : "• ");
                appendInline(out, t.substring(indent + 2), accent, codeBg);
                out.setSpan(new LeadingMarginSpan.Standard(12 + indent * 12, 12 + indent * 12 + 24), start,
                        out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (t.matches("^\\s*\\d+[.)] .*")) {
                int indent = t.indexOf(t.trim().charAt(0));
                int dot = t.indexOf(' ', indent);
                out.append(t.substring(indent, dot + 1));
                appendInline(out, t.substring(dot + 1), accent, codeBg);
                out.setSpan(new LeadingMarginSpan.Standard(12 + indent * 12), start, out.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                appendInline(out, t, accent, codeBg);
            }
            if (li < lines.length - 1) out.append('\n');
        }
        return out;
    }

    /** 行の中の強調・リンク・コード */
    private static void appendInline(SpannableStringBuilder out, String text, int accent, int codeBg) {
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        // 先にコードを処理して、中の記号を強調として扱わないようにする
        sb = replace(sb, CODE, (m, s) -> {
            s.setSpan(new TypefaceSpan("monospace"), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new BackgroundColorSpan(codeBg), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }, 1);
        sb = replace(sb, LINK, (m, s) ->
                s.setSpan(new URLSpan(m.group(2)), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE), 1);
        sb = replace(sb, BOLD, (m, s) ->
                s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE), -1);
        sb = replace(sb, ITALIC, (m, s) ->
                s.setSpan(new StyleSpan(Typeface.ITALIC), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE), -1);
        sb = replace(sb, STRIKE, (m, s) ->
                s.setSpan(new StrikethroughSpan(), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE), 1);
        // そのまま書かれた URL もリンクにする
        Matcher um = URL.matcher(sb);
        while (um.find()) {
            if (sb.getSpans(um.start(), um.end(), URLSpan.class).length == 0) {
                sb.setSpan(new URLSpan(um.group(1)), um.start(), um.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        out.append(sb);
    }

    private interface Styler {
        void style(Matcher m, SpannableStringBuilder inner);
    }

    /**
     * pattern に合う所を、中身(group)だけにして styler で飾る。group が -1 なら最初に値のある group を使う。
     */
    private static SpannableStringBuilder replace(SpannableStringBuilder src, Pattern p, Styler styler, int group) {
        Matcher m = p.matcher(src.toString());
        SpannableStringBuilder out = new SpannableStringBuilder();
        int last = 0;
        while (m.find()) {
            // すでにコードとして飾った所の中は触らない
            if (p != CODE) {
                TypefaceSpan[] code = src.getSpans(m.start(), m.end(), TypefaceSpan.class);
                if (code.length > 0) continue;
            }
            out.append(src.subSequence(last, m.start()));
            int g = group;
            if (g < 0) {
                g = 1;
                while (g <= m.groupCount() && m.group(g) == null) g++;
            }
            int gs = m.start(g), ge = m.end(g);
            SpannableStringBuilder inner = new SpannableStringBuilder(src.subSequence(gs, ge));
            styler.style(m, inner);
            out.append(inner);
            last = m.end();
        }
        out.append(src.subSequence(last, src.length()));
        return out;
    }

    /** 区切り線 */
    private static final class RuleSpan extends ReplacementSpan {
        private final int color;

        RuleSpan(int color) {
            this.color = color;
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return 0;
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y,
                         int bottom, @NonNull Paint paint) {
            int old = paint.getColor();
            paint.setColor(Color.argb(90, Color.red(color), Color.green(color), Color.blue(color)));
            float mid = (top + bottom) / 2f;
            canvas.drawRect(x, mid - 1, canvas.getWidth(), mid + 1, paint);
            paint.setColor(old);
        }
    }

    /** 一覧のカード用: 記号を取り除いた短い文 */
    static String strip(String src) {
        String s = src.replaceAll("(?m)^```.*$", "");
        s = s.replaceAll("(?m)^#{1,6} ", "");
        s = s.replaceAll("(?m)^\\s*[-*+] \\[( )] ", "☐ ");
        s = s.replaceAll("(?m)^\\s*[-*+] \\[(x|X)] ", "☑ ");
        s = s.replaceAll("(?m)^\\s*[-*+] ", "• ");
        s = s.replaceAll("(?m)^> ", "");
        s = s.replaceAll("\\*\\*(.+?)\\*\\*", "$1").replaceAll("~~(.+?)~~", "$1").replaceAll("`([^`]+)`", "$1");
        s = s.replaceAll("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)", "$1");
        return s;
    }
}
