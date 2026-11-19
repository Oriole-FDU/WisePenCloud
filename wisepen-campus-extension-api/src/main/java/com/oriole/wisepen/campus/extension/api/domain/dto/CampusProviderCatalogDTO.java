package com.oriole.wisepen.campus.extension.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 校园 Provider 汇总，按登录与身份认证能力分组，供前端分别渲染两个入口。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampusProviderCatalogDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 支持校园登录的 Provider 入口 */
    private List<CampusProviderDescriptorDTO> login;

    /** 支持校园身份认证的 Provider 入口 */
    private List<CampusProviderDescriptorDTO> identity;
}
