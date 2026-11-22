package com.oriole.wisepen.file.storage.api.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 内部服务端直传产物上传完成确认请求，用于把 UPLOADING 记录置为 AVAILABLE。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmUploadReqDTO {

    @Valid
    @NotEmpty
    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {

        @NotBlank
        private String objectKey;

        private String md5;

        private Long size;
    }
}
