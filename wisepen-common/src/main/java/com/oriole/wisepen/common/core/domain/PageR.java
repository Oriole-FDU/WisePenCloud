package com.oriole.wisepen.common.core.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.oriole.wisepen.common.core.domain.serializer.LongNumberSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Data
@NoArgsConstructor
public class PageR<T> implements Serializable {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "当前页数据")
    private List<T> list = new ArrayList<>();

    @JsonSerialize(using = LongNumberSerializer.class)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int64", description = "总记录数")
    private long total;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "当前页码")
    private int page;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "每页记录数")
    private int size;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "总页数")
    private int totalPage;

    public PageR(long total, int page, int size) {
        this.total = total;
        this.page = page;
        this.size = size;
        this.totalPage = size == 0 ? 0 : (int) Math.ceil((double) total / size);
    }

    public void addAll(Collection<? extends T> collection) {
        if (collection != null) {
            this.list.addAll(collection);
        }
    }

    public void add(T item) {
        if (item != null) {
            this.list.add(item);
        }
    }
}
