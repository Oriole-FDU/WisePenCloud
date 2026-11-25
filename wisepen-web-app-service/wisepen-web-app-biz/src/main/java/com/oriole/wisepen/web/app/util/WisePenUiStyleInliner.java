package com.oriole.wisepen.web.app.util;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WisePen UI 样式内联器
 *
 * 将 Single HTML Page 中引用 WisePen UI v1 tokens.css 的 <link> 外链替换为内联 <style>
 */
@Component
public class WisePenUiStyleInliner {

    /** WisePen UI v1 样式资源路径 */
    private static final String TOKENS_CSS_RESOURCE = "wisepen-ui/v1/tokens.css";

    /** 匹配引用 WisePen UI v1 tokens.css 的 <link> 标签 */
    private static final Pattern STYLESHEET_LINK = Pattern.compile(
            "(?i)<link\\b[^>]*href\\s*=\\s*[\"'][^\"']*wisepen-ui/v1/tokens\\.css[^\"']*[\"'][^>]*>");

    /** 内联样式块 */
    private final String styleReplacement;

    public WisePenUiStyleInliner() {
        this.styleReplacement = Matcher.quoteReplacement("<style>\n" + loadTokensCss() + "\n</style>");
    }

    /**
     * 将 HTML 中引用 WisePen UI v1 tokens.css 的 <link> 替换为内联 <style>。
     * 未引用该样式时原样返回
     */
    public String inline(String html) {
        String inlined = STYLESHEET_LINK.matcher(html).replaceFirst(styleReplacement);
        return STYLESHEET_LINK.matcher(inlined).replaceAll("");
    }

    private static String loadTokensCss() {
        try (InputStream inputStream = new ClassPathResource(TOKENS_CSS_RESOURCE).getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load " + TOKENS_CSS_RESOURCE, e);
        }
    }
}
