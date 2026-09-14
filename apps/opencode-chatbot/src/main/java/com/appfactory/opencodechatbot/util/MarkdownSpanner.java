package com.appfactory.opencodechatbot.util;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;

import com.appfactory.opencodechatbot.R;

import java.util.List;

/**
 * Renders a {@link Markdown.Document} into a {@link Spanned} for a TextView.
 * Android-specific (spans + theme colors); the parsing itself lives in the
 * pure {@link Markdown} class.
 */
public final class MarkdownSpanner {

    private final int primaryColor;
    private final int secondaryColor;
    private final int codeFg;
    private final int codeBg;
    private final int accentColor;
    private final int keywordColor;
    private final int stringColor;
    private final int commentColor;
    private final int numberColor;
    private final int typeColor;

    public MarkdownSpanner(android.content.Context context) {
        this.primaryColor = AppTheme.resolveColor(context, R.attr.chatTextPrimary);
        this.secondaryColor = AppTheme.resolveColor(context, R.attr.chatTextSecondary);
        this.accentColor = AppTheme.resolveColor(context, R.attr.chatAccent);
        int codeBgRaw = AppTheme.resolveColor(context, R.attr.chatCodeBackground);
        this.codeBg = codeBgRaw;
        this.codeFg = 0xFFE2E8F0; // light code text on dark code background
        this.keywordColor = 0xFFF472B6;
        this.stringColor = 0xFFA5B4FC;
        this.commentColor = 0xFF64748B;
        this.numberColor = 0xFFFBBF24;
        this.typeColor = 0xFF67E8F9;
    }

    public Spanned render(Markdown.Document document) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (document == null) {
            return sb;
        }
        List<Markdown.Block> blocks = document.getBlocks();

        for (int b = 0; b < blocks.size(); b++) {
            Markdown.Block block = blocks.get(b);

            switch (block.kind) {
                case Markdown.BLOCK_HEADING: {
                    int start = sb.length();
                    appendInlines(sb, block.inlines, primaryColor);
                    int size = block.level <= 1 ? 22 : block.level == 2 ? 18 : 16;
                    sb.setSpan(new android.text.style.AbsoluteSizeSpan(size, true), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                }
                case Markdown.BLOCK_PARAGRAPH: {
                    appendInlines(sb, block.inlines, primaryColor);
                    break;
                }
                case Markdown.BLOCK_QUOTE: {
                    int start = sb.length();
                    sb.append('\u201C');
                    appendInlines(sb, block.inlines, secondaryColor);
                    sb.append('\u201D');
                    sb.setSpan(new StyleSpan(Typeface.ITALIC), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                }
                case Markdown.BLOCK_LIST_ITEM: {
                    sb.append(block.ordered ? "\u2022 " : "");
                    appendInlines(sb, block.inlines, primaryColor);
                    break;
                }
                case Markdown.BLOCK_CODE: {
                    appendCodeBlock(sb, block.text, block.url);
                    break;
                }
                case Markdown.BLOCK_HR: {
                    sb.append("_ _ _ _ _ _ _ _");
                    sb.setSpan(new ForegroundColorSpan(secondaryColor),
                            sb.length() - 16, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                }
                default:
                    break;
            }

            if (b < blocks.size() - 1) {
                sb.append('\n');
            }
        }
        return sb;
    }

    private void appendInlines(SpannableStringBuilder sb, List<Markdown.Inline> inlines, int baseColor) {
        if (inlines == null) {
            return;
        }
        for (Markdown.Inline in : inlines) {
            int start = sb.length();
            sb.append(in.text);
            if ((in.style & Markdown.STYLE_BOLD) != 0) {
                sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if ((in.style & Markdown.STYLE_ITALIC) != 0) {
                sb.setSpan(new StyleSpan(Typeface.ITALIC), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if ((in.style & Markdown.STYLE_STRIKE) != 0) {
                sb.setSpan(new android.text.style.StrikethroughSpan(), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if ((in.style & Markdown.STYLE_CODE_OUT) != 0) {
                sb.setSpan(new BackgroundColorSpan(codeBg), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new ForegroundColorSpan(codeFg), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new android.text.style.TypefaceSpan("monospace"), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if ((in.style & Markdown.STYLE_LINK) != 0) {
                sb.setSpan(new URLSpan(in.url), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new ForegroundColorSpan(accentColor), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (baseColor != 0) {
                sb.setSpan(new ForegroundColorSpan(baseColor), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private void appendCodeBlock(SpannableStringBuilder sb, String code, String lang) {
        String text = code == null ? "" : code;
        if (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        int start = sb.length();
        sb.append(text);
        sb.setSpan(new BackgroundColorSpan(codeBg), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new android.text.style.TypefaceSpan("monospace"), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        List<SyntaxHighlighter.Token> tokens = SyntaxHighlighter.highlight(lang, text);
        for (SyntaxHighlighter.Token t : tokens) {
            int s = start + t.start;
            int e = Math.min(start + t.end, sb.length());
            if (e <= s) {
                continue;
            }
            int color;
            switch (t.kind) {
                case SyntaxHighlighter.KIND_KEYWORD:
                    color = keywordColor;
                    break;
                case SyntaxHighlighter.KIND_STRING:
                    color = stringColor;
                    break;
                case SyntaxHighlighter.KIND_COMMENT:
                    color = commentColor;
                    break;
                case SyntaxHighlighter.KIND_NUMBER:
                    color = numberColor;
                    break;
                default:
                    color = typeColor;
            }
            sb.setSpan(new ForegroundColorSpan(color), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}