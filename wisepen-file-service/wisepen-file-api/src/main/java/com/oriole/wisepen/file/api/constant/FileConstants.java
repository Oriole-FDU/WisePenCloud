package com.oriole.wisepen.file.api.constant;

import java.util.Set;

/**
 * 文件常量类
 *
 * @author Ian.xiong
 */
public class FileConstants {
    public static final String BUCKET_NAME = "wisepen-files";

    /** 单文件最大体积：100MB */
    public static final long MAX_FILE_SIZE = 100L * 1024 * 1024;

    /** 上传状态 */
    public static final Integer UPLOAD_STATUS_PROCESSING = 0;
    public static final Integer UPLOAD_STATUS_AVAILABLE = 1;
    public static final Integer UPLOAD_STATUS_FAILED = 2;

    /** 允许上传的文件扩展名白名单 */
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "doc", "docx", "ppt", "pptx", "xls", "xlsx", "pdf"
    );

    /** Office 文档扩展名（需要转换 PDF） */
    public static final Set<String> OFFICE_EXTENSIONS = Set.of(
            "doc", "docx", "ppt", "pptx", "xls", "xlsx"
    );

    // Redis queue keys
    public static final String CONVERT_QUEUE_KEY = "wisepen:file:convert:queue";
    public static final String UPLOAD_QUEUE_KEY = "wisepen:file:upload:queue";
}
