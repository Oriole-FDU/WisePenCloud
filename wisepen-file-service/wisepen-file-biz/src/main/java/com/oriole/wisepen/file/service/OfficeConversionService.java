package com.oriole.wisepen.file.service;
import java.io.File;

/**
 * @author Ian.xiong
 */
public interface OfficeConversionService {

    /**
     * Convert office document to PDF
     * @param source source file
     * @param target target PDF file
     */
    void convertToPdf(File source, File target);
}
