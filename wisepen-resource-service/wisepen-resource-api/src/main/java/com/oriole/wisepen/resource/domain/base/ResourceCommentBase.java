package com.oriole.wisepen.resource.domain.base;

import com.oriole.wisepen.resource.enums.CommentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceCommentBase {
    private String resourceId;

    private String replyToUserId;

    private String authorId;
    private String content;
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    @Builder.Default
    private Integer likeCount = 0;
    @Builder.Default
    private Integer replyCount = 0;

    private CommentType commentType;

    /** true 表示该评论由 AI 代笔生成 */
    private Boolean aiAuthored;
}
