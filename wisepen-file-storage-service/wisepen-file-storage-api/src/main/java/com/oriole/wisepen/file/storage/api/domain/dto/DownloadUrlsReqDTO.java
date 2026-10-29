package com.oriole.wisepen.file.storage.api.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 内部批量下载签名请求，限制单次查询规模。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadUrlsReqDTO {

    public static final int MAX_BATCH_SIZE = 500;

    @NotEmpty
    @Size(max = MAX_BATCH_SIZE)
    private List<@NotBlank String> objectKeys;

    @Positive
    private Long durationSeconds;
}
