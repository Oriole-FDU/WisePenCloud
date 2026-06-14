package com.oriole.wisepen.resource.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import com.oriole.wisepen.resource.domain.dto.req.*;
import com.oriole.wisepen.resource.domain.dto.res.FavoriteCollectionResponse;
import com.oriole.wisepen.resource.domain.dto.res.FavoriteItemResponse;
import com.oriole.wisepen.resource.service.IFavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "收藏管理", description = "资源收藏关系与收藏集合标签管理")
@RestController
@RequestMapping("/resource/favorite")
@RequiredArgsConstructor
@CheckLogin
@Validated
public class FavoriteController {

    private final IFavoriteService favoriteService;

    @Operation(
            summary = "更改资源收藏状态",
            description = """
                    - 用途：维护当前用户对单个资源的收藏关系，以及该资源归属的收藏集合标签。
                    - 请求：resourceId 指定目标资源；favorite=true 表示收藏或更新收藏集合标签，favorite=false 表示取消收藏；collectionIds 仅在 favorite=true 时生效，未传或为空时自动使用默认收藏集合。
                    - 约束：当前用户必须已登录；目标资源必须存在且未被软删除；favorite=true 时传入的 collectionIds 必须全部归属当前用户。
                    - 处理：favorite=true 时创建或更新当前用户对该资源的唯一收藏引用，并同步维护资源 favoriteCount 与收藏集合 itemCount；favorite=false 时删除收藏引用并递减相关计数；不修改资源本身。
                    - 失败：目标资源不存在或已删除 -> ResourceError.RESOURCE_NOT_FOUND；目标收藏集合不存在或不归属当前用户 -> ResourceError.FAVORITE_COLLECTION_NOT_FOUND。
                    - 响应：成功时返回空结果。
                    """
    )
    @PostMapping("/changeResourceFavoriteStatus")
    @Log(title = "更改资源收藏状态", businessType = BusinessType.UPDATE)
    public R<Void> changeResourceFavoriteStatus(@Validated @RequestBody ResourceFavoriteRequest request) {
        String userId = SecurityContextHolder.getUserId().toString();
        favoriteService.changeResourceFavoriteStatus(request, userId);
        return R.ok();
    }

    @Operation(
            summary = "查询当前用户收藏集合列表",
            description = """
                    - 用途：获取当前用户创建的所有收藏集合标签，用于收藏集合列表展示和收藏操作时的集合选择。
                    - 请求：无入参，取当前登录用户的全部收藏集合。
                    - 约束：当前用户必须已登录。
                    - 处理：确保当前用户存在默认收藏集合；查询当前用户全部收藏集合，按默认集合置顶、创建时间倒序排列；每条返回集合基本信息和已维护的 itemCount；不加载资源详情。
                    - 失败：无业务失败点。
                    - 响应：返回收藏集合列表，默认集合排在最前。
                    """
    )
    @GetMapping("/listCollections")
    public R<List<FavoriteCollectionResponse>> listCollections() {
        String userId = SecurityContextHolder.getUserId().toString();
        return R.ok(favoriteService.listCollections(userId));
    }

    @Operation(
            summary = "新建收藏集合",
            description = """
                    - 用途：为当前用户创建一个自定义收藏集合标签，用于分类管理收藏资源。
                    - 请求：collectionName 为集合名称，不能为空；description 为可选描述，不传表示无描述。
                    - 约束：当前用户必须已登录；collectionName 不能为空字符串。
                    - 处理：创建新收藏集合，isDefault 固定为 false，itemCount 初始为 0；不自动添加任何资源；返回服务端生成的 collectionId。
                    - 失败：无业务失败点。
                    - 响应：返回服务端生成的收藏集合 ID（ObjectId 字符串）。
                    """
    )
    @PostMapping("/createCollection")
    @Log(title = "收藏管理", businessType = BusinessType.INSERT)
    public R<String> createCollection(@Validated @RequestBody FavoriteCollectionCreateRequest request) {
        String userId = SecurityContextHolder.getUserId().toString();
        return R.ok(favoriteService.createCollection(request, userId));
    }

