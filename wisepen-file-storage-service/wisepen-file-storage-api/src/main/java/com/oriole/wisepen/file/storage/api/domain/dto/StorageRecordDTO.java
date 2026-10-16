package com.oriole.wisepen.file.storage.api.domain.dto;

import com.oriole.wisepen.file.storage.api.domain.base.StorageRecordBase;
import com.oriole.wisepen.file.storage.api.enums.StorageStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class StorageRecordDTO extends StorageRecordBase implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long fileId;
    private String domain;
    private StorageStatusEnum status;
}
