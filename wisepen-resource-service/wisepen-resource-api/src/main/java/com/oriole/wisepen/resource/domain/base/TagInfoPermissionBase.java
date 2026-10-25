package com.oriole.wisepen.resource.domain.base;

import com.oriole.wisepen.resource.enums.AccessControlScope;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Builder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TagInfoPermissionBase extends TagInfoBase{
    // 权限配置
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "资源权限授予范围")
    private AccessControlScope taggedResourceAclGrantScope;         // 资源权限授予范围

    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源权限白名单或黑名单用户 ID 列表")
    private List<String> taggedResourceAclGrantSpecifiedUsers = new ArrayList<>();      // 供 资源权限授予范围中 白名单/黑名单使用的 userId 列表

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int32", description = "匹配该标签时授予的权限掩码")
    private Integer taggedResourceGrantedActionsMask;               // 匹配该标签时授予的权限掩码

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "标签挂载权限范围")
    private AccessControlScope tagMountPermissionScope;             // 标签挂载权限范围

    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "标签挂载白名单或黑名单用户 ID 列表")
    private List<String> tagMountSpecifiedUsers = new ArrayList<>();                    // 供 标签挂载权限范围中 白名单/黑名单使用的 userId 列表
}
