package com.oriole.wisepen.file.storage.controller;

import cn.hutool.core.io.FileUtil;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.exception.FileStorageError;
import com.oriole.wisepen.file.storage.service.IStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.EnumSet;

import static com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum.*;

@Tag(name = "文件存储", description = "用户文件与图片上传代理接口")
@RestController
@RequestMapping("/storage/")
@RequiredArgsConstructor
@CheckRole
public class StorageController {

    private final IStorageService storageService;

    /**
     * 图床代理上传
     * @param file    图片文件
     * @param scene   业务场景
     * @param bizTag  业务隔离标识
     */
    @Operation(
            summary = "上传图床图片",
            description = """
                    - 用途：代理当前用户上传小尺寸图片并生成可访问的存储记录。
                    - 请求：file 为图片文件；scene 指定图片存储场景；bizTag 为业务隔离目录标识，可为空。
                    - 约束：当前用户必须已登录；文件扩展名只能是 jpg、jpeg、png、gif 或 webp；scene 只能属于用户公开图片、小组公开图片或笔记私有图片场景；文件大小不能超过小文件代理上传上限。
                    - 处理：校验文件类型和场景后直接上传到主存储配置，创建 AVAILABLE 状态的存储记录；不走预签名直传和上传完成回调流程。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；文件类型不支持 -> FileStorageError.CANNOT_SUPPORT_FILE_TYPE；场景不支持 -> FileStorageError.CANNOT_SUPPORT_FILE_STORAGE_SCENE；文件过大 -> FileStorageError.FILE_SIZE_ABOVE_UPPER_BOUND；存储上传失败 -> FileStorageError.STORAGE_PROVIDER_UPLOAD_FILE_FAILED。
                    - 响应：返回存储记录、objectKey 和访问域名信息。
                    """
    )
    @PostMapping("/imageUpload")
    public R<StorageRecordDTO> uploadImageProxy(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "scene", defaultValue = "true") StorageSceneEnum scene,
            @RequestParam(value = "bizTag", required = false) String bizTag) {

        String extension = FileUtil.extName(file.getOriginalFilename()).toLowerCase();
        if (!Arrays.asList("jpg", "jpeg", "png", "gif", "webp").contains(extension)) {
            throw new ServiceException(FileStorageError.CANNOT_SUPPORT_FILE_TYPE);
        }
        if (!EnumSet.of(PUBLIC_IMAGE_FOR_USER, PUBLIC_IMAGE_FOR_GROUP, PRIVATE_IMAGE_FOR_NOTE).contains(scene)) {
            throw new ServiceException(FileStorageError.CANNOT_SUPPORT_FILE_STORAGE_SCENE);
        }
        StorageRecordDTO record = storageService.uploadSmallFileProxy(file, scene, bizTag);
        return R.ok(record);
    }
}
