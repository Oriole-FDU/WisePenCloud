package com.oriole.wisepen.file.storage.api.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 内部批量下载签名请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadUrlsReqDTO {

    @NotEmpty
    private List<@NotBlank String> objectKeys;

    @Positive
    private Long durationSeconds;
}
