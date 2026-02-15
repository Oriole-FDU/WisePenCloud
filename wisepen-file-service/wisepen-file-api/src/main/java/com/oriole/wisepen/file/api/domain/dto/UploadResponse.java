package com.oriole.wisepen.file.api.domain.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class UploadResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer status;

    private Boolean isFastUpload;

    public void setFastUpload(Boolean fastUpload) {
        this.isFastUpload = fastUpload;
    }

    public static UploadResponse processing() {
        UploadResponse response = new UploadResponse();
        response.setStatus(0);
        response.setFastUpload(false);
        return response;
    }

    public static UploadResponse fastUpload() {
        UploadResponse response = new UploadResponse();
        response.setStatus(1);
        response.setFastUpload(true);
        return response;
    }
}
