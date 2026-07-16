package com.oriole.wisepen.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.system.domain.entity.QuestionnaireEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 问卷模板 Mapper
 * 
 * @author Architecture Team
 */
@Mapper
public interface QuestionnaireMapper extends BaseMapper<QuestionnaireEntity> {
    
    /**
     * 按反馈类型查询问卷列表
     */
    List<QuestionnaireEntity> selectByFeedbackType(@Param("feedbackType") String feedbackType);
    
    /**
     * 查询该反馈类型的默认问卷
     */
    QuestionnaireEntity selectDefaultByFeedbackType(@Param("feedbackType") String feedbackType);
    
    /**
     * 分页查询问卷
     */
    Page<QuestionnaireEntity> selectPageByType(Page<QuestionnaireEntity> page, @Param("feedbackType") String feedbackType);
}
