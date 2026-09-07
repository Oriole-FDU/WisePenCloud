package com.oriole.wisepen.generic.resource.api.domain.base;

import com.oriole.wisepen.generic.resource.api.enums.GenericResourceStatusEnum;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GenericResourceStatus {
    private GenericResourceStatusEnum status;

    public GenericResourceStatus(GenericResourceStatusEnum status) {
        this.status = status;
    }
}
