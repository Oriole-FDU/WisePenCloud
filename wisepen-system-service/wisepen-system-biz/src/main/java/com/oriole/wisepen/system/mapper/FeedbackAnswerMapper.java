package com.oriole.wisepen.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oriole.wisepen.system.domain.entity.FeedbackAnswerEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 反馈回答 Mapper
 * 
 * @author Architecture Team
 */
@Mapper
public interface FeedbackAnswerMapper extends BaseMapper<FeedbackAnswerEntity> {
    
    /**
     * 按反馈ID查询所有回答
     */
    List<FeedbackAnswerEntity> selectByFeedbackId(@Param("feedbackId") Long feedbackId);
    
    /**
     * 批量删除反馈的所有回答
     */
    int deleteByFeedbackId(@Param("feedbackId") Long feedbackId);
}
