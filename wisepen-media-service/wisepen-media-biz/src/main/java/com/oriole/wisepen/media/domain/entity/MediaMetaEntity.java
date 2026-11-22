package com.oriole.wisepen.media.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "wisepen_media_meta")
public class MediaMetaEntity {

    @Id
    private String mediaId;

    private Long durationMs;

    private Integer width;

    private Integer height;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaHlsRendition {
        /** 主清单引用的媒体清单文件名，如 "0.m3u8" */
        private String playlistName;
        private String bandwidth;
        private String resolution;
        private String codecs;
        /** 该档各分片时长（秒），顺序与 MediaHlsRenditionKeys.segmentObjectKeys 一致 */
        private List<Double> segmentDurations;
    }

    /** HLS 各档结构信息 */
    private List<MediaHlsRendition> hls;
}
