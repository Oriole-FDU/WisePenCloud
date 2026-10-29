package com.oriole.wisepen.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oriole.wisepen.user.domain.entity.MessageRecipientEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;

import java.util.List;

@Mapper
public interface MessageRecipientMapper extends BaseMapper<MessageRecipientEntity> {

    @Insert("""
            <script>
            INSERT INTO message_recipient (
                id,
                message_id,
                delivery_scope,
                user_id,
                read_time,
                delete_time,
                create_time
            )
            VALUES
            <foreach collection="recipients" item="recipient" separator=",">
                (
                    #{recipient.id},
                    #{recipient.messageId},
                    #{recipient.deliveryScope},
                    #{recipient.userId},
                    #{recipient.readTime},
                    #{recipient.deleteTime},
                    #{recipient.createTime}
                )
            </foreach>
            </script>
            """)
    int insertBatch(@Param("recipients") List<MessageRecipientEntity> recipients);
}
