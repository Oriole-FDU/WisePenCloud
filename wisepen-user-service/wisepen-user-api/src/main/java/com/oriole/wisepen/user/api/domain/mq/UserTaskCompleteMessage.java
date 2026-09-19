package com.oriole.wisepen.user.api.domain.mq;

import com.oriole.wisepen.user.api.enums.UserTaskCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTaskCompleteMessage implements Serializable {

    private Long userId;

    private UserTaskCode taskCode;

    private Long operatorId;

    private String meta;
}
