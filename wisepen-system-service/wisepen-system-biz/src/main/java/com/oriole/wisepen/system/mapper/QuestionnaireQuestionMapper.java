package com.oriole.wisepen.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oriole.wisepen.system.domain.entity.QuestionnaireQuestionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 问卷问题 Mapper
 * 
 * @author Architecture Team
 */
@Mapper
public interface QuestionnaireQuestionMapper extends BaseMapper<QuestionnaireQuestionEntity> {
    
    /**
     * 按问卷ID查询所有问题（按排序）
     */
    List<QuestionnaireQuestionEntity> selectByQuestionnaireId(@Param("questionnaireId") Long questionnaireId);
}
