package com.oriole.wisepen.file.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 文件内部接口（仅供微服务内部调用，网关应屏蔽 /internal/** 外网访问）
 *
 * @author Ian.xiong
 */
@RestController
@RequestMapping("/internal/file")
@RequiredArgsConstructor
public class InternalFileController {

    private final FileService fileService;

    
}
