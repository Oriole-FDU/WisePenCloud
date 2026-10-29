package com.oriole.wisepen.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oriole.wisepen.system.api.enums.BatchTaskItemStatus;
import com.oriole.wisepen.system.domain.entity.BatchTaskItemEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BatchTaskItemMapper extends BaseMapper<BatchTaskItemEntity> {
    @Insert("""
            <script>
            INSERT INTO batch_task_item
                (task_id, status, meta, result, error_code, error_message,
                 start_time, finish_time, create_time, update_time)
            VALUES
            <foreach collection="items" item="item" separator=",">
                (#{item.taskId}, #{item.status}, #{item.meta}, #{item.result},
                 #{item.errorCode}, #{item.errorMessage}, #{item.startTime}, #{item.finishTime},
                 #{item.createTime}, #{item.updateTime})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("items") List<BatchTaskItemEntity> items);

    @Select("""
            SELECT i.*
            FROM batch_task_item i
            INNER JOIN batch_task t ON t.id = i.task_id
            WHERE i.status = 'PENDING'
              AND t.status IN ('PENDING', 'RUNNING')
              AND t.deleted_at IS NULL
            ORDER BY i.id ASC
            LIMIT #{limit}
            """)
    List<BatchTaskItemEntity> selectRunnableItems(@Param("limit") int limit);

    @Select("""
            SELECT i.*
            FROM batch_task_item i
            INNER JOIN batch_task t ON t.id = i.task_id
            WHERE i.status = 'RUNNING'
              AND i.start_time < #{cutoff}
              AND t.status IN ('PENDING', 'RUNNING', 'PAUSING')
              AND t.deleted_at IS NULL
            ORDER BY i.id ASC
            LIMIT #{limit}
            """)
    List<BatchTaskItemEntity> selectStaleRunningItems(@Param("cutoff") LocalDateTime cutoff,
                                                      @Param("limit") int limit);

    @Update("""
            UPDATE batch_task_item
            SET status = 'RUNNING', start_time = #{startTime}, update_time = #{startTime}
            WHERE id = #{itemId} AND status = 'PENDING'
              AND EXISTS (
                  SELECT 1
                  FROM batch_task t
                  WHERE t.id = batch_task_item.task_id
                    AND t.status IN ('PENDING', 'RUNNING')
                    AND t.deleted_at IS NULL
              )
            """)
    int claimPendingItem(@Param("itemId") Long itemId, @Param("startTime") LocalDateTime startTime);

    @Update("""
            UPDATE batch_task_item
            SET status = #{status}, result = #{result},
                error_code = #{errorCode}, error_message = #{errorMessage},
                finish_time = #{finishTime}, update_time = #{finishTime}
            WHERE id = #{itemId} AND status = 'RUNNING'
            """)
    int completeRunningItem(@Param("itemId") Long itemId,
                            @Param("status") BatchTaskItemStatus status,
                            @Param("result") String result,
                            @Param("errorCode") Integer errorCode,
                            @Param("errorMessage") String errorMessage,
                            @Param("finishTime") LocalDateTime finishTime);

    @Update("""
            UPDATE batch_task_item
            SET status = 'PENDING', start_time = NULL, finish_time = NULL, update_time = #{updateTime}
            WHERE id = #{itemId} AND status = 'RUNNING'
            """)
    int releaseRunningItem(@Param("itemId") Long itemId, @Param("updateTime") LocalDateTime updateTime);

}
