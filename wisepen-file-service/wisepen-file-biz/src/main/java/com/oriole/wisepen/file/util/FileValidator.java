package com.oriole.wisepen.file.util;

import com.oriole.wisepen.file.api.constant.FileConstants;
import com.oriole.wisepen.file.exception.FileErrorCode;
import com.oriole.wisepen.common.core.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件校验工具类
 *
 * @author Ian.Xiong
 */
@Slf4j
public class FileValidator {

    private static final Map<String, String> MAGIC_NUMBER_MAP = new HashMap<>();
    private static final int MAGIC_NUMBER_LENGTH = 8;
    private static final int BUFFER_SIZE = 8192;

    static {
        MAGIC_NUMBER_MAP.put("doc", "D0CF11E0A1B11AE1");
        MAGIC_NUMBER_MAP.put("docx", "504B0304");
        MAGIC_NUMBER_MAP.put("ppt", "D0CF11E0A1B11AE1");
        MAGIC_NUMBER_MAP.put("pptx", "504B0304");
        MAGIC_NUMBER_MAP.put("xls", "D0CF11E0A1B11AE1");
        MAGIC_NUMBER_MAP.put("xlsx", "504B0304");
        MAGIC_NUMBER_MAP.put("pdf", "25504446");
    }

    /**
     * 校验文件大小（≤ 100MB）
     */
    public static void validateFileSize(MultipartFile file) {
        if (file.getSize() > FileConstants.MAX_FILE_SIZE) {
            throw new ServiceException(FileErrorCode.FILE_SIZE_EXCEEDED);
        }
    }

    /**
     * 校验文件类型（白名单 + 魔数校验）
     */
    public static void validateFileType(MultipartFile file, String extension) {
        if (extension == null || extension.isEmpty()) {
            throw new ServiceException(FileErrorCode.FILE_TYPE_NOT_ALLOWED);
        }

        extension = extension.toLowerCase();

        if (!FileConstants.ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ServiceException(FileErrorCode.FILE_TYPE_NOT_ALLOWED);
        }

        validateMagicNumber(file, extension);
    }

    private static void validateMagicNumber(MultipartFile file, String extension) {
        String expectedMagic = MAGIC_NUMBER_MAP.get(extension);
        if (expectedMagic == null) {
            return;
        }

        try (InputStream is = file.getInputStream()) {
            byte[] buffer = new byte[MAGIC_NUMBER_LENGTH];
            int read = is.read(buffer);

            if (read < expectedMagic.length() / 2) {
                throw new ServiceException(FileErrorCode.FILE_MAGIC_NUMBER_MISMATCH);
            }

            String actualMagic = bytesToHex(Arrays.copyOf(buffer, read));

            if (!actualMagic.toUpperCase().startsWith(expectedMagic.toUpperCase())) {
                log.warn("Magic number mismatch. Expected: {}, Actual: {}, Extension: {}",
                        expectedMagic, actualMagic, extension);
                throw new ServiceException(FileErrorCode.FILE_MAGIC_NUMBER_MISMATCH);
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error validating magic number", e);
            throw new ServiceException(FileErrorCode.FILE_READ_ERROR);
        }
    }

    public static String calculateMd5(MultipartFile file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            try (InputStream is = file.getInputStream()) {
                while ((read = is.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                }
            }
            return bytesToHex(md.digest());
        } catch (Exception e) {
            log.error("Error calculating MD5", e);
            throw new ServiceException(FileErrorCode.FILE_READ_ERROR);
        }
    }

    public static String calculateMd5(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            try (InputStream is = Files.newInputStream(file.toPath())) {
                while ((read = is.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                }
            }
            return bytesToHex(md.digest());
        } catch (Exception e) {
            log.error("Error calculating MD5 for file: {}", file.getAbsolutePath(), e);
            throw new ServiceException(FileErrorCode.FILE_READ_ERROR);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
