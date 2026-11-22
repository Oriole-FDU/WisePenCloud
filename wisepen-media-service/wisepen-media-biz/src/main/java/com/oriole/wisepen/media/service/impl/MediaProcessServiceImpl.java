package com.oriole.wisepen.media.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.storage.api.domain.dto.ConfirmUploadReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import com.oriole.wisepen.media.api.constant.MediaConstants;
import com.oriole.wisepen.media.api.domain.base.MediaStatus;
import com.oriole.wisepen.media.api.domain.mq.MediaProcessTaskMessage;
import com.oriole.wisepen.media.api.enums.MediaStatusEnum;
import com.oriole.wisepen.media.config.MediaProperties;
import com.oriole.wisepen.media.domain.entity.MediaEntity.MediaHlsRenditionObjectKeys;
import com.oriole.wisepen.media.domain.entity.MediaMetaEntity.MediaHlsRendition;
import com.oriole.wisepen.media.domain.entity.MediaMetaEntity;
import com.oriole.wisepen.media.exception.MediaError;
import com.oriole.wisepen.media.mq.KafkaMediaEventPublisher;
import com.oriole.wisepen.media.service.IMediaProcessService;
import com.oriole.wisepen.media.service.IMediaService;
import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaProcessServiceImpl implements IMediaProcessService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Pattern ATTR_BANDWIDTH = Pattern.compile("BANDWIDTH=(\\d+)");
    private static final Pattern ATTR_RESOLUTION = Pattern.compile("RESOLUTION=([\\dx]+)");
    private static final Pattern ATTR_CODECS = Pattern.compile("CODECS=\"([^\"]+)\"");

    private final RemoteStorageService remoteStorageService;
    private final IMediaService mediaService;

    private final KafkaMediaEventPublisher eventPublisher;
    private final MediaProperties mediaProperties;
    private final ObjectMapper objectMapper;

    @Override
    public void processMedia(MediaProcessTaskMessage message) {
        MediaStatus status = mediaService.getMediaStatus(message.getMediaId()).orElse(null);
        if (status == null) {
            log.info("media process skipped because media is missing. mediaId={}", message.getMediaId());
            return;
        }

        // 只有 UPLOADED 状态允许进入处理，防止重复消费消息导致重复封装或覆盖产物
        if (status.getStatus() != MediaStatusEnum.UPLOADED) {
            log.debug("media process skipped because status mismatched. mediaId={} status={}", message.getMediaId(), status);
            return;
        }

        mediaService.updateStatus(message.getMediaId(), new MediaStatus(MediaStatusEnum.PROCESSING));

        MediaProcessingResult packagingResult = switch (message.getResourceType()){
            case ResourceType.IMAGE -> processImage(message);
            case ResourceType.VIDEO -> processVideo(message);
            case ResourceType.AUDIO -> processAudio(message);
            default -> throw new ServiceException(MediaError.CANNOT_SUPPORT_FILE_TYPE);
        };
        mediaService.saveMediaProcessResult(message.getMediaId(), packagingResult.previewObjectKey(), packagingResult.hlsObjectKeys(), packagingResult.meta());

        // 基础产物和元数据落库后再注册 resource，避免资源服务暴露未完成媒体
        mediaService.finalizeToReady(message.getMediaId());
    }

    private MediaProcessingResult processImage(MediaProcessTaskMessage message) {
        // 图片生成独立低清封面图，用于资源列表和播放封面展示
        File sourceFile = null;
        File previewFile = null;
        String previewObjectKey = null;
        try {
            // 下载文件
            String sourceUrl = remoteStorageService.getDownloadUrl(message.getSourceObjectKey(), null, null).getData();
            sourceFile = downloadSourceFile(sourceUrl, message.getMediaId(), message.getExtension());

            // 处理图片预览
            previewFile = createCacheFile(message.getMediaId(), ".jpg");
            runFfmpegToImagePreview(sourceFile.toPath(), previewFile.toPath());
            UploadedProduct previewProduct = uploadFile(previewFile.toPath(), message.getMediaId());
            previewObjectKey = previewProduct.objectKey();
            confirmUploads(message.getMediaId(), List.of(previewProduct));

            MediaMetaEntity meta = probeImage(sourceFile.toPath());

            return new MediaProcessingResult(previewObjectKey, null, meta);
        } catch (Exception e) {
            if (StrUtil.isNotBlank(previewObjectKey)) {
                eventPublisher.publishFileDeleteEvent(List.of(previewObjectKey));
            }
            throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED);
        } finally {
            // 清理本地缓存
            deleteSilently(sourceFile);
            deleteSilently(previewFile);
        }
    }

    private MediaProcessingResult processVideo(MediaProcessTaskMessage message) {
        File sourceFile = null;
        File hlsDir = null;
        File posterFile = null;
        List<UploadedProduct> uploadedProducts = new ArrayList<>();
        try {
            // 下载文件
            String sourceUrl = remoteStorageService.getDownloadUrl(message.getSourceObjectKey(), null, null).getData();
            sourceFile = downloadSourceFile(sourceUrl, message.getMediaId(), message.getExtension());

            // HLS 输出目录
            // ffmpeg 的 init/分片/清单全部写入其中
            hlsDir = createCacheDir(message.getMediaId() + "_HLS");

            // 探测视频元数据
            // hasAudio 决定 HLS 转码是否映射音轨
            VideoProbe probe = probeVideo(sourceFile.toPath());
            MediaMetaEntity meta = probe.meta();
            runFfmpegToHls(sourceFile.toPath(), hlsDir.toPath(), meta, probe.hasAudio());

            // HLS 清单由播放阶段按结构化描述即时生成，这里只上传 init 与分片
            Map<String, String> objectKeyByFilename = new HashMap<>();
            Map<String, String> m3u8Texts = new HashMap<>();
            try (var stream = Files.list(hlsDir.toPath())) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    String filename = file.getFileName().toString();
                    if (filename.endsWith(".m3u8")) {
                        m3u8Texts.put(filename, Files.readString(file));
                    } else {
                        UploadedProduct product = uploadFile(file, message.getMediaId());
                        uploadedProducts.add(product);
                        objectKeyByFilename.put(filename, product.objectKey());
                    }
                }
            }
            ParsedHls parsedHls = parseHlsManifest(m3u8Texts, objectKeyByFilename);

            String previewObjectKey = null;
            posterFile = createCacheFile(message.getMediaId(), ".jpg");
            try {
                // 封面
                runFfmpegToPoster(sourceFile.toPath(), posterFile.toPath());
                UploadedProduct posterProduct = uploadFile(posterFile.toPath(), message.getMediaId());
                uploadedProducts.add(posterProduct);
                previewObjectKey = posterProduct.objectKey();
            } catch (Exception e) {
                log.warn("media poster generation failed. mediaId={}", message.getMediaId(), e);
            }

            // 所有产物直传成功后一次性确认存储记录
            confirmUploads(message.getMediaId(), uploadedProducts);

            // 返回视频可播放所需的 HLS 描述、封面和基础元数据
            return new MediaProcessingResult(
                    previewObjectKey,
                    parsedHls.objectKeys(),
                    MediaMetaEntity.builder()
                            .durationMs(meta.getDurationMs())
                            .width(meta.getWidth())
                            .height(meta.getHeight())
                            .hls(parsedHls.renditions())
                            .build());
        } catch (Exception e) {
            List<String> uploadedObjectKeys = uploadedProducts.stream().map(UploadedProduct::objectKey).toList();
            log.error("processVideo failed. mediaId={}, sourceObjectKey={}, uploadedObjectKeys={}",
                    message.getMediaId(), message.getSourceObjectKey(), uploadedObjectKeys, e);
            if (!uploadedObjectKeys.isEmpty()) eventPublisher.publishFileDeleteEvent(uploadedObjectKeys);
            throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED);
        } finally {
            deleteSilently(sourceFile);
            deleteSilently(hlsDir);
            deleteSilently(posterFile);
        }
    }

    private MediaProcessingResult processAudio(MediaProcessTaskMessage message) {
        // 音频不生成播放衍生产物，播放时直接签发源文件 URL，这里仅探测时长
        File sourceFile = null;
        try {
            String downloadUrl = remoteStorageService.getDownloadUrl(message.getSourceObjectKey(), null, null).getData();
            sourceFile = downloadSourceFile(downloadUrl, message.getMediaId(), message.getExtension());
            MediaMetaEntity meta = probeAudio(sourceFile.toPath());
            return new MediaProcessingResult(null, null, meta);
        } catch (Exception e) {
            throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED);
        } finally {
            // 音频探测完成后删除临时源文件
            deleteSilently(sourceFile);
        }
    }

    /** 流式下载源文件到本地缓存目录 */
    private File downloadSourceFile(String url, String mediaId, String extension) throws IOException, InterruptedException {
        Path dir = Paths.get(mediaProperties.getLocalResources().getCachePath());
        Files.createDirectories(dir);
        Path target = dir.resolve(mediaId + "_source." + extension);

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                .timeout(Duration.ofMillis(mediaProperties.getProcessingRuntime().getHttpRequestTimeoutMs()))
                .GET().build();

        HttpResponse<Path> response;
        try {
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(target));
        } catch (Exception e) {
            Files.deleteIfExists(target);
            throw e;
        }
        if (response.statusCode() / 100 != 2) {
            Files.deleteIfExists(target);
            throw new IllegalStateException("媒体源文件下载失败 StatusCode=" + response.statusCode());
        }
        return target.toFile();
    }

    /** 在缓存目录下创建临时文件 */
    private File createCacheFile(String prefix, String suffix) throws IOException {
        Path dir = Paths.get(mediaProperties.getLocalResources().getCachePath());
        Files.createDirectories(dir);
        return Files.createTempFile(dir, prefix + "_", suffix).toFile();
    }

    /** 在缓存目录下创建临时目录 */
    private File createCacheDir(String prefix) throws IOException {
        Path dir = Paths.get(mediaProperties.getLocalResources().getCachePath());
        Files.createDirectories(dir);
        return Files.createTempDirectory(dir, prefix + "_").toFile();
    }

    /** 静默删除本地临时文件或目录（目录递归删除），失败仅打印警告，不影响主流程 */
    private void deleteSilently(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        Path path = file.toPath();
        if (!Files.isDirectory(path)) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("cache file delete failed. path={}", path, e);
            }
            return;
        }
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("cache file delete failed. path={}", p, e);
                }
            });
        } catch (IOException e) {
            log.warn("cache directory delete failed. path={}", path, e);
        }
    }

    private MediaMetaEntity probeImage(Path sourcePath) throws IOException {
        // 用 ffprobe 读取图片首帧宽高
        String output = runCommand(List.of(
                mediaProperties.getLocalResources().getFfprobePath(),
                "-v", "error", // 只输出错误
                "-select_streams", "v:0", // 只取第一条视频流（图片通常只有一条）
                "-show_entries", "stream=width,height", // 只返回 width/height
                "-of", "json", // JSON 输出
                sourcePath.toString()
        ), Duration.ofMillis(mediaProperties.getProcessingRuntime().getFfmpegTimeoutMs()));

        JsonNode root = objectMapper.readTree(output);
        JsonNode stream = root.path("streams").isArray() && !root.path("streams").isEmpty()
                ? root.path("streams").get(0) : objectMapper.createObjectNode();
        // 检查尺寸是否合法
        if (stream.path("width").isMissingNode() || stream.path("height").isMissingNode()) {
            throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED, "图片尺寸读取失败");
        }

        return MediaMetaEntity.builder()
                .width(stream.path("width").asInt())
                .height(stream.path("height").asInt())
                .build();
    }

    private VideoProbe probeVideo(Path sourcePath) throws IOException {
        // 用 ffprobe 读取视频流宽高、音轨存在性、容器时长
        String output = runCommand(List.of(
                mediaProperties.getLocalResources().getFfprobePath(),
                "-v", "error", // 只输出错误
                "-show_entries", "stream=codec_type,width,height:format=duration", // 遍历所有流，区分视频/音频，取容器级时长（秒），调用方转毫秒
                "-of", "json", // JSON 输出
                sourcePath.toString()
        ), Duration.ofMillis(mediaProperties.getProcessingRuntime().getFfmpegTimeoutMs()));
        JsonNode root = objectMapper.readTree(output);
        JsonNode videoStream = null;

        // 音轨是否存在
        // 供 runFfmpegToHls 使用，无声视频不能 -map 0:a:0，否则 ffmpeg 因音轨不存在而报错
        boolean hasAudio = false;

        for (JsonNode stream : root.path("streams")) {
            if (videoStream == null && "video".equals(stream.path("codec_type").asText())) {
                videoStream = stream;
            }
            if ("audio".equals(stream.path("codec_type").asText())) {
                hasAudio = true;
            }
        }
        // 检查尺寸是否合法
        if (videoStream == null || videoStream.path("width").asInt() < 2 || videoStream.path("height").asInt() < 2) {
            throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED, "视频尺寸无效");
        }
        // 时长转换
        double durationSeconds = root.path("format").path("duration").asDouble(0D);
        MediaMetaEntity meta = MediaMetaEntity.builder()
                .durationMs(Math.round(durationSeconds * 1000D))
                .width(videoStream.path("width").asInt())
                .height(videoStream.path("height").asInt())
                .build();
        return new VideoProbe(meta, hasAudio);
    }

    private MediaMetaEntity probeAudio(Path sourcePath) throws IOException {
        // 用 ffprobe 读取音频容器时长
        String output = runCommand(List.of(
                mediaProperties.getLocalResources().getFfprobePath(),
                "-v", "error", // 只输出错误
                "-select_streams", "a:0", // 只选第一条音频流；没有音频流说明文件不是有效音频媒体
                "-show_entries", "stream=codec_type:format=duration", // 取容器级时长（秒），调用方转毫秒
                "-of", "json", // JSON 输出
                sourcePath.toString()
        ), Duration.ofMillis(mediaProperties.getProcessingRuntime().getFfmpegTimeoutMs()));
        JsonNode root = objectMapper.readTree(output);
        JsonNode streams = root.path("streams");
        if (!streams.isArray() || streams.isEmpty()) {
            throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED, "音频流不存在");
        }
        // 时长转换
        double durationSeconds = root.path("format").path("duration").asDouble(0D);
        return MediaMetaEntity.builder().durationMs(Math.round(durationSeconds * 1000D)).build();
    }

    private void runFfmpegToHls(Path sourcePath, Path hlsDir, MediaMetaEntity meta, boolean hasAudio) {
        // 用 ffmpeg 把源视频转成多档位 fMP4 HLS 自适应码率输出，产物包括：
        //   - index.m3u8      主清单（master），每个档位一行 STREAM-INF + 指向该档 %v.m3u8
        //   - %v.m3u8         档位媒体清单（如 480p.m3u8），引用 init 段与分片
        //   - init-*.mp4      各档 fMP4 初始化段（fMP4 解码参数）
        //   - %v-seg-*.m4s    各档分片
        int segmentSeconds = mediaProperties.getProcessingRuntime().getHlsSegmentSeconds();
        if (segmentSeconds <= 0) {
            throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED, "HLS 分片时长必须为正数");
        }
        // 取源视频的短边，档位判断与方向无关
        // 例如，横屏（1920×1080）和竖屏（1080×1920）都得到 1080
        int sourceShortSide = Math.min(meta.getWidth(), meta.getHeight()) / 2 * 2; // 向下取最近的偶数，短边必须对齐偶数，否则会出现奇数边导致编码报错
        List<HlsRendition> renditions = new ArrayList<>();
        for (HlsRendition target : List.of(
                // 三个固定档位：480p@1000k、720p@2500k、1080p@5000k，从低到高排列
                new HlsRendition(480, 1000),
                new HlsRendition(720, 2500),
                new HlsRendition(1080, 5000))
        ) {
            renditions.add(new HlsRendition(Math.min(sourceShortSide, target.shortSide()), target.bitrateKbps()));
            if (sourceShortSide <= target.shortSide()) break; // 一旦源的短边 ≤ 当前档位，就不再生成更高档
        }

        List<String> command = new ArrayList<>(List.of(
                mediaProperties.getLocalResources().getFfmpegPath(),
                "-y", "-i", sourcePath.toString()));
        List<String> variants = new ArrayList<>();
        for (int i = 0; i < renditions.size(); i++) {
            HlsRendition rendition = renditions.get(i);
            String shortSide = String.valueOf(rendition.shortSide());
            // 每个档位各映射一份视频流
            command.addAll(List.of("-map", "0:v:0"));
            if (hasAudio) {
                // 音频流仅在有音轨时映射，避免引用不存在的流
                command.addAll(List.of("-map", "0:a:0"));
            }
            command.addAll(List.of(
                    // scale：长边按短边比例计算并取偶数（-2），自动应用旋转信息，保留画面比例
                    "-filter:v:" + i, "scale=w='if(gte(iw,ih),-2," + shortSide
                            + ")':h='if(gte(iw,ih)," + shortSide + ",-2)'",
                    "-b:v:" + i, rendition.bitrateKbps() + "k",
                    // 码率：maxrate=目标码率、bufsize=2x，用 VBV 约束避免码率瞬时飙升
                    "-maxrate:v:" + i, rendition.bitrateKbps() + "k",
                    "-bufsize:v:" + i, (rendition.bitrateKbps() * 2) + "k"));
            variants.add("v:" + i + (hasAudio ? ",a:" + i : "") + ",name:" + shortSide + "p");
        }
        // 视频编码统一 H.264 + yuv420p（兼容性最好）；+cgop + sc_threshold 0 强制封闭 GOP
        command.addAll(List.of(
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-pix_fmt", "yuv420p",
                "-flags:v", "+cgop",
                "-sc_threshold:v", "0",
                // 各档在相同时间强制关键帧，保证切档时分片边界与解码起点对齐。
                "-force_key_frames:v", "expr:gte(t,n_forced*" + segmentSeconds + ")",
                "-c:a", "aac",
                "-b:a", "128k", "-ac", "2", "-ar", "48000",
                "-f", "hls",
                "-hls_time", String.valueOf(segmentSeconds),
                "-hls_playlist_type", "vod",
                "-hls_segment_type", "fmp4",
                "-hls_flags", "independent_segments",
                // FFmpeg 仅在多档时展开 init 文件名的 %v；输出路径用 / 保证 Windows 下也正确定位 init。
                "-hls_fmp4_init_filename", renditions.size() == 1
                        ? "init-" + renditions.getFirst().shortSide() + "p.mp4" : "init-%v.mp4",
                "-hls_segment_filename", hlsDir.resolve("%v-seg-%05d.m4s").toString().replace('\\', '/'),
                "-master_pl_name", "index.m3u8",
                "-var_stream_map", String.join(" ", variants),
                hlsDir.resolve("%v.m3u8").toString().replace('\\', '/')));
        runCommand(command, Duration.ofMillis(mediaProperties.getProcessingRuntime().getFfmpegTimeoutMs()));
    }

    private void runFfmpegToImagePreview(Path sourcePath, Path previewPath) {
        // 图片预览统一转成 jpg 并限制长边 1280：列表/详情页只需缩略图，直接加载原图浪费带宽。
        // scale 表达式：横图限宽 1280、高按比例取偶数；竖图限高 1280、宽按比例取偶数。
        // -frames:v 1 只输出一帧；-q:v 3 是 jpg 质量（值越小质量越高）。
        runCommand(List.of(
                mediaProperties.getLocalResources().getFfmpegPath(),
                "-y",
                "-i", sourcePath.toString(),
                "-frames:v", "1",
                "-vf", "scale=w='if(gt(iw,ih),min(1280,iw),-2)':h='if(gt(iw,ih),-2,min(1280,ih))'",
                "-q:v", "3",
                previewPath.toString()
        ), Duration.ofMillis(mediaProperties.getProcessingRuntime().getFfmpegTimeoutMs()));
    }

    private void runFfmpegToPoster(Path sourcePath, Path posterPath) {
        // 从第 1 秒截取单帧作封面：视频首帧常是黑屏，1 秒处通常已有画面。
        // -ss 放在 -i 之前是 input seeking，直接跳到目标位置解码，速度更快。
        // -q:v 2 比预览图质量略高，封面在列表里需要更清晰。
        runCommand(List.of(
                mediaProperties.getLocalResources().getFfmpegPath(),
                "-y",
                "-ss", "00:00:01",
                "-i", sourcePath.toString(),
                "-frames:v", "1",
                "-q:v", "2",
                posterPath.toString()
        ), Duration.ofMillis(mediaProperties.getProcessingRuntime().getFfmpegTimeoutMs()));
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
                    return "";
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
            log.error("media command failed. command={}, timeout={}", command, timeout, e);
            throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED);
        }
    }

    private UploadedProduct uploadFile(Path file, String mediaId) {
        try {
            // 向存储服务申请上传凭证；objectKey 由存储服务下发，md5 支持秒传复用已有对象
            String extension = FileUtil.extName(file.getFileName().toString());
            String md5 = SecureUtil.md5(file.toFile());
            long size = Files.size(file);
            UploadInitRespDTO uploadInitResp = remoteStorageService.initUpload(UploadInitReqDTO.builder()
                    .md5(md5)
                    .extension(extension)
                    .scene(StorageSceneEnum.PRIVATE_MEDIA)
                    .bizTag(mediaId)
                    .expectedSize(size)
                    .isNeedCallback(false)
                    .build()).getData();
            if (!Boolean.TRUE.equals(uploadInitResp.getFlashUploaded())) {
                // 非秒传场景由媒体服务直接 PUT 产物；不需要上传回调再次驱动媒体流程
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(uploadInitResp.getPutUrl()))
                        .timeout(Duration.ofMillis(mediaProperties.getProcessingRuntime().getHttpRequestTimeoutMs()))
                        .header("Content-Type", "application/octet-stream")
                        .PUT(HttpRequest.BodyPublishers.ofFile(file))
                        .build();
                HttpResponse<Void> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() / 100 != 2) {
                    throw new IllegalStateException("媒体产物上传失败 StatusCode=" + response.statusCode());
                }
            }
            return new UploadedProduct(uploadInitResp.getObjectKey(), md5, size);
        } catch (Exception e) {
            log.error("media product upload failed. mediaId={} file={}", mediaId, file.getFileName(), e);
            throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED);
        }
    }

    private ParsedHls parseHlsManifest(Map<String, String> m3u8Texts, Map<String, String> objectKeyByFilename) {
        // 解析 ffmpeg 生成的主清单（index.m3u8），把每个档位拆成两部分
        //   - MediaHlsRendition：档位结构（bandwidth/resolution/codecs/分片时长），存 MediaMetaEntity.hls
        //   - MediaHlsRenditionObjectKeys：init/分片 objectKey，存 MediaEntity.hlsObjectKeys

        // 主清单结构：每个档位先一行 #EXT-X-STREAM-INF:BANDWIDTH=...，紧跟一行该档媒体清单文件名
        String masterText = m3u8Texts.get(MediaConstants.MASTER_PLAYLIST_NAME);
        if (StrUtil.isBlank(masterText)) {
            throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED, "HLS 主清单缺失");
        }
        List<MediaHlsRendition> renditions = new ArrayList<>();
        List<MediaHlsRenditionObjectKeys> objectKeys = new ArrayList<>();
        String pendingAttrs = null;
        for (String line : masterText.split("\\R")) {
            if (StrUtil.isBlank(line)) {
                continue;
            }
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                pendingAttrs = line;
            } else if (!line.startsWith("#") && pendingAttrs != null) {
                String playlistName = line.trim();
                String playlistText = m3u8Texts.get(playlistName);
                if (StrUtil.isBlank(playlistText)) {
                    throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED, "HLS 媒体清单缺失: " + playlistName);
                }
                Matcher bandwidthMatcher = ATTR_BANDWIDTH.matcher(pendingAttrs);
                Matcher resolutionMatcher = ATTR_RESOLUTION.matcher(pendingAttrs);
                Matcher codecsMatcher = ATTR_CODECS.matcher(pendingAttrs);
                RenditionParse parsed = parseMediaPlaylist(playlistName, playlistText, objectKeyByFilename);
                renditions.add(MediaHlsRendition.builder()
                        .playlistName(playlistName)
                        .bandwidth(bandwidthMatcher.find() ? bandwidthMatcher.group(1) : null)
                        .resolution(resolutionMatcher.find() ? resolutionMatcher.group(1) : null)
                        .codecs(codecsMatcher.find() ? codecsMatcher.group(1) : null)
                        .segmentDurations(parsed.durations())
                        .build());
                objectKeys.add(MediaHlsRenditionObjectKeys.builder()
                        .playlistName(playlistName)
                        .initObjectKey(parsed.initObjectKey())
                        .segmentObjectKeys(parsed.segmentObjectKeys())
                        .build());
                pendingAttrs = null;
            }
        }
        if (renditions.isEmpty()) {
            throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED, "HLS 档位为空");
        }
        return new ParsedHls(renditions, objectKeys);
    }

    private RenditionParse parseMediaPlaylist(String playlistName, String playlistText,
                                              Map<String, String> objectKeyByFilename) {
        // 解析单个档位的媒体清单，提取 fMP4 的 init 段与每个分片的 objectKey + 精确时长
        // 结构：
        //   #EXT-X-MAP:URI="init-480p.mp4" -> init 段（fMP4 解码参数，播放器下载分片前先取它）
        //   #EXTINF:4.000000,              -> 下一行分片的时长（秒）
        //   <分片文件名>                    -> 紧跟 EXTINF 的分片 URI，映射到已上传的 objectKey
        // EXTINF 与分片行成对出现,先用 pendingDuration 暂存时长，遇到分片行再配对入列
        String initFilename = null;
        List<Double> durations = new ArrayList<>();
        List<String> segmentObjectKeys = new ArrayList<>();
        Double pendingDuration = null;
        for (String line : playlistText.split("\\R")) {
            if (StrUtil.isBlank(line)) {
                continue;
            }
            if (line.startsWith("#EXT-X-MAP:")) {
                int uriStart = line.indexOf("URI=\"");
                if (uriStart < 0) {
                    throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED, "HLS init 段缺失: " + playlistName);
                }
                int valueStart = uriStart + "URI=\"".length();
                int valueEnd = line.indexOf('"', valueStart);
                initFilename = valueEnd < 0 ? null : line.substring(valueStart, valueEnd);
            } else if (line.startsWith("#EXTINF:")) {
                int colon = line.indexOf(':');
                int comma = line.indexOf(',');
                String value = comma < 0 ? line.substring(colon + 1) : line.substring(colon + 1, comma);
                pendingDuration = Double.parseDouble(value.trim());
            } else if (!line.startsWith("#")) {
                String segmentFilename = line.trim();
                String objectKey = objectKeyByFilename.get(segmentFilename);
                if (pendingDuration == null || objectKey == null) {
                    throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED, "HLS 媒体清单不完整: " + playlistName);
                }
                durations.add(pendingDuration);
                segmentObjectKeys.add(objectKey);
                pendingDuration = null;
            }
        }
        String initObjectKey = initFilename == null ? null : objectKeyByFilename.get(initFilename);
        if (initObjectKey == null || segmentObjectKeys.isEmpty()) {
            throw new ServiceException(MediaError.MEDIA_PROCESS_FAILED, "HLS 媒体清单不完整: " + playlistName);
        }
        return new RenditionParse(durations, initObjectKey, segmentObjectKeys);
    }

    private void confirmUploads(String mediaId, List<UploadedProduct> products) {
        if (products.isEmpty()) {
            return;
        }
        ConfirmUploadReqDTO req = ConfirmUploadReqDTO.builder()
                .items(products.stream().map(p -> ConfirmUploadReqDTO.Item.builder()
                        .objectKey(p.objectKey()).md5(p.md5()).size(p.size()).build()).toList())
                .build();
        try {
            remoteStorageService.confirmUpload(req);
        } catch (Exception e) {
            log.warn("media product upload confirm failed, ignored. mediaId={}", mediaId, e);
        }
    }

    private record UploadedProduct(String objectKey, String md5, long size) {
    }

    private record VideoProbe(MediaMetaEntity meta, boolean hasAudio) {
    }

    private record HlsRendition(int shortSide, int bitrateKbps) {
    }

    private record MediaProcessingResult(String previewObjectKey, List<MediaHlsRenditionObjectKeys> hlsObjectKeys, MediaMetaEntity meta) {
    }

    private record ParsedHls(List<MediaHlsRendition> renditions, List<MediaHlsRenditionObjectKeys> objectKeys) {
    }

    private record RenditionParse(List<Double> durations, String initObjectKey, List<String> segmentObjectKeys) {
    }
}
