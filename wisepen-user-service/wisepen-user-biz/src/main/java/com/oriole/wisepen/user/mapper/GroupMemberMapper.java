package com.oriole.wisepen.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oriole.wisepen.user.domain.entity.GroupMemberEntity;
import com.oriole.wisepen.user.mapper.model.GroupMemberCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface GroupMemberMapper extends BaseMapper<GroupMemberEntity> {

    /**
     * 批量统计小组成员数，避免列表接口按小组逐条查询。
     */
    @Select("""
            <script>
            SELECT group_id AS groupId, COUNT(*) AS memberCount
            FROM sys_group_member
            WHERE group_id IN
            <foreach collection="groupIds" item="groupId" open="(" separator="," close=")">
                #{groupId}
            </foreach>
            GROUP BY group_id
            </script>
            """)
    List<GroupMemberCount> selectMemberCounts(@Param("groupIds") Collection<Long> groupIds);
}
