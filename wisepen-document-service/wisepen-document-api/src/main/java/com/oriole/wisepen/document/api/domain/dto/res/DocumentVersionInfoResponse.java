package com.oriole.wisepen.document.api.domain.dto.res;

import com.oriole.wisepen.document.api.domain.base.DocumentVersionBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class DocumentVersionInfoResponse extends DocumentVersionBase {
    private String documentId;
    private Integer version;
}
