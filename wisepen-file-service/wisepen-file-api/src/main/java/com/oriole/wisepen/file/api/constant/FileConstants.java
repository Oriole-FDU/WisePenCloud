package com.oriole.wisepen.file.api.constant;

/**
 * 文件常量类
 *
 * @author Ian.Xiong
 */
public class FileConstants {
    public static final String BUCKET_NAME = "wisepen-files";
    
    // Upload status
    public static final Integer UPLOAD_STATUS_PROCESSING = 0;
    public static final Integer UPLOAD_STATUS_AVAILABLE = 1;
    public static final Integer UPLOAD_STATUS_FAILED = 2;

    public static final java.util.Set<String> OFFICE_EXTENSIONS = java.util.Collections.unmodifiableSet(
            new java.util.HashSet<>(java.util.Arrays.asList
                            ("doc", "docx", "ppt",
                            "pptx", "xls", "xlsx"))
    );

    // Redis queue keys
    public static final String CONVERT_QUEUE_KEY = "wisepen:file:convert:queue";
    public static final String UPLOAD_QUEUE_KEY = "wisepen:file:upload:queue";
}
