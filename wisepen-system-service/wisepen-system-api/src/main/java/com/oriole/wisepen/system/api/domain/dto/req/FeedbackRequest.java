package com.oriole.wisepen.system.api.domain.dto.req;

import lombok.Data;

/**
 * @author Xiong Heng
 */
@Data
public class FeedbackRequest {
    private String content;
    private String contact;
    private String imageUrl;

    private boolean bugReport;
    private boolean suggestion;
    private boolean consultation;
    private boolean complaint;
    private boolean other;
}
