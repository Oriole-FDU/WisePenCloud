package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.user.api.domain.dto.req.GroupAnnouncementAttachmentUploadInitRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupAnnouncementCreateRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupAnnouncementUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.res.GroupAnnouncementAttachmentUploadInitResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupAnnouncementDetailResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupAnnouncementListItemResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupAnnouncementReadMemberResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupAnnouncementReadStatsResponse;
import com.oriole.wisepen.user.service.IGroupAnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "小组公告", description = "小组公告发布、查看、已读统计与私有附件访问")
@RestController
@RequestMapping("/group/announcement")
@RequiredArgsConstructor
@Validated
@CheckLogin
public class GroupAnnouncementController {

    private final IGroupAnnouncementService announcementService;

    @Operation(
            summary = "初始化公告附件上传",
            description = """
                    - 用途：为小组公告图片或普通附件申请私有对象存储直传凭证。
                    - 请求：groupId 指定目标小组；md5、extension 和 expectedSize 描述待上传文件。
                    - 约束：当前用户必须是目标小组 OWNER 或 ADMIN；文件上传成功后仍需在发布或更新公告时关联 objectKey。
                    - 处理：使用私有小组公告存储场景申请上传凭证，不创建公告或附件关联记录。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是 OWNER/ADMIN -> PermissionError.PERMISSION_DENIED；小组不存在 -> UserError.GROUP_NOT_EXIST；存储服务不可用或返回空结果 -> UserError.GROUP_ANNOUNCEMENT_ATTACHMENT_VALIDATION_FAILED。
                    - 响应：返回 objectKey、是否秒传和直传所需凭证。
                    """
    )
    @Log(title = "初始化公告附件上传", businessType = BusinessType.INSERT, isSaveResponseData = false)
    @PostMapping("/initAttachmentUpload")
    public R<GroupAnnouncementAttachmentUploadInitResponse> initAttachmentUpload(
            @RequestBody @Valid GroupAnnouncementAttachmentUploadInitRequest req) {
        SecurityContextHolder.assertGroupRole(req.getGroupId(), GroupRoleType.OWNER, GroupRoleType.ADMIN);
        return R.ok(announcementService.initAttachmentUpload(req, SecurityContextHolder.getUserId()));
    }

    @Operation(
            summary = "发布小组公告",
            description = """
                    - 用途：由小组 OWNER 或 ADMIN 发布一条对当前组成员可见的公告。
                    - 请求：groupId 指定目标小组；content 是公告正文；attachments 是已上传附件的完整关联列表。
                    - 约束：当前用户必须是目标小组 OWNER 或 ADMIN；附件 objectKey 必须属于该小组的私有公告存储目录且已存在。
                    - 处理：创建公告和附件关联记录，并在事务提交后向操作时的当前组成员发送站内信；不创建草稿或定时发布任务。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是 OWNER/ADMIN -> PermissionError.PERMISSION_DENIED；小组不存在 -> UserError.GROUP_NOT_EXIST；附件无效 -> UserError.GROUP_ANNOUNCEMENT_ATTACHMENT_INVALID；附件校验失败 -> UserError.GROUP_ANNOUNCEMENT_ATTACHMENT_VALIDATION_FAILED。
                    - 响应：返回新建公告 ID。
                    """
    )
    @Log(title = "发布小组公告", businessType = BusinessType.INSERT)
    @PostMapping("/create")
    public R<Long> createAnnouncement(@RequestBody @Valid GroupAnnouncementCreateRequest req) {
        SecurityContextHolder.assertGroupRole(req.getGroupId(), GroupRoleType.OWNER, GroupRoleType.ADMIN);
        return R.ok(announcementService.createAnnouncement(req, SecurityContextHolder.getUserId()));
    }

