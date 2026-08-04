package com.oriole.wisepen.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oriole.wisepen.user.domain.entity.GroupAnnouncementAttachmentEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GroupAnnouncementAttachmentMapper extends BaseMapper<GroupAnnouncementAttachmentEntity> {

    @Insert("""
            <script>
            INSERT INTO sys_group_announcement_attachment
            (attachment_id, announcement_id, object_key, file_name, file_size, sort_order, create_time)
            VALUES
            <foreach collection="attachments" item="attachment" separator=",">
                (#{attachment.attachmentId}, #{attachment.announcementId}, #{attachment.objectKey},
                 #{attachment.fileName}, #{attachment.fileSize}, #{attachment.sortOrder}, #{attachment.createTime})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("attachments") List<GroupAnnouncementAttachmentEntity> attachments);
}
