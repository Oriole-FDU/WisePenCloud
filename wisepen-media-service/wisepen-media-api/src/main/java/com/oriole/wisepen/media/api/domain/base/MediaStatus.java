package com.oriole.wisepen.media.api.domain.base;

import com.oriole.wisepen.media.api.enums.MediaStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaStatus {

    /** 当前媒体处理状态。 */
    private MediaStatusEnum status;

    /** 处理失败或异常中断时的错误说明。 */
    private String errorMessage;

    public MediaStatus(MediaStatusEnum status) {
        this.status = status;
    }

    public MediaStatus(String errorMessage) {
        this.status = MediaStatusEnum.FAILED;
        this.errorMessage = errorMessage;
    }
}