    @Operation(
            summary = "更新小组公告",
            description = """
                    - 用途：更新已发布小组公告的正文和附件。
                    - 请求：groupId 和 announcementId 指定目标公告；content 是更新后的正文；attachments 是更新后的完整附件列表。
                    - 约束：当前用户必须是目标小组 OWNER 或 ADMIN，且必须是公告发布者本人；附件必须属于目标小组的私有公告存储目录。
                    - 处理：更新公告和附件关联记录，清空该公告全部已读记录，并在事务提交后向操作时的当前组成员发送站内信。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是 OWNER/ADMIN 或不是发布者 -> PermissionError.PERMISSION_DENIED；公告不存在或不属于目标小组 -> UserError.GROUP_ANNOUNCEMENT_NOT_FOUND；附件无效 -> UserError.GROUP_ANNOUNCEMENT_ATTACHMENT_INVALID；附件校验失败 -> UserError.GROUP_ANNOUNCEMENT_ATTACHMENT_VALIDATION_FAILED。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "更新小组公告", businessType = BusinessType.UPDATE)
    @PostMapping("/update")
    public R<Void> updateAnnouncement(@RequestBody @Valid GroupAnnouncementUpdateRequest req) {
        SecurityContextHolder.assertGroupRole(req.getGroupId(), GroupRoleType.OWNER, GroupRoleType.ADMIN);
        announcementService.updateAnnouncement(req, SecurityContextHolder.getUserId());
        return R.ok();
    }

    @Operation(
            summary = "删除小组公告",
            description = """
                    - 用途：逻辑删除一条已发布的小组公告。
                    - 请求：groupId 和 announcementId 指定目标公告。
                    - 约束：当前用户必须是目标小组 OWNER 或 ADMIN，且必须是公告发布者本人。
                    - 处理：逻辑删除公告主记录；不删除审计所需的附件关联和已读记录，也不发送站内信。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是 OWNER/ADMIN 或不是发布者 -> PermissionError.PERMISSION_DENIED；公告不存在或不属于目标小组 -> UserError.GROUP_ANNOUNCEMENT_NOT_FOUND。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "删除小组公告", businessType = BusinessType.DELETE)
    @PostMapping("/delete")
    public R<Void> deleteAnnouncement(@RequestParam("groupId") Long groupId,
                                      @RequestParam("announcementId") Long announcementId) {
        SecurityContextHolder.assertGroupRole(groupId, GroupRoleType.OWNER, GroupRoleType.ADMIN);
        announcementService.deleteAnnouncement(groupId, announcementId, SecurityContextHolder.getUserId());
        return R.ok();
    }

