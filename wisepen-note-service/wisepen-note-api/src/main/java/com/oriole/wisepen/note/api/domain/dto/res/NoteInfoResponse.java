package com.oriole.wisepen.note.api.domain.dto.res;

import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class NoteInfoResponse {
    ResourceItemResponse resourceInfo;
    private Integer version;
    private LocalDateTime updateTime;
    Map<Long, UserDisplayBase> authorsDisplay;
}
