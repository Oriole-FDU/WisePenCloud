package com.oriole.wisepen.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.GroupType;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiContractSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfiguration().jackson2ObjectMapperBuilderCustomizer().customize(builder);
        objectMapper = builder.build();
    }

    @Test
    void successEnvelopeUsesStableNonEmptyMetadata() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(R.ok()));

        assertThat(json.get("code").asInt()).isEqualTo(200);
        assertThat(json.get("key").asText()).isEqualTo("SUCCESS");
        assertThat(json.get("msg").asText()).isEqualTo("操作成功");
        assertThat(json.get("data").isNull()).isTrue();
    }

    @Test
    void pageTotalUsesJsonNumber() throws Exception {
        PageR<String> page = new PageR<>(3, 1, 2);
        page.addAll(List.of("a", "b"));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(page));

        assertThat(json.get("total").isNumber()).isTrue();
        assertThat(json.get("total").asLong()).isEqualTo(3);
        assertThat(json.get("list").isArray()).isTrue();
    }

    @Test
    void enumsSerializeWithCanonicalWireValues() throws Exception {
        assertThat(objectMapper.writeValueAsString(IdentityType.STUDENT)).isEqualTo("\"STUDENT\"");
        assertThat(objectMapper.writeValueAsString(GroupType.ADVANCED_GROUP)).isEqualTo("\"ADVANCED_GROUP\"");
        assertThat(objectMapper.writeValueAsString(GroupRoleType.NOT_MEMBER)).isEqualTo("\"NOT_MEMBER\"");
    }

    @Test
    void enumsParseCanonicalAndLegacyCodes() throws Exception {
        assertThat(objectMapper.readValue("\"STUDENT\"", IdentityType.class)).isEqualTo(IdentityType.STUDENT);
        assertThat(objectMapper.readValue("\"1\"", IdentityType.class)).isEqualTo(IdentityType.STUDENT);
        assertThat(objectMapper.readValue("\"2\"", GroupType.class)).isEqualTo(GroupType.ADVANCED_GROUP);
        assertThat(objectMapper.readValue("\"-1\"", GroupRoleType.class)).isEqualTo(GroupRoleType.NOT_MEMBER);
    }

    @Test
    void queryConvertersUseCanonicalWireValues() {
        DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
        new WisepenWebAutoConfiguration().addFormatters(conversionService);

        assertThat(conversionService.convert("TEACHER", IdentityType.class)).isEqualTo(IdentityType.TEACHER);
        assertThat(conversionService.convert("MARKET_GROUP", GroupType.class)).isEqualTo(GroupType.MARKET_GROUP);
        assertThat(conversionService.convert("OWNER", GroupRoleType.class)).isEqualTo(GroupRoleType.OWNER);
        assertThatThrownBy(() -> conversionService.convert("NOT_A_VALUE", IdentityType.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
}