    @Operation(
            summary = "分页查询小组公告",
            description = """
                    - 用途：为小组公告列表展示当前组成员可见的有效公告。
                    - 请求：groupId 指定目标小组；page 和 size 控制分页。
                    - 约束：当前用户必须属于目标小组。
                    - 处理：按发布时间倒序分页查询未删除公告，批量补充发布者、附件数量和当前用户已读状态；不生成附件下载地址。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不属于目标小组 -> PermissionError.PERMISSION_DENIED。
                    - 响应：返回公告分页列表。
                    """
    )
    @GetMapping("/list")
    public R<PageR<GroupAnnouncementListItemResponse>> listAnnouncements(
            @RequestParam("groupId") Long groupId,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {
        SecurityContextHolder.assertInGroup(groupId);
        return R.ok(announcementService.listAnnouncements(groupId, SecurityContextHolder.getUserId(), page, size));
    }

    @Operation(
            summary = "获取公告详情",
            description = """
                    - 用途：展示公告正文和附件元数据，并将当前成员标记为已读。
                    - 请求：groupId 和 announcementId 指定目标公告。
                    - 约束：当前用户必须属于目标小组；公告必须存在、未删除且属于目标小组。
                    - 处理：返回公告详情和附件元数据；首次进入详情时写入已读时间，重复访问保持首次已读时间；不返回永久文件链接。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不属于目标小组 -> PermissionError.PERMISSION_DENIED；公告不存在或不属于目标小组 -> UserError.GROUP_ANNOUNCEMENT_NOT_FOUND。
                    - 响应：返回公告详情。
                    """
    )
    @GetMapping("/detail")
    public R<GroupAnnouncementDetailResponse> getAnnouncementDetail(
            @RequestParam("groupId") Long groupId,
            @RequestParam("announcementId") Long announcementId) {
        SecurityContextHolder.assertInGroup(groupId);
        return R.ok(announcementService.getAnnouncementDetail(groupId, announcementId, SecurityContextHolder.getUserId()));
    }

    @Operation(
            summary = "获取公告附件地址",
            description = """
                    - 用途：为当前组成员生成一条公告附件的短时访问地址。
                    - 请求：groupId、announcementId 和 attachmentId 指定目标附件。
                    - 约束：当前用户必须属于目标小组；公告和附件必须存在且相互关联。
                    - 处理：在组成员校验后向文件存储服务申请 900 秒有效的下载地址；不返回 objectKey。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不属于目标小组 -> PermissionError.PERMISSION_DENIED；公告不存在或不属于目标小组 -> UserError.GROUP_ANNOUNCEMENT_NOT_FOUND；附件无效 -> UserError.GROUP_ANNOUNCEMENT_ATTACHMENT_INVALID；文件存储不可用 -> UserError.GROUP_ANNOUNCEMENT_ATTACHMENT_VALIDATION_FAILED。
                    - 响应：返回短时下载地址。
                    """
    )
    @GetMapping("/attachment/downloadUrl")
    public R<String> getAttachmentDownloadUrl(
            @RequestParam("groupId") Long groupId,
            @RequestParam("announcementId") Long announcementId,
            @RequestParam("attachmentId") Long attachmentId) {
        SecurityContextHolder.assertInGroup(groupId);
        return R.ok(announcementService.getAttachmentDownloadUrl(groupId, announcementId, attachmentId));
    }

    @Operation(
            summary = "获取公告已读统计",
            description = """
                    - 用途：供公告发布者查看当前小组成员对公告的已读和未读人数。
                    - 请求：groupId 和 announcementId 指定目标公告。
                    - 约束：当前用户必须属于目标小组且必须是公告发布者本人。
                    - 处理：按当前成员关系统计已读和未读人数，不使用小组 memberCount 字段。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不属于目标小组或不是公告发布者 -> PermissionError.PERMISSION_DENIED；公告不存在或不属于目标小组 -> UserError.GROUP_ANNOUNCEMENT_NOT_FOUND。
                    - 响应：返回已读人数和未读人数。
                    """
    )
    @GetMapping("/readStats")
    public R<GroupAnnouncementReadStatsResponse> getReadStats(
            @RequestParam("groupId") Long groupId,
            @RequestParam("announcementId") Long announcementId) {
        SecurityContextHolder.assertInGroup(groupId);
        return R.ok(announcementService.getReadStats(groupId, announcementId, SecurityContextHolder.getUserId()));
    }

    @Operation(
            summary = "分页查询公告已读成员",
            description = """
                    - 用途：供公告发布者分页查看当前小组成员的已读或未读状态。
                    - 请求：groupId 和 announcementId 指定目标公告；read 为 true 查询已读成员，false 查询未读成员；page 和 size 控制分页。
                    - 约束：当前用户必须属于目标小组且必须是公告发布者本人。
                    - 处理：按当前成员关系返回已读或未读成员；已读成员返回首次 readTime，未读成员 readTime 为空。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不属于目标小组或不是公告发布者 -> PermissionError.PERMISSION_DENIED；公告不存在或不属于目标小组 -> UserError.GROUP_ANNOUNCEMENT_NOT_FOUND。
                    - 响应：返回成员分页列表。
                    """
    )
    @GetMapping("/readMembers")
    public R<PageR<GroupAnnouncementReadMemberResponse>> listReadMembers(
            @RequestParam("groupId") Long groupId,
            @RequestParam("announcementId") Long announcementId,
            @RequestParam("read") boolean read,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {
        SecurityContextHolder.assertInGroup(groupId);
        return R.ok(announcementService.listReadMembers(
                groupId, announcementId, SecurityContextHolder.getUserId(), read, page, size));
    }
}
