package com.oriole.wisepen.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oriole.wisepen.system.domain.entity.FeedbackStatusHistoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 反馈状态历史 Mapper
 * 
 * @author Architecture Team
 */
@Mapper
public interface FeedbackStatusHistoryMapper extends BaseMapper<FeedbackStatusHistoryEntity> {
    
    /**
     * 按反馈ID查询状态历史（按时间升序）
     */
    List<FeedbackStatusHistoryEntity> selectByFeedbackId(@Param("feedbackId") Long feedbackId);
}
