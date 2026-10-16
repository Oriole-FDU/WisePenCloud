package com.oriole.wisepen.document.util;

import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 保留正文原貌的图片扫描器。转换、查询和 Fork 共用位置和页边界解释，
 * 不将整篇 Markdown 转为 HTML 再转回。
 */
public final class MarkdownImages {

    private static final char BACKTICK = '`';
    private static final Pattern DEFINITION = Pattern.compile("(?m)^ {0,3}\\[([^\\]\\r\\n]+)][ \\t]*:[ \\t]*(<[^>\\r\\n]*>|[^\\s]+)[^\\r\\n]*");
    private static final Pattern TITLE_START = Pattern.compile("\\s+[\"'(]");
    private static final Pattern PAGE = Pattern.compile("(?m)^\\h*<!-- page:(start|end) page=([1-9][0-9]{0,8}) -->\\h*$");
    private static final Pattern DATA_IMAGE = Pattern.compile("(?i)data:image/[^\\s\"'<>\\[\\]()]*");

    private MarkdownImages() {
    }

    public record Image(int start, int end, String alt, String source, Integer pageNo) {
    }

    private record Definition(int start, int end, int sourceStart, int sourceEnd, String source) {
    }

    private record Page(int start, int end, int number) {
    }

    private record Replacement(int start, int end, String text) {
    }

    public static final class Parsed {
        private final String markdown;
        private final List<Image> images;
        private final List<Definition> definitions;

        private Parsed(String markdown, List<Image> images, List<Definition> definitions) {
            this.markdown = markdown;
            this.images = images;
            this.definitions = definitions;
        }

        public List<Image> images() {
            return images;
        }

        public String rewrite(Function<Image, String> replacement) {
            List<Replacement> edits = new ArrayList<>();
            Map<String, String> targets = new HashMap<>();
            for (Image image : images) {
                String rewritten = replacement.apply(image);
                edits.add(new Replacement(image.start(), image.end(), rewritten));
                Image target = rewritten.startsWith("![") ? markdownImage(rewritten, 0, Map.of()) : null;
                // 同一引用多次出现时选取首个成功目标，也保留共用此定义的普通链接。
                targets.putIfAbsent(image.source(), target == null ? null : target.source());
            }
            for (Definition definition : definitions) {
                if (targets.containsKey(definition.source())) {
                    String target = targets.get(definition.source());
                    if (target == null) edits.add(new Replacement(definition.start(), definition.end(), ""));
                    else edits.add(new Replacement(definition.sourceStart(), definition.sourceEnd(), "<" + target + ">"));
                } else if (isDataImage(definition.source())) {
                    edits.add(new Replacement(definition.start(), definition.end(), ""));
                }
            }
            edits.sort(java.util.Comparator.comparingInt(Replacement::start));
            StringBuilder rewritten = new StringBuilder();
            int cursor = 0;
            for (Replacement edit : edits) {
                if (edit.start() < cursor) continue;
                rewritten.append(markdown, cursor, edit.start()).append(edit.text());
                cursor = edit.end();
            }
            return rewritten.append(markdown, cursor, markdown.length()).toString();
        }
    }

