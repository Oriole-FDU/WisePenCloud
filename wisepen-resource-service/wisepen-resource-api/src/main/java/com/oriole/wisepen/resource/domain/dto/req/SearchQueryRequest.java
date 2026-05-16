package com.oriole.wisepen.resource.domain.dto.req;

import com.oriole.wisepen.resource.constant.SearchConstants;
import com.oriole.wisepen.resource.enums.SearchScope;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 前端全文搜索请求 DTO。
 * <p>
 * 字段命名 (page/size) 与 {@code ResourceItemController#listResources} 保持一致，
 * 便于前端复用同一套分页客户端逻辑。
 */
@Data
public class SearchQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "搜索关键字不能为空")
    private String keyword;

    /** 搜索范围：默认 {@link SearchScope#ALL}，与前端"全部"Tab 对齐 */
    private SearchScope scope = SearchScope.ALL;

    @Min(value = SearchConstants.MIN_PAGE_NUM, message = "页码不能小于 " + SearchConstants.MIN_PAGE_NUM)
    private Integer page = SearchConstants.DEFAULT_PAGE_NUM;

    /** 单页大小：滚动加载常用步长，下限 1、上限 100，默认 20 */
    @Min(value = SearchConstants.MIN_PAGE_NUM, message = "每页数量不能小于"+SearchConstants.MIN_PAGE_SIZE)
    @Max(value = SearchConstants.MAX_PAGE_SIZE, message = "每页数量不能大于"+SearchConstants.MAX_PAGE_SIZE)
    private Integer size = SearchConstants.DEFAULT_PAGE_SIZE;
}
