package com.oriole.wisepen.campus.extension.api.domain.dto;

import com.oriole.wisepen.campus.extension.api.enums.ChallengeArtifactEncoding;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 客户端完成登录 Challenge 时需要展示的物料。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeArtifactDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前 Challenge 内稳定的物料名称 */
    private String name;

    /** 物料的 MIME 类型 */
    private String mediaType;

    /** content 使用的编码 */
    private ChallengeArtifactEncoding encoding;

    /** 按 encoding 编码后的物料内容 */
    private String content;
}
