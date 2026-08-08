package com.oriole.wisepen.user.strategy.email;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 教育邮箱学校解析器。
 *
 * 启动时加载教育邮箱域名索引，并根据邮箱地址或域名解析对应的学校。
 * 域名查询支持从完整子域名逐级回退到父域名。
 */
@Slf4j
@Component
public class EducationEmailSchoolResolver {

    /** 教育邮箱学校索引文件。 */
    private static final String DATA_RESOURCE = "data/edu-email-schools.json";

    /** 允许参与学校解析的教育邮箱域名后缀。 */
    private static final Set<String> TARGET_SUFFIXES = Set.of(".edu", ".edu.cn");

    private final ObjectMapper objectMapper;

    /** 规范化后的学校域名索引，key 为域名，value 为学校信息。 */
    private Map<String, EducationEmailSchool> schoolsByDomain = Collections.emptyMap();

    public EducationEmailSchoolResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 加载并规范化教育邮箱学校域名索引。 */
    @PostConstruct
    public void load() {
        ClassPathResource resource = new ClassPathResource(DATA_RESOURCE);
        try (InputStream inputStream = resource.getInputStream()) {
            EducationEmailSchoolIndex index = objectMapper.readValue(inputStream, EducationEmailSchoolIndex.class);
            schoolsByDomain = normalizeSchools(index.getSchoolsByDomain());
            log.info("education email school index loaded. count={} sourceCommit={}",
                    schoolsByDomain.size(), index.getSourceCommit());
        } catch (IOException e) {
            log.error("education email school index load failed. resource={}", DATA_RESOURCE, e);
            throw new IllegalStateException("education email school index load failed.", e);
        }
    }

    /** 根据邮箱地址提取域名，并解析对应的学校。 */
    public Optional<EducationEmailSchool> findByEmail(String email) {
        return findByDomain(extractDomain(email));
    }

    /**
     * 根据域名解析学校。
     *
     * 优先匹配完整域名；未匹配时逐级移除最左侧子域名，
     * 以支持诸如 {@code student.example.edu} 这类邮箱域名。</p>
     */
    public Optional<EducationEmailSchool> findByDomain(String domain) {
        String candidateDomain = normalizeDomain(domain);
        if (!isTargetDomain(candidateDomain)) {
            return Optional.empty();
        }
        while (StrUtil.isNotBlank(candidateDomain)) {
            EducationEmailSchool school = schoolsByDomain.get(candidateDomain);
            if (school != null) {
                return Optional.of(school);
            }
            int dotIndex = candidateDomain.indexOf('.');
            if (dotIndex < 0) {
                break;
            }
            candidateDomain = candidateDomain.substring(dotIndex + 1);
        }
        return Optional.empty();
    }

    /** 从邮箱地址中提取 {@code @} 后面的域名部分。 */
    private static String extractDomain(String email) {
        if (StrUtil.isBlank(email)) {
            return "";
        }
        int atIndex = email.lastIndexOf('@');
        if (atIndex < 0 || atIndex == email.length() - 1) {
            return "";
        }
        return email.substring(atIndex + 1);
    }

    /** 统一域名格式，避免大小写和首尾空格影响匹配。 */
    private static String normalizeDomain(String domain) {
        if (StrUtil.isBlank(domain)) {
            return "";
        }
        return domain.trim().toLowerCase(Locale.ROOT);
    }

    /** 过滤无效数据，并构建不可变的规范化域名索引。 */
    private static Map<String, EducationEmailSchool> normalizeSchools(Map<String, EducationEmailSchool> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, EducationEmailSchool> normalized = new HashMap<>();
        source.forEach((domain, school) -> {
            String normalizedDomain = normalizeDomain(domain);
            if (StrUtil.isBlank(normalizedDomain)
                    || !isTargetDomain(normalizedDomain)
                    || school == null
                    || StrUtil.isBlank(school.getNameZh())) {
                return;
            }
            school.setDomain(normalizedDomain);
            normalized.put(normalizedDomain, school);
        });
        return Collections.unmodifiableMap(normalized);
    }

    /** 判断域名是否属于支持的教育邮箱域名范围。 */
    private static boolean isTargetDomain(String domain) {
        return TARGET_SUFFIXES.stream().anyMatch(domain::endsWith);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EducationEmailSchoolIndex {
        private String sourceCommit;
        private Map<String, EducationEmailSchool> schoolsByDomain;
    }
}