    public static Parsed parse(String markdown) {
        if (markdown == null || markdown.isEmpty()) return new Parsed("", List.of(), List.of());
        BitSet code = codePositions(markdown);
        Map<String, Definition> byLabel = new HashMap<>();
        List<Definition> definitions = new ArrayList<>();
        Matcher definitionMatcher = DEFINITION.matcher(markdown);
        while (definitionMatcher.find()) {
            if (code.get(definitionMatcher.start())) continue;
            String source = definitionMatcher.group(2);
            if (source.startsWith("<") && source.endsWith(">")) source = source.substring(1, source.length() - 1);
            Definition definition = new Definition(definitionMatcher.start(), definitionMatcher.end(),
                    definitionMatcher.start(2), definitionMatcher.end(2), source);
            byLabel.putIfAbsent(label(definitionMatcher.group(1)), definition);
            definitions.add(definition);
        }
        List<Page> pages = pages(markdown, code);
        BitSet excluded = (BitSet) code.clone();
        for (Definition definition : definitions) excluded.set(definition.start(), definition.end());
        List<Image> images = new ArrayList<>();
        int pageIndex = 0;
        for (int cursor = 0; cursor < markdown.length();) {
            if (excluded.get(cursor)) {
                cursor = excluded.nextClearBit(cursor);
                continue;
            }
            if (markdown.charAt(cursor) == '\\') {
                cursor += 2;
                continue;
            }
            if (markdown.startsWith("<!--", cursor)) {
                int end = markdown.indexOf("-->", cursor + 4);
                cursor = end < 0 ? markdown.length() : end + 3;
                continue;
            }
            Image image = null;
            if (markdown.startsWith("![", cursor)) image = markdownImage(markdown, cursor, byLabel);
            else if (markdown.regionMatches(true, cursor, "<img", 0, 4)
                    && cursor + 4 < markdown.length()
                    && (Character.isWhitespace(markdown.charAt(cursor + 4)) || markdown.charAt(cursor + 4) == '>')) {
                int end = htmlTagEnd(markdown, cursor + 4);
                if (end >= 0) {
                    var element = Jsoup.parseBodyFragment(markdown.substring(cursor, end)).selectFirst("img");
                    if (element != null) image = new Image(cursor, end, element.attr("alt"), element.attr("src"), null);
                }
            }
            if (image == null) {
                cursor++;
                continue;
            }
            while (pageIndex < pages.size() && pages.get(pageIndex).end() <= image.start()) pageIndex++;
            Integer pageNo = null;
            if (pageIndex < pages.size()) {
                Page page = pages.get(pageIndex);
                if (image.start() >= page.start() && image.end() <= page.end()) pageNo = page.number();
            }
            images.add(new Image(image.start(), image.end(), image.alt(), image.source(), pageNo));
            cursor = image.end();
        }
        return new Parsed(markdown, images, definitions);
    }

    private static Image markdownImage(String text, int start, Map<String, Definition> definitions) {
        int altEnd = closingBracket(text, start + 1, '[', ']');
        if (altEnd < 0) return null;
        String alt = text.substring(start + 2, altEnd);
        int cursor = altEnd + 1;
        if (cursor < text.length() && text.charAt(cursor) == '(') {
            int end = closingBracket(text, cursor, '(', ')');
            if (end < 0) return null;
            String body = text.substring(cursor + 1, end).trim();
            String source;
            if (body.startsWith("<")) {
                int close = body.indexOf('>');
                if (close < 0) return null;
                source = body.substring(1, close);
            } else if (isDataImage(body)) {
                // 允许 base64 中折行，但不能将可选的 Markdown title 当成载荷。
                Matcher title = TITLE_START.matcher(body);
                source = title.find() ? body.substring(0, title.start()) : body;
            } else {
                int sourceEnd = 0;
                while (sourceEnd < body.length() && !Character.isWhitespace(body.charAt(sourceEnd))) sourceEnd++;
                source = body.substring(0, sourceEnd);
            }
            return new Image(start, end + 1, alt, source, null);
        }
        String reference = alt;
        if (cursor < text.length() && text.charAt(cursor) == '[') {
            int end = closingBracket(text, cursor, '[', ']');
            if (end < 0) return null;
            reference = end == cursor + 1 ? alt : text.substring(cursor + 1, end);
            cursor = end + 1;
        }
        Definition definition = definitions.get(label(reference));
        if (definition == null) return null;
        return new Image(start, cursor, alt, definition.source(), null);
    }

