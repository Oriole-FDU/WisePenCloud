package com.oriole.wisepen.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.user.domain.entity.GroupAnnouncementReadEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GroupAnnouncementReadMapper extends BaseMapper<GroupAnnouncementReadEntity> {

    @Insert("""
            INSERT IGNORE INTO sys_group_announcement_read
            (id, announcement_id, user_id, read_time, create_time)
            VALUES (#{id}, #{announcementId}, #{userId}, #{readTime}, #{createTime})
            """)
    int insertIgnore(GroupAnnouncementReadEntity entity);

    @Select("""
            SELECT announcement_read.user_id, announcement_read.read_time
            FROM sys_group_announcement_read announcement_read
            INNER JOIN sys_group_member group_member
                    ON group_member.group_id = #{groupId}
                    AND group_member.user_id = announcement_read.user_id
            WHERE announcement_read.announcement_id = #{announcementId}
            ORDER BY announcement_read.read_time ASC, announcement_read.user_id ASC
            """)
    IPage<GroupAnnouncementReadEntity> selectReadMemberPage(
            Page<GroupAnnouncementReadEntity> page,
            @Param("groupId") Long groupId,
            @Param("announcementId") Long announcementId);

    @Select("""
            SELECT group_member.user_id
            FROM sys_group_member group_member
            WHERE group_member.group_id = #{groupId}
              AND NOT EXISTS (
                  SELECT 1
                  FROM sys_group_announcement_read announcement_read
                  WHERE announcement_read.announcement_id = #{announcementId}
                    AND announcement_read.user_id = group_member.user_id
              )
            ORDER BY group_member.join_time DESC, group_member.user_id ASC
            """)
    IPage<Long> selectUnreadUserIdPage(
            Page<Long> page,
            @Param("groupId") Long groupId,
            @Param("announcementId") Long announcementId);

    @Select("""
            SELECT COUNT(*)
            FROM sys_group_announcement_read announcement_read
            INNER JOIN sys_group_member group_member
                    ON group_member.group_id = #{groupId}
                    AND group_member.user_id = announcement_read.user_id
            WHERE announcement_read.announcement_id = #{announcementId}
            """)
    Long countCurrentReadMembers(@Param("groupId") Long groupId, @Param("announcementId") Long announcementId);
}
