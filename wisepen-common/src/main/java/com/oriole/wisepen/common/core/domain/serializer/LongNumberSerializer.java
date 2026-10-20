package com.oriole.wisepen.common.core.domain.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 将指定 long 字段按 JSON number 输出，避免全局 Long -> string 规则泄漏到分页计数等数值契约。
 */
public class LongNumberSerializer extends JsonSerializer<Long> {

    @Override
    public void serialize(Long value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
        if (value == null) {
            generator.writeNull();
            return;
        }
        generator.writeNumber(value);
    }
}