    private static int closingBracket(String text, int start, char open, char close) {
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '\\') i++;
            else if (character == open) depth++;
            else if (character == close && --depth == 0) return i;
        }
        return -1;
    }

    private static int htmlTagEnd(String text, int start) {
        char quote = 0;
        for (int i = start; i < text.length(); i++) {
            char character = text.charAt(i);
            if (quote != 0) {
                if (character == quote) quote = 0;
            } else if (character == '"' || character == '\'') quote = character;
            else if (character == '>') return i + 1;
        }
        return -1;
    }

    private static String label(String label) {
        return label.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** 仅完整同页号区间可推定页码；丢失边界、嵌套或跨页区间均不猜测。 */
    private static List<Page> pages(String markdown, BitSet code) {
        List<Page> pages = new ArrayList<>();
        Matcher matcher = PAGE.matcher(markdown);
        int start = -1;
        int number = 0;
        boolean nested = false;
        while (matcher.find()) {
            if (code.get(matcher.start())) continue;
            int page = Integer.parseInt(matcher.group(2));
            if ("start".equals(matcher.group(1))) {
                if (start >= 0) nested = true;
                else {
                    start = matcher.end();
                    number = page;
                }
            } else {
                if (start >= 0 && number == page && !nested) pages.add(new Page(start, matcher.start(), number));
                start = -1;
                nested = false;
            }
        }
        return pages;
    }

    private static BitSet codePositions(String text) {
        BitSet code = new BitSet(text.length());
        char fence = 0;
        int fenceLength = 0;
        for (int lineStart = 0; lineStart < text.length();) {
            int newline = text.indexOf('\n', lineStart);
            int lineEnd = newline < 0 ? text.length() : newline + 1;
            int first = lineStart;
            while (first < lineEnd && text.charAt(first) == ' ') first++;
            char marker = first < lineEnd ? text.charAt(first) : 0;
            int runEnd = first;
            if (marker == BACKTICK || marker == '~') {
                while (runEnd < lineEnd && text.charAt(runEnd) == marker) runEnd++;
            }
            if (fence != 0) {
                code.set(lineStart, lineEnd);
                if (first - lineStart <= 3 && marker == fence && runEnd - first >= fenceLength
                        && text.substring(runEnd, lineEnd).isBlank()) fence = 0;
            } else if (first - lineStart <= 3 && runEnd - first >= 3) {
                fence = marker;
                fenceLength = runEnd - first;
                code.set(lineStart, lineEnd);
            } else if (first - lineStart >= 4 || marker == '\t') {
                code.set(lineStart, lineEnd);
            }
            lineStart = lineEnd;
        }
        for (int i = 0; i < text.length();) {
            if (code.get(i)) {
                i = code.nextClearBit(i);
                continue;
            }
            if (text.charAt(i) == '\\') {
                i += 2;
                continue;
            }
            if (text.charAt(i) != BACKTICK) {
                i++;
                continue;
            }
            int runEnd = i + 1;
            while (runEnd < text.length() && text.charAt(runEnd) == BACKTICK) runEnd++;
            String delimiter = text.substring(i, runEnd);
            int close = text.indexOf(delimiter, runEnd);
            while (close >= 0 && !code.get(close)) {
                int closeEnd = close + delimiter.length();
                if (closeEnd == text.length() || text.charAt(closeEnd) != BACKTICK) break;
                while (closeEnd < text.length() && text.charAt(closeEnd) == BACKTICK) closeEnd++;
                close = text.indexOf(delimiter, closeEnd);
            }
            if (close >= 0 && !code.get(close)) {
                code.set(i, close + delimiter.length());
                i = close + delimiter.length();
            } else i = runEnd;
        }
        return code;
    }

    public static boolean isDataImage(String source) {
        return source != null && source.regionMatches(true, 0, "data:image/", 0, 11);
    }

    public static String image(String alt, String source) {
        return "![" + cleanAlt(alt) + "](" + source + ")";
    }

    public static String cleanAlt(String alt) {
        return alt == null ? "" : alt.replaceAll("[\\[\\]\\\\\\r\\n]", " ").trim();
    }

    public static String missing(String alt) {
        return "【图片暂不可用" + (alt == null || alt.isBlank() ? "" : "：" + cleanAlt(alt)) + "】";
    }

    /** 兼顾历史内容和损坏节点；代码示例不作为图片上传，但 data URI 载荷仍不进入文本上下文。 */
    public static String withoutInlineData(String markdown) {
        if (markdown == null || !markdown.toLowerCase(Locale.ROOT).contains("data:image/")) return markdown;
        String rewritten = parse(markdown).rewrite(image -> isDataImage(image.source())
                ? missing(image.alt()) : image(image.alt(), image.source()));
        return DATA_IMAGE.matcher(rewritten).replaceAll("【内嵌图片已省略】");
    }
}
