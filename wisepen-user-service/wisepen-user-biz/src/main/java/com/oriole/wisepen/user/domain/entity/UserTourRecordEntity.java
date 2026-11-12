package com.oriole.wisepen.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.oriole.wisepen.user.api.enums.TourCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_tour_record")
public class UserTourRecordEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private TourCode tourCode;
    private LocalDateTime completeTime;
}
