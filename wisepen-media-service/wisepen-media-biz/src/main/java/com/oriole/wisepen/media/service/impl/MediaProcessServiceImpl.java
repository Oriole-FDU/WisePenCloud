package com.oriole.wisepen.media.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import com.oriole.wisepen.media.api.domain.base.MediaStatus;
import com.oriole.wisepen.media.api.domain.mq.MediaProcessTaskMessage;
import com.oriole.wisepen.media.api.domain.mq.MediaReadyMessage;
import com.oriole.wisepen.media.api.enums.MediaStatusEnum;
import com.oriole.wisepen.media.config.MediaProperties;
import com.oriole.wisepen.media.domain.MediaPackagingResult;
import com.oriole.wisepen.media.domain.entity.MediaInfoEntity;
import com.oriole.wisepen.media.exception.MediaError;
import com.oriole.wisepen.media.mq.KafkaMediaEventPublisher;
import com.oriole.wisepen.media.repository.MediaInfoRepository;
import com.oriole.wisepen.media.service.IMediaProcessService;
import com.oriole.wisepen.resource.domain.dto.ResourceCreateReqDTO;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaProcessServiceImpl implements IMediaProcessService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final MediaInfoRepository mediaInfoRepository;
    private final KafkaMediaEventPublisher eventPublisher;
    private final RemoteResourceService remoteResourceService;
    private final RemoteStorageService remoteStorageService;
    private final MediaProperties mediaProperties;
    private final ObjectMapper objectMapper;

    @Override
    public void processMedia(MediaProcessTaskMessage message) {
        // 消费异步处理消息时重新读取媒体记录，避免使用消息里的过期状态做决策
        String mediaId = message.getMediaId();
        MediaInfoEntity entity = mediaInfoRepository.findById(mediaId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        MediaStatusEnum status = entity.getMediaStatus() != null ? entity.getMediaStatus().getStatus() : null;

        // 资源注册阶段失败或超时时，重试只补注册资源，不重复下载和转码源文件
        if (status == MediaStatusEnum.REGISTERING_RES || status == MediaStatusEnum.REGISTERING_RES_TIMEOUT) {
            finalizeToReady(mediaId);
            return;
        }

        // 只有 UPLOADED 状态允许进入处理，防止重复消费消息导致重复封装或覆盖产物
        if (status != MediaStatusEnum.UPLOADED) {
            log.info("media process skipped because status mismatched. mediaId={} status={}",
                    entity.getMediaId(), status);
            return;
        }

        updateStatus(mediaId, new MediaStatus(MediaStatusEnum.PROBING));

        // packaging 只生成媒体基础产物：图片尺寸、视频源 HLS 与封面；音频只读取音频流元数据
        MediaPackagingResult packagingResult;
        if (entity.getResourceType() == ResourceType.IMAGE) {
            updateStatus(mediaId, new MediaStatus(MediaStatusEnum.PACKAGING));
            packagingResult = packageImage(entity);
        } else if (entity.getResourceType() == ResourceType.VIDEO) {
            updateStatus(mediaId, new MediaStatus(MediaStatusEnum.PACKAGING));
            packagingResult = packageVideo(entity);
        } else if (entity.getResourceType() == ResourceType.AUDIO) {
            packagingResult = packageAudio(entity);
        } else {
            throw new ServiceException(MediaError.CANNOT_SUPPORT_FILE_TYPE);
        }
        mediaInfoRepository.updatePackagingResultById(mediaId,
                packagingResult.getSourceHlsPrefix(),
                packagingResult.getSourceHlsObjectKeys(),
                packagingResult.getPreviewObjectKey(),
                packagingResult.getDurationMs(),
                packagingResult.getWidth(),
                packagingResult.getHeight());

        // 基础产物和元数据落库后再注册 resource，避免资源服务暴露未完成媒体
        finalizeToReady(mediaId);
    }

    @Override
    public void updateStatus(String mediaId, MediaStatus status) {
        // 先读取旧状态只用于日志追踪；状态流转约束由调用方业务流程保证
        MediaInfoEntity entity = mediaInfoRepository.findById(mediaId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        MediaStatusEnum from = entity.getMediaStatus() != null ? entity.getMediaStatus().getStatus() : null;
        mediaInfoRepository.updateStatusById(mediaId, status);
        log.info("media status changed. mediaId={} resourceId={} from={} to={}",
                mediaId, entity.getResourceId(), from, status.getStatus());
    }

    @Override
    public void prepareProcessRetry(String mediaId) {
        // 资源注册阶段、终态和失败态不回拨；这些状态有各自的重试或出口语义
        MediaInfoEntity entity = mediaInfoRepository.findById(mediaId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        MediaStatusEnum status = entity.getMediaStatus() != null ? entity.getMediaStatus().getStatus() : null;
        if (status == MediaStatusEnum.REGISTERING_RES
                || status == MediaStatusEnum.REGISTERING_RES_TIMEOUT
                || status == MediaStatusEnum.READY
                || status == MediaStatusEnum.FAILED) {
            return;
        }
        // 处理链路中的中间态统一回拨到 UPLOADED，后续由异步处理重新推进
        if (status == null
                || status == MediaStatusEnum.UPLOADING
                || status == MediaStatusEnum.UPLOADED
                || status == MediaStatusEnum.PROBING
                || status == MediaStatusEnum.PACKAGING
                || status == MediaStatusEnum.FORENSIC_PREPROCESSING) {
            if (status != MediaStatusEnum.UPLOADED) {
                updateStatus(mediaId, new MediaStatus(MediaStatusEnum.UPLOADED));
            }
        }
    }

    @Override
    public void markProcessFailed(String mediaId, String errorMessage) {
        // 终态不再覆盖，避免晚到的失败消息污染已经完成或已记录失败的任务
        MediaInfoEntity entity = mediaInfoRepository.findById(mediaId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        MediaStatusEnum status = entity.getMediaStatus() != null ? entity.getMediaStatus().getStatus() : null;
        if (status == MediaStatusEnum.READY
                || status == MediaStatusEnum.FAILED
                || status == MediaStatusEnum.REGISTERING_RES_TIMEOUT) {
            return;
        }
        // 注册资源失败保留为可补偿的 REGISTERING_RES_TIMEOUT，而不是普通 FAILED
        if (status == MediaStatusEnum.REGISTERING_RES) {
            updateStatus(mediaId, new MediaStatus(MediaStatusEnum.REGISTERING_RES_TIMEOUT, errorMessage));
            return;
        }
        // 探测、封装等媒体处理失败进入普通失败态
        updateStatus(mediaId, new MediaStatus(errorMessage));
    }

    private MediaPackagingResult packageImage(MediaInfoEntity mediaInfo) {
        // 图片生成独立预览图，不把源文件暴露给预览链路
        File sourceFile = null;
        Path previewPath = null;
        try {
            String downloadUrl = remoteStorageService.getDownloadUrl(mediaInfo.getSourceObjectKey(), null, null).getData();
            String sourceExtension = getSourceExtension(mediaInfo);
            sourceFile = downloadSourceFile(downloadUrl, mediaInfo.getMediaId(), sourceExtension);
            ImageSize imageSize = probeImage(sourceFile.toPath());

            previewPath = Files.createTempFile(Paths.get(mediaProperties.getCachePath()), mediaInfo.getMediaId() + "_preview_", ".jpg");
            runFfmpegToImagePreview(sourceFile.toPath(), previewPath);
            String previewObjectKey = uploadFile(previewPath,
                    StorageSceneEnum.PRIVATE_MEDIA.getPrefix() + "/" + mediaInfo.getMediaId() + "/preview.jpg",
                    mediaInfo.getMediaId());

            return MediaPackagingResult.builder()
                    .previewObjectKey(previewObjectKey)
                    .width(imageSize.width())
                    .height(imageSize.height())
                    .build();
        } catch (Exception e) {
            log.warn("media image packaging failed. mediaId={} objectKey={}",
                    mediaInfo.getMediaId(), mediaInfo.getSourceObjectKey(), e);
            throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED, e.getMessage());
        } finally {
            // 清理本地缓存
            if (sourceFile != null) {
                Path path = sourceFile.toPath();
                try {
                    Files.deleteIfExists(path);
                } catch (Exception e) {
                    log.warn("media cache file delete failed. path={}", path, e);
                }
            }
            if (previewPath != null) {
                try {
                    Files.deleteIfExists(previewPath);
                } catch (Exception e) {
                    log.warn("media cache file delete failed. path={}", previewPath, e);
                }
            }
        }
    }

    private MediaPackagingResult packageVideo(MediaInfoEntity mediaInfo) {
        // 视频处理会产生多个中间文件，使用独立 workDir 便于最后整体清理
        Path workDir = null;
        try {
            Path cacheRoot = Paths.get(mediaProperties.getCachePath());
            Files.createDirectories(cacheRoot);
            workDir = Files.createTempDirectory(cacheRoot, mediaInfo.getMediaId() + "_");

            // 把源视频下载到缓存目录
            String sourceUrl = remoteStorageService.getDownloadUrl(mediaInfo.getSourceObjectKey(), null, null).getData();
            String sourceExtension = getSourceExtension(mediaInfo);
            File sourceFile = downloadSourceFile(sourceUrl, mediaInfo.getMediaId(), sourceExtension);
            Path sourcePath = workDir.resolve(sourceFile.getName());
            Files.move(sourceFile.toPath(), sourcePath);

            // 先探测视频元数据
            VideoProbe probe = probeVideo(sourcePath);
            Path hlsDir = workDir.resolve("hls");
            Files.createDirectories(hlsDir);
            runFfmpegToHls(sourcePath, hlsDir);

            // HLS 产物统一上传到 private media 目录，播放时再由 manifest 服务签发分片 URL
            String hlsPrefix = StorageSceneEnum.PRIVATE_MEDIA.getPrefix()
                    + "/" + mediaInfo.getMediaId() + "/source-hls";
            List<String> hlsObjectKeys = new ArrayList<>();
            try (var stream = Files.list(hlsDir)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    hlsObjectKeys.add(uploadFile(file, hlsPrefix + "/" + file.getFileName(), mediaInfo.getMediaId()));
                }
            }

            String previewObjectKey = null;
            Path posterPath = workDir.resolve("poster.jpg");
            try {
                // 封面
                runFfmpegToPoster(sourcePath, posterPath);
                previewObjectKey = uploadFile(posterPath, hlsPrefix + "/poster.jpg", mediaInfo.getMediaId());
            } catch (Exception e) {
                log.warn("media poster generation failed. mediaId={}", mediaInfo.getMediaId(), e);
            }

            // 返回视频可播放所需的 HLS 前缀、文件清单、封面和基础元数据
            return MediaPackagingResult.builder()
                    .sourceHlsPrefix(hlsPrefix)
                    .sourceHlsObjectKeys(hlsObjectKeys)
                    .previewObjectKey(previewObjectKey)
                    .durationMs(probe.durationMs())
                    .width(probe.width())
                    .height(probe.height())
                    .build();
        } catch (Exception e) {
            log.warn("media video packaging failed. mediaId={} objectKey={}",
                    mediaInfo.getMediaId(), mediaInfo.getSourceObjectKey(), e);
            throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED, e.getMessage());
        } finally {
            // 视频处理目录包含源文件、HLS 分片和封面临时文件，需要递归清理
            if (workDir != null) {
                try (var paths = Files.walk(workDir)) {
                    paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception e) {
                            log.warn("media cache file delete failed. path={}", path, e);
                        }
                    });
                } catch (Exception e) {
                    log.warn("media cache directory delete failed. path={}", workDir, e);
                }
            }
        }
    }

    private MediaPackagingResult packageAudio(MediaInfoEntity mediaInfo) {
        // 音频不生成播放衍生产物，播放时直接签发源文件 URL，这里仅探测时长
        File sourceFile = null;
        try {
            String downloadUrl = remoteStorageService.getDownloadUrl(mediaInfo.getSourceObjectKey(), null, null).getData();
            String sourceExtension = getSourceExtension(mediaInfo);
            sourceFile = downloadSourceFile(downloadUrl, mediaInfo.getMediaId(), sourceExtension);
            AudioProbe probe = probeAudio(sourceFile.toPath());
            return MediaPackagingResult.builder()
                    .durationMs(probe.durationMs())
                    .build();
        } catch (Exception e) {
            log.warn("media audio probe failed. mediaId={} objectKey={}",
                    mediaInfo.getMediaId(), mediaInfo.getSourceObjectKey(), e);
            throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED, e.getMessage());
        } finally {
            // 音频探测完成后删除临时源文件
            if (sourceFile != null) {
                Path path = sourceFile.toPath();
                try {
                    Files.deleteIfExists(path);
                } catch (Exception e) {
                    log.warn("media cache file delete failed. path={}", path, e);
                }
            }
        }
    }

    private File downloadSourceFile(String url, String mediaId, String extension) throws IOException, InterruptedException {
        // 所有媒体源文件先落到本地缓存目录
        Path dir = Paths.get(mediaProperties.getCachePath());
        Files.createDirectories(dir);
        Path target = Files.createTempFile(dir, mediaId + "_source_", "." + extension);

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<Path> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() / 100 != 2) {
            Files.deleteIfExists(target);
            throw new IllegalStateException("媒体源文件下载失败 StatusCode=" + response.statusCode());
        }
        return target.toFile();
    }

    private ImageSize probeImage(Path sourcePath) throws IOException {
        // ffprobe 读取图片首帧尺寸，覆盖 webp/gif 等 ImageIO 默认支持不稳定的格式
        String output = runCommand(List.of(
                mediaProperties.getFfprobePath(),
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height",
                "-of", "json",
                sourcePath.toString()
        ), Duration.ofMillis(mediaProperties.getFfmpegTimeoutMs()));
        JsonNode root = objectMapper.readTree(output);
        JsonNode stream = root.path("streams").isArray() && !root.path("streams").isEmpty()
                ? root.path("streams").get(0) : objectMapper.createObjectNode();
        if (stream.path("width").isMissingNode() || stream.path("height").isMissingNode()) {
            throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED, "图片尺寸读取失败");
        }
        return new ImageSize(stream.path("width").asInt(), stream.path("height").asInt());
    }

    private VideoProbe probeVideo(Path sourcePath) throws IOException {
        // ffprobe 输出 JSON，读取首个视频流尺寸和容器时长
        String output = runCommand(List.of(
                mediaProperties.getFfprobePath(),
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height:format=duration",
                "-of", "json",
                sourcePath.toString()
        ), Duration.ofMillis(mediaProperties.getFfmpegTimeoutMs()));
        JsonNode root = objectMapper.readTree(output);
        JsonNode stream = root.path("streams").isArray() && !root.path("streams").isEmpty()
                ? root.path("streams").get(0) : objectMapper.createObjectNode();
        double durationSeconds = root.path("format").path("duration").asDouble(0D);
        // duration 缺失时按 0 处理；宽高缺失时保留 null
        return new VideoProbe(
                Math.round(durationSeconds * 1000D),
                stream.path("width").isMissingNode() ? null : stream.path("width").asInt(),
                stream.path("height").isMissingNode() ? null : stream.path("height").asInt()
        );
    }

    private AudioProbe probeAudio(Path sourcePath) throws IOException {
        // ffprobe 只选择第一条音频流；没有音频流说明文件不符合音频媒体预期
        String output = runCommand(List.of(
                mediaProperties.getFfprobePath(),
                "-v", "error",
                "-select_streams", "a:0",
                "-show_entries", "stream=codec_type:format=duration",
                "-of", "json",
                sourcePath.toString()
        ), Duration.ofMillis(mediaProperties.getFfmpegTimeoutMs()));
        JsonNode root = objectMapper.readTree(output);
        JsonNode streams = root.path("streams");
        if (!streams.isArray() || streams.isEmpty()) {
            throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED, "音频流不存在");
        }
        double durationSeconds = root.path("format").path("duration").asDouble(0D);
        // 当前音频播放走源文件直出，只需要记录时长
        return new AudioProbe(Math.round(durationSeconds * 1000D));
    }

    private void runFfmpegToHls(Path sourcePath, Path hlsDir) {
        // 转成 VOD HLS：视频统一编码为 H.264，音频存在时转 AAC，并输出 index.m3u8 与 TS 分片
        runCommand(List.of(
                mediaProperties.getFfmpegPath(),
                "-y",
                "-i", sourcePath.toString(),
                "-map", "0:v:0",
                "-map", "0:a?",
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-c:a", "aac",
                "-hls_time", String.valueOf(mediaProperties.getHlsSegmentSeconds()),
                "-hls_playlist_type", "vod",
                "-hls_segment_filename", hlsDir.resolve("seg-%05d.ts").toString(),
                hlsDir.resolve("index.m3u8").toString()
        ), Duration.ofMillis(mediaProperties.getFfmpegTimeoutMs()));
    }

    private void runFfmpegToImagePreview(Path sourcePath, Path previewPath) {
        // 图片预览统一输出 jpg，并限制长边，避免列表和详情页直接加载原图
        runCommand(List.of(
                mediaProperties.getFfmpegPath(),
                "-y",
                "-i", sourcePath.toString(),
                "-frames:v", "1",
                "-vf", "scale=w='if(gt(iw,ih),min(1280,iw),-2)':h='if(gt(iw,ih),-2,min(1280,ih))'",
                "-q:v", "3",
                previewPath.toString()
        ), Duration.ofMillis(mediaProperties.getFfmpegTimeoutMs()));
    }

    private void runFfmpegToPoster(Path sourcePath, Path posterPath) {
        // 从第 1 秒截取单帧作为视频封面，避免首帧黑屏的常见情况
        runCommand(List.of(
                mediaProperties.getFfmpegPath(),
                "-y",
                "-ss", "00:00:01",
                "-i", sourcePath.toString(),
                "-frames:v", "1",
                "-q:v", "2",
                posterPath.toString()
        ), Duration.ofMillis(mediaProperties.getFfmpegTimeoutMs()));
    }

    private String runCommand(List<String> command, Duration timeout) {
        try {
            // 合并 stderr/stdout，失败时把 FFmpeg/ffprobe 输出作为异常详情返回上层
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    return e.getMessage();
                }
            });
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                // 超时强制终止进程，防止异常媒体文件长期占用处理线程
                process.destroyForcibly();
                throw new IllegalStateException("媒体处理命令超时");
            }
            String output = outputFuture.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                throw new IllegalStateException(output);
            }
            return output;
        } catch (Exception e) {
            throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED, e.getMessage());
        }
    }

    private String uploadFile(Path file, String objectKey, String mediaId) {
        try {
            // 先向存储服务创建目标记录并申请 PUT URL；md5 支持秒传复用已有对象
            String extension = FileUtil.extName(file.getFileName().toString());
            UploadInitRespDTO uploadInitResp = remoteStorageService.initUpload(UploadInitReqDTO.builder()
                    .md5(SecureUtil.md5(file.toFile()))
                    .extension(extension)
                    .scene(StorageSceneEnum.PRIVATE_MEDIA)
                    .bizTag(mediaId)
                    .targetObjectKey(objectKey)
                    .expectedSize(Files.size(file))
                    .isNeedCallback(false)
                    .build()).getData();
            if (!Boolean.TRUE.equals(uploadInitResp.getFlashUploaded())) {
                // 非秒传场景由媒体服务直接 PUT 产物；不需要上传回调再次驱动媒体流程
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(uploadInitResp.getPutUrl()))
                        .header("Content-Type", "application/octet-stream")
                        .PUT(HttpRequest.BodyPublishers.ofFile(file))
                        .build();
                HttpResponse<Void> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() / 100 != 2) {
                    throw new IllegalStateException("媒体产物上传失败 StatusCode=" + response.statusCode());
                }
            }
            // 返回存储服务确认后的 objectKey，避免调用方依赖本地拼接结果
            return uploadInitResp.getObjectKey();
        } catch (Exception e) {
            throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED, e.getMessage());
        }
    }

    private String getSourceExtension(MediaInfoEntity mediaInfo) {
        // 优先使用上传时记录的扩展名；缺失时从 objectKey 兜底推导
        if (StrUtil.isNotBlank(mediaInfo.getSourceExtension())) {
            return mediaInfo.getSourceExtension();
        }
        return FileUtil.extName(mediaInfo.getSourceObjectKey());
    }

    @Override
    @Transactional
    public void finalizeToReady(String mediaId) {
        // finalize 可被正常处理和注册补偿重试共同调用，因此保持幂等
        MediaInfoEntity entity = mediaInfoRepository.findById(mediaId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        if (entity.getMediaStatus() != null && entity.getMediaStatus().getStatus() == MediaStatusEnum.READY) {
            return;
        }

        // 进入资源注册阶段后，如果远程资源服务失败，会留下 REGISTERING_RES_TIMEOUT 供重试
        updateStatus(mediaId, new MediaStatus(MediaStatusEnum.REGISTERING_RES));
        String resourceId = entity.getResourceId();
        if (StrUtil.isBlank(resourceId)) {
            try {
                // 只有基础产物确定后才注册 resource，避免资源服务暴露未完成媒体
                String result = null;
                // 资源服务的 preview 存 objectKey：图片使用预览图，视频使用封面，音频不提供预览图
                if (entity.getResourceType() == ResourceType.IMAGE || entity.getResourceType() == ResourceType.VIDEO) {
                    result = entity.getPreviewObjectKey();
                }
                resourceId = remoteResourceService.createResource(ResourceCreateReqDTO.builder()
                        .resourceName(entity.getOriginalFilename())
                        .resourceType(entity.getResourceType())
                        .ownerId(String.valueOf(entity.getOwnerId()))
                        .ownerGroupRoles(entity.getUploaderGroupRoles())
                        .mountTargetTagId(entity.getMountTargetTagId())
                        .preview(result)
                        .size(entity.getSize())
                        .build()).getData();
            } catch (Exception e) {
                log.error("media resource register failed. mediaId={}", mediaId, e);
                updateStatus(mediaId, new MediaStatus(MediaStatusEnum.REGISTERING_RES_TIMEOUT));
                throw new ServiceException(MediaError.MEDIA_REGISTER_RESOURCE_FAILED, e.getMessage());
            }
            mediaInfoRepository.updateResourceIdById(mediaId, resourceId);
        }

        // 资源 ID 存在后才能对外宣告媒体 READY，并发布 ready 事件给下游服务
        updateStatus(mediaId, new MediaStatus(MediaStatusEnum.READY));
        eventPublisher.publishReadyEvent(MediaReadyMessage.builder()
                .resourceId(resourceId)
                .mediaId(mediaId)
                .resourceType(entity.getResourceType())
                .build());
        log.info("media ready finalized. mediaId={} resourceId={}", mediaId, resourceId);
    }

    private record ImageSize(Integer width, Integer height) {
    }

    private record VideoProbe(Long durationMs, Integer width, Integer height) {
    }

    private record AudioProbe(Long durationMs) {
    }
}
