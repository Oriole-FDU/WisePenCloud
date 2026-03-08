package com.oriole.wisepen.file.service.impl;

import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.exception.FileErrorCode;
import com.oriole.wisepen.file.service.OfficeConversionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.office.OfficeException;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * Office 文档转换 service 实现类
 *
 * @author Ian.xiong
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeConversionServiceImpl implements OfficeConversionService {

    private final DocumentConverter documentConverter;

    @Override
    public void convertToPdf(File source, File target) {
        long start = System.currentTimeMillis();
        log.info("Starting conversion: {} ({} bytes) -> {}", 
                source.getName(), source.length(), target.getName());

        try {
            documentConverter.convert(source)
                    .to(target)
                    .execute();

            long duration = System.currentTimeMillis() - start;
            log.info("Success: Conversion finished in {} ms.", duration);

        } catch (OfficeException e) {
            long duration = System.currentTimeMillis() - start;
            log.error("FAILED: Conversion error after {} ms. File: {} - Error: {}", 
                    duration, source.getName(), e.getMessage(), e);
            throw new ServiceException(FileErrorCode.FILE_CONVERT_ERROR);
        }
    }
}
