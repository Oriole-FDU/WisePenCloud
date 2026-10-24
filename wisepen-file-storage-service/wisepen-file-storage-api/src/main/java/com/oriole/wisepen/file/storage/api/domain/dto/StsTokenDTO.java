package com.oriole.wisepen.file.storage.api.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class StsTokenDTO implements Serializable {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "临时 AccessKey ID")
    private String accessKeyId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "临时 AccessKey Secret")
    private String accessKeySecret;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "临时安全令牌")
    private String securityToken;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "对象存储 Bucket")
    private String bucket;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "对象存储地域")
    private String region;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int64", description = "令牌过期时间")
    private LocalDateTime expiration;
}
