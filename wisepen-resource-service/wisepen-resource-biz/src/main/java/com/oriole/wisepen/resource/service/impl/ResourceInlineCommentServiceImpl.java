package com.oriole.wisepen.resource.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.resource.domain.base.ResourceInlineCommentItemBase;
import com.oriole.wisepen.resource.domain.base.ResourceInlineCommentItemReactionBase;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentCreateRequest;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentItemCreateRequest;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentItemDeleteRequest;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentItemReactionDeleteRequest;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentItemReactionSetRequest;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentItemUpdateRequest;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentResolveRequest;
import com.oriole.wisepen.resource.domain.dto.res.InlineCommentItemResponse;
import com.oriole.wisepen.resource.domain.dto.res.InlineCommentItemReactionGroupResponse;
import com.oriole.wisepen.resource.domain.dto.res.ResourceInlineCommentResponse;
import com.oriole.wisepen.resource.domain.entity.ResourceInlineCommentEntity;
import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.exception.ResourceError;
import com.oriole.wisepen.resource.repository.CustomResourceInlineCommentRepository;
import com.oriole.wisepen.resource.repository.ResourceInlineCommentRepository;
import com.oriole.wisepen.resource.mq.IResourceEventPublisher;
import com.oriole.wisepen.resource.service.IResourceInlineCommentService;
import com.oriole.wisepen.resource.service.IResourceService;
import com.oriole.wisepen.resource.service.assembler.ResourceInteractionMessageBuilder;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.feign.RemoteUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResourceInlineCommentServiceImpl implements IResourceInlineCommentService {

    private final IResourceService resourceService;
    private final ResourceInlineCommentRepository inlineCommentRepository;
    private final CustomResourceInlineCommentRepository customInlineCommentRepository;
    private final RemoteUserService remoteUserService;
    private final IResourceEventPublisher resourceEventPublisher;

    @Override
    public String createInlineComment(InlineCommentCreateRequest request,
                                      String operatorUserId) {
        String resourceId = request.getResourceId();
        ResourceItemEntity resource = resourceService.getResourceEntity(resourceId);

        LocalDateTime now = LocalDateTime.now();
        // 构建 InlineCommentItem
        ResourceInlineCommentItemBase commentItem = ResourceInlineCommentItemBase.builder()
                .itemId(IdUtil.fastSimpleUUID()).authorId(operatorUserId)
                .createTime(now).updateTime(now).build();
        BeanUtil.copyProperties(request, commentItem);

        // 构建锚点
        ResourceInlineCommentEntity.AnchorRef anchorRef = BeanUtil.copyProperties(request, ResourceInlineCommentEntity.AnchorRef.class);

        // 构建 InlineComment
        ResourceInlineCommentEntity inlineComment = ResourceInlineCommentEntity.builder()
                .resourceId(resourceId)
                .applicableFromVersion(request.getApplicableFromVersion()).applicableToVersion(request.getApplicableToVersion())
                .creatorId(operatorUserId)
                .anchorRef(anchorRef)
                .items(new ArrayList<>(List.of(commentItem)))
                .resolved(false)
                .build();
        inlineComment = inlineCommentRepository.save(inlineComment);
        if (StringUtils.hasText(resource.getOwnerId()) && !Objects.equals(resource.getOwnerId(), operatorUserId)) {
            resourceEventPublisher.publishUserMessage(ResourceInteractionMessageBuilder.inlineComment(
                    resource, operatorUserId, inlineComment.getInlineCommentId(), commentItem.getItemId(), resource.getOwnerId(), commentItem.getContent()));
        }

        log.info("inline comment created. resourceId={} inlineCommentId={} creatorId={}", resourceId, inlineComment.getInlineCommentId(), operatorUserId);
        return inlineComment.getInlineCommentId();
    }

    @Override
    public String addInlineCommentItem(InlineCommentItemCreateRequest request,
                                       String operatorUserId) {
        String resourceId = request.getResourceId();
        ResourceItemEntity resource = resourceService.getResourceEntity(resourceId);
        ResourceInlineCommentEntity inlineComment = getInlineComment(request.getInlineCommentId(), resourceId);
        // 检查是否仍然适用当前版本
        if (!isApplicable(inlineComment, request.getContentVersion())) {
            throw new ServiceException(ResourceError.COMMENT_NOT_FOUND);
        }

        // 构建 InlineComment 并直接插入
        LocalDateTime now = LocalDateTime.now();
        ResourceInlineCommentItemBase commentItem = ResourceInlineCommentItemBase.builder()
                .itemId(IdUtil.fastSimpleUUID()).authorId(operatorUserId)
                .createTime(now).updateTime(now).build();
        BeanUtil.copyProperties(request, commentItem);
        customInlineCommentRepository.appendItem(resourceId, request.getInlineCommentId(), commentItem);
        if (StringUtils.hasText(inlineComment.getCreatorId()) && !Objects.equals(inlineComment.getCreatorId(), operatorUserId)) {
            resourceEventPublisher.publishUserMessage(ResourceInteractionMessageBuilder.inlineComment(
                    resource, operatorUserId, request.getInlineCommentId(), commentItem.getItemId(), inlineComment.getCreatorId(), commentItem.getContent()));
        }

        log.info("inline comment item created. resourceId={} inlineCommentId={} itemId={} authorId={}", resourceId, request.getInlineCommentId(), commentItem.getItemId(), operatorUserId);
        return commentItem.getItemId();
    }

    @Override
    public void updateInlineCommentItem(InlineCommentItemUpdateRequest request, String operatorUserId) {
        String resourceId = request.getResourceId();
        ResourceInlineCommentEntity inlineComment = getInlineComment(request.getInlineCommentId(), resourceId);
        if (!isApplicable(inlineComment, request.getContentVersion())) {
            throw new ServiceException(ResourceError.COMMENT_NOT_FOUND);
        }

        ResourceInlineCommentItemBase commentItem = inlineComment.getItems().stream()
                .filter(candidate -> Objects.equals(candidate.getItemId(), request.getItemId()))
                .findFirst()
                .orElseThrow(() -> new ServiceException(ResourceError.COMMENT_NOT_FOUND));

        if (!Objects.equals(commentItem.getAuthorId(), operatorUserId)) {
            throw new ServiceException(ResourceError.COMMENT_UPDATE_ACCESS_DENIED);
        }

        customInlineCommentRepository.updateItem(resourceId, request.getInlineCommentId(), request.getItemId(), operatorUserId,
                request.getContent(), request.getImageUrls(), request.getMentionUserIds());
        log.info("inline comment item updated. resourceId={} inlineCommentId={} itemId={} operatorUserId={}",
                resourceId, request.getInlineCommentId(), request.getItemId(), operatorUserId);
    }

    @Override
    public void setInlineCommentItemReaction(InlineCommentItemReactionSetRequest request, String operatorUserId) {
        String resourceId = request.getResourceId();
        ResourceItemEntity resource = resourceService.getResourceEntity(resourceId);
        ResourceInlineCommentEntity inlineComment = getInlineComment(request.getInlineCommentId(), resourceId);
        if (!isApplicable(inlineComment, request.getContentVersion())) {
            throw new ServiceException(ResourceError.COMMENT_NOT_FOUND);
        }

        ResourceInlineCommentItemBase commentItem = inlineComment.getItems() == null ? null : inlineComment.getItems().stream()
                .filter(candidate -> Objects.equals(candidate.getItemId(), request.getItemId()))
                .findFirst()
                .orElse(null);
        if (commentItem == null) throw new ServiceException(ResourceError.COMMENT_NOT_FOUND);

        LocalDateTime now = LocalDateTime.now();
        ResourceInlineCommentItemReactionBase reaction = ResourceInlineCommentItemReactionBase.builder()
                .emojiId(request.getEmojiId())
                .createTime(now)
                .build();
        Map<String, List<ResourceInlineCommentItemReactionBase>> reactionMap =
                commentItem.getReactions() == null ? Collections.emptyMap() : commentItem.getReactions();
        List<ResourceInlineCommentItemReactionBase> reactions = new ArrayList<>(reactionMap.getOrDefault(operatorUserId, Collections.emptyList()));
        boolean alreadyReacted = reactions.stream().anyMatch(existingReaction -> Objects.equals(existingReaction.getEmojiId(), request.getEmojiId()));
        reactions.removeIf(existingReaction -> Objects.equals(existingReaction.getEmojiId(), request.getEmojiId()));
        reactions.add(reaction);
        customInlineCommentRepository.setItemReactions(resourceId, request.getInlineCommentId(), request.getItemId(), operatorUserId, reactions);
        if (!alreadyReacted && StringUtils.hasText(commentItem.getAuthorId()) && !Objects.equals(commentItem.getAuthorId(), operatorUserId)) {
            resourceEventPublisher.publishUserMessage(ResourceInteractionMessageBuilder.inlineCommentReaction(
                    resource, operatorUserId, request.getInlineCommentId(), request.getItemId(), request.getEmojiId(), commentItem.getAuthorId(), commentItem.getContent()));
        }
        log.info("inline comment item reaction set. resourceId={} inlineCommentId={} itemId={} operatorUserId={} emojiId={}",
                resourceId, request.getInlineCommentId(), request.getItemId(), operatorUserId, request.getEmojiId());
    }

    @Override
    public void deleteInlineCommentItemReaction(InlineCommentItemReactionDeleteRequest request, String operatorUserId) {
        String resourceId = request.getResourceId();
        ResourceInlineCommentEntity inlineComment = getInlineComment(request.getInlineCommentId(), resourceId);
        if (!isApplicable(inlineComment, request.getContentVersion())) {
            throw new ServiceException(ResourceError.COMMENT_NOT_FOUND);
        }

        ResourceInlineCommentItemBase commentItem = inlineComment.getItems() == null ? null : inlineComment.getItems().stream()
                .filter(candidate -> Objects.equals(candidate.getItemId(), request.getItemId()))
                .findFirst()
                .orElse(null);
        if (commentItem == null) throw new ServiceException(ResourceError.COMMENT_NOT_FOUND);

        List<ResourceInlineCommentItemReactionBase> reactions = commentItem.getReactions().getOrDefault(operatorUserId, Collections.emptyList());
        reactions.removeIf(existingReaction -> Objects.equals(existingReaction.getEmojiId(), request.getEmojiId()));
        customInlineCommentRepository.setItemReactions(resourceId, request.getInlineCommentId(), request.getItemId(), operatorUserId, reactions);
        log.info("inline comment item reaction deleted. resourceId={} inlineCommentId={} itemId={} operatorUserId={} emojiId={}",
                resourceId, request.getInlineCommentId(), request.getItemId(), operatorUserId, request.getEmojiId());
    }

    @Override
    public void deleteInlineCommentItem(InlineCommentItemDeleteRequest request, String operatorUserId, IdentityType operatorIdentityType) {
        String resourceId = request.getResourceId();
        ResourceItemEntity resource = resourceService.getResourceEntity(resourceId);
        ResourceInlineCommentEntity inlineComment = getInlineComment(request.getInlineCommentId(), resourceId);

        ResourceInlineCommentItemBase commentItem = inlineComment.getItems().stream()
                .filter(candidate -> Objects.equals(candidate.getItemId(), request.getItemId()))
                .findFirst()
                .orElseThrow(() -> new ServiceException(ResourceError.COMMENT_NOT_FOUND));

        // 管理员，资源所有者，评论者本人可以删除评论
        if (!commentItem.getAuthorId().equals(operatorUserId)
                && operatorIdentityType != IdentityType.ADMIN
                && !resource.getOwnerId().equals(operatorUserId)) {
            throw new ServiceException(ResourceError.COMMENT_DELETE_ACCESS_DENIED);
        }

        if (inlineComment.getItems().size() <= 1) {
            customInlineCommentRepository.deleteInlineComment(resourceId, request.getInlineCommentId());
        } else {
            customInlineCommentRepository.deleteItem(resourceId, request.getInlineCommentId(), request.getItemId());
        }
        log.info("inline comment item deleted. resourceId={} inlineCommentId={} itemId={} operatorUserId={}",
                resourceId, request.getInlineCommentId(), request.getItemId(), operatorUserId);
    }

    @Override
    public void changeInlineCommentResolveStatus(InlineCommentResolveRequest request,
                                                 String operatorUserId, IdentityType operatorIdentityType, boolean operatorHasEditAction) {
        String resourceId = request.getResourceId();
        ResourceItemEntity resource = resourceService.getResourceEntity(resourceId);
        ResourceInlineCommentEntity inlineComment = getInlineComment(request.getInlineCommentId(), resourceId);
        if (!isApplicable(inlineComment, request.getContentVersion())) {
            throw new ServiceException(ResourceError.COMMENT_NOT_FOUND);
        }

        // 管理员，资源所有者，评论者创建者（首条评论作者）本人，有编辑权限的用户可以解决评论
        if (!inlineComment.getCreatorId().equals(operatorUserId)
                && operatorIdentityType != IdentityType.ADMIN
                && !resource.getOwnerId().equals(operatorUserId)
                && !operatorHasEditAction) {
            throw new ServiceException(ResourceError.COMMENT_RESOLVE_ACCESS_DENIED);
        }

        if (request.isResolved()) {
            customInlineCommentRepository.resolveInlineComment(resourceId, request.getInlineCommentId(), operatorUserId);
        } else {
            customInlineCommentRepository.unresolveInlineComment(resourceId, request.getInlineCommentId());
        }

        log.info("inline comment resolved. resourceId={} inlineCommentId={} operatorUserId={}",
                resourceId, request.getInlineCommentId(), operatorUserId);
    }

    @Override
    public List<ResourceInlineCommentResponse> listInlineComments(String resourceId,
                                                                  Integer contentVersion,
                                                                  Boolean resolved,
                                                                  String operatorUserId) {
        resourceService.getResourceEntity(resourceId);

        List<ResourceInlineCommentEntity> inlineComments = customInlineCommentRepository.listInlineComments(
                resourceId, contentVersion, resolved
        );
        // 收集 UserId
        Set<Long> userIds = new LinkedHashSet<>();
        for (ResourceInlineCommentEntity inlineComment : inlineComments) {
            if (StringUtils.hasText(inlineComment.getCreatorId())) userIds.add(Long.valueOf(inlineComment.getCreatorId()));
            if (StringUtils.hasText(inlineComment.getResolvedBy())) userIds.add(Long.valueOf(inlineComment.getResolvedBy()));
            if (inlineComment.getItems() != null) {
                for (ResourceInlineCommentItemBase item : inlineComment.getItems()) {
                    // 收集评论作者 userIds
                    if (StringUtils.hasText(item.getAuthorId())) userIds.add(Long.valueOf(item.getAuthorId()));
                    // 收集评论互动人员 userIds
                    if (item.getReactions() != null) {
                        for (String reactionUserId : item.getReactions().keySet()) {
                            if (StringUtils.hasText(reactionUserId)) userIds.add(Long.valueOf(reactionUserId));
                        }
                    }
                }
            }
        }

        // 远程拉取用户信息
        Map<Long, UserDisplayBase> userMap = Collections.emptyMap();
        if (!userIds.isEmpty()) {
            try {
                Map<Long, UserDisplayBase> fetched = remoteUserService.getUserDisplayInfo(userIds.stream().toList()).getData();
                userMap = fetched == null ? Collections.emptyMap() : fetched;
            } catch (Exception e) {
                log.warn("inline comment user info batch degraded. userCount={}", userIds.size(), e);
            }
        }

        List<ResourceInlineCommentResponse> responses = new ArrayList<>();
        for (ResourceInlineCommentEntity entity : inlineComments) {

            List<InlineCommentItemResponse> commentItemResponses = new ArrayList<>();
            if (entity.getItems() != null) {
                for (ResourceInlineCommentItemBase commentItem : entity.getItems()) {
                    InlineCommentItemResponse commentItemResponse = BeanUtil.copyProperties(commentItem, InlineCommentItemResponse.class);
                    commentItemResponse.setAuthorInfo(userMap.get(Long.valueOf(commentItem.getAuthorId())));

                    Map<String, InlineCommentItemReactionGroupResponse> reactionGroupMap = new LinkedHashMap<>();
                    // 这条 item 有人点过表情，才开始组装 reactionGroups
                    if (commentItemResponse.getReactions() != null && !commentItemResponse.getReactions().isEmpty()) {
                        // 遍历每个用户的 reaction 列表，同一用户可对同一消息添加多个表情。
                        for (Map.Entry<String, List<ResourceInlineCommentItemReactionBase>> entry : commentItemResponse.getReactions().entrySet()) {
                            String reactionUserId = entry.getKey();
                            List<ResourceInlineCommentItemReactionBase> reactions = entry.getValue() == null ? Collections.emptyList() : entry.getValue();
                            for (ResourceInlineCommentItemReactionBase reaction : reactions) {
                                // 如果当前 emojiId 的分组还不存在，就创建一个，如果已经存在，就复用已有分组
                                InlineCommentItemReactionGroupResponse reactionGroup = reactionGroupMap.computeIfAbsent(reaction.getEmojiId(),
                                        emojiId -> InlineCommentItemReactionGroupResponse.builder().emojiId(emojiId).build());
                                reactionGroup.setCount(reactionGroup.getCount() + 1); // 表情计数 +1
                                // 如果当前遍历到的是当前登录用户的 reaction，就标记这一组
                                if (Objects.equals(reactionUserId, operatorUserId)) reactionGroup.setReactedByCurrentUser(true);
                                // 从前面批量查询好的 userMap 里取这个 reaction 用户的展示信息，放进该表情分组的 users 里
                                UserDisplayBase reactionUserInfo = userMap.get(Long.valueOf(reactionUserId));
                                if (reactionUserInfo != null) reactionGroup.getUsers().add(reactionUserInfo);
                            }
                        }
                    }
                    commentItemResponse.setReactionGroups(new ArrayList<>(reactionGroupMap.values()));
                    commentItemResponses.add(commentItemResponse);
                }
            }
            ResourceInlineCommentResponse commentResponse =  BeanUtil.copyProperties(entity, ResourceInlineCommentResponse.class);
            commentResponse.setCreatorInfo(userMap.get(Long.valueOf(entity.getCreatorId())));
            commentResponse.setItems(commentItemResponses);
            if (StringUtils.hasText(entity.getResolvedBy())) { // 仅处理已被解决的 Comment
                commentResponse.setResolvedByInfo(userMap.get(Long.valueOf(entity.getResolvedBy())));
            }
            responses.add(commentResponse);
        }
        return responses;
    }

    private ResourceInlineCommentEntity getInlineComment(String inlineCommentId, String resourceId) {
        return inlineCommentRepository.findByIdAndResourceId(inlineCommentId, resourceId)
                .orElseThrow(() -> new ServiceException(ResourceError.COMMENT_NOT_FOUND));
    }

    private boolean isApplicable(ResourceInlineCommentEntity inlineComment, Integer contentVersion) {
        if (contentVersion == null) {
            return true;
        }
        Integer from = inlineComment.getApplicableFromVersion();
        Integer to = inlineComment.getApplicableToVersion();
        return (from == null || from <= contentVersion) && (to == null || to >= contentVersion);
    }
}