    @Operation(
            summary = "修改收藏集合名称或描述",
            description = """
                    - 用途：更新收藏集合的展示名称或描述，不影响集合内的资源引用和收藏关系。
                    - 请求：collectionId、collectionName、description 均在请求体中；Full Update 语义，必须同时传入 collectionName 和 description，description 传 null 表示清除描述。
                    - 约束：当前用户必须已登录；目标收藏集合必须存在且归属当前用户；collectionName 不能为空字符串。
                    - 处理：更新集合名称和描述；不修改 isDefault 标记、itemCount、收藏资源关系或创建时间。
                    - 失败：目标收藏集合不存在或不归属当前用户 -> ResourceError.FAVORITE_COLLECTION_NOT_FOUND。
                    - 响应：成功时返回空结果。
                    """
    )
    @PostMapping("/updateCollectionInfo")
    @Log(title = "收藏管理", businessType = BusinessType.UPDATE)
    public R<Void> updateCollectionInfo(@Validated @RequestBody FavoriteCollectionInfoUpdateRequest request) {
        String userId = SecurityContextHolder.getUserId().toString();
        favoriteService.updateCollectionInfo(request, userId);
        return R.ok();
    }

    @Operation(
            summary = "删除收藏集合",
            description = """
                    - 用途：删除当前用户的指定收藏集合标签，并处理原本只归属该集合的收藏资源。
                    - 请求：collectionId 在请求体中指定目标集合；keepResourcesToDefault=false 或未传时，将失去最后一个集合标签的收藏资源转入默认收藏集合；keepResourcesToDefault=true 时，直接删除这些收藏关系。
                    - 约束：当前用户必须已登录；目标收藏集合必须存在且归属当前用户；默认收藏集合不可删除。
                    - 处理：从受影响的收藏引用中移除目标 collectionId；仍有其他集合标签的引用继续保留；无其他集合标签的引用按 keepResourcesToDefault 规则转入默认集合或删除，并同步维护资源 favoriteCount 与收藏集合 itemCount；不删除资源本身。
                    - 失败：目标收藏集合不存在或不归属当前用户 -> ResourceError.FAVORITE_COLLECTION_NOT_FOUND；尝试删除默认收藏集合 -> ResourceError.DEFAULT_COLLECTION_CANNOT_DELETE。
                    - 响应：成功时返回空结果。
                    """
    )
    @PostMapping("/deleteCollection")
    @Log(title = "收藏管理", businessType = BusinessType.DELETE)
    public R<Void> deleteCollection(@Validated @RequestBody FavoriteCollectionDeleteRequest request) {
        String userId = SecurityContextHolder.getUserId().toString();
        favoriteService.deleteCollection(request, userId);
        return R.ok();
    }

    @Operation(
            summary = "分页查询已收藏资源",
            description = """
                    - 用途：分页展示当前用户已收藏的资源，可作为总收藏视图，也可按收藏集合标签过滤。
                    - 请求：collectionId 为可选 Query 参数；不传时查询当前用户全部收藏资源，传入时只查询归属该收藏集合的资源；page 为页码（从 1 开始，默认 1）；size 为每页条数（默认 20，最大 100）。
                    - 约束：当前用户必须已登录；collectionId 传入时目标收藏集合必须存在且归属当前用户；page 不能小于 1；size 不能超过 100。
                    - 处理：直接分页查询收藏引用集合，按 favoritedAt 倒序、id 倒序返回；批量加载当前页资源详情；资源不存在或已删除时返回 accessible=false 且仅保留 resourceId；不跨收藏集合做内存去重。
                    - 失败：目标收藏集合不存在或不归属当前用户 -> ResourceError.FAVORITE_COLLECTION_NOT_FOUND。
                    - 响应：返回分页收藏条目列表；每条包含资源详情、favoritedAt、collectionIds 和 accessible。
                    """
    )
    @GetMapping("/listFavoritedResources")
    public R<PageR<FavoriteItemResponse>> listFavoritedResources(
            @RequestParam(required = false) String collectionId,
            @Min(value = 1, message = ResourceValidationMsg.PAGE_MIN_INVALID) @RequestParam(defaultValue = "1") int page,
            @Min(value = 1, message = ResourceValidationMsg.SIZE_MIN_INVALID) @Max(value = 100, message = ResourceValidationMsg.SIZE_MAX_INVALID) @RequestParam(defaultValue = "20") int size) {
        String userId = SecurityContextHolder.getUserId().toString();
        return R.ok(favoriteService.listFavoritedResources(collectionId, page, size, userId));
    }
}
