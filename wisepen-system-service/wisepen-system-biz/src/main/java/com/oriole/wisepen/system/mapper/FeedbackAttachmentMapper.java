package com.oriole.wisepen.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oriole.wisepen.system.domain.entity.FeedbackAttachmentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 反馈附件 Mapper
 * 
 * @author Architecture Team
 */
@Mapper
public interface FeedbackAttachmentMapper extends BaseMapper<FeedbackAttachmentEntity> {
    
    /**
     * 按反馈ID查询所有附件
     */
    List<FeedbackAttachmentEntity> selectByFeedbackId(@Param("feedbackId") Long feedbackId);
}
