package com.oriole.wisepen.file.service;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

public interface OfficeConversionService {

    /**
     * Convert office document to PDF
     * @param source source file
     * @param target target pdf file
     */
    void convertToPdf(File source, File target);
}
