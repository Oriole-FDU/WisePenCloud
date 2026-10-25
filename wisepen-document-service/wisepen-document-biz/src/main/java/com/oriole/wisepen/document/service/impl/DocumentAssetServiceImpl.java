package com.oriole.wisepen.document.service.impl;

import com.oriole.wisepen.common.core.constant.CommonError;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentImageReferencesResponse;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentImageReferencesResponse.ImageReference;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentImageReferencesResponse.PageLocation;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentImageResolveResponse;
import com.oriole.wisepen.document.api.enums.DocumentStatusEnum;
import com.oriole.wisepen.document.config.DocumentProperties;
import com.oriole.wisepen.document.domain.entity.DocumentVersionEntity;
import com.oriole.wisepen.document.exception.DocumentError;
import com.oriole.wisepen.document.repository.DocumentContentRepository;
import com.oriole.wisepen.document.service.IDocumentAssetService;
import com.oriole.wisepen.document.util.DocumentAssetKey;
import com.oriole.wisepen.document.util.MarkdownAssetLinkCodec;
import com.oriole.wisepen.document.util.MarkdownImages;
import com.oriole.wisepen.document.util.MarkdownPageBreakInjector;
import com.oriole.wisepen.file.storage.api.domain.dto.CopyReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAssetServiceImpl implements IDocumentAssetService {

    private static final String DRAFT = "wisepen-image-draft:";
    private static final Pattern DATA_HEADER = Pattern.compile("(?i)data:image/[a-z0-9.+-]+;base64");

    private final RemoteStorageService remoteStorageService;
    private final DocumentContentRepository documentContentRepository;
    private final MarkdownPageBreakInjector pageBreakInjector;
    private final DocumentProperties properties;

    @Override
    public String processMarkdown(String markdown, File pdfFile, String documentId) {
        if (markdown == null || markdown.isBlank()) return markdown;
        MarkdownImages.Parsed original = MarkdownImages.parse(markdown);
        Map<String, MarkdownImages.Image> drafts = new HashMap<>();
        int[] sequence = {0};
        String skeleton = original.rewrite(image -> {
            String token = DRAFT + (++sequence[0]);
            drafts.put(token, image);
            return MarkdownImages.image(image.alt(), token);
        });
        // 先移走图片载荷，再运行页尾文字匹配；POI 已生成的分页标记直接复用。
        skeleton = MarkdownImages.withoutInlineData(skeleton);
        if (!skeleton.contains("<!-- page:start page=")) skeleton = pageBreakInjector.inject(skeleton, pdfFile, documentId);
        int[] uploaded = {0};
        int[] missing = {0};
        String rewritten = MarkdownImages.parse(skeleton).rewrite(image -> {
            MarkdownImages.Image source = drafts.get(image.source());
            if (source == null) return MarkdownImages.image(image.alt(), image.source());
            int index = Integer.parseInt(image.source().substring(DRAFT.length()));
            String alt = (image.pageNo() == null ? "图片" + index + "（页码未知）" : "第" + image.pageNo() + "页图片" + index + "（推定）")
                    + (source.alt().isBlank() ? "" : "：" + source.alt());
            if (MarkdownImages.isDataImage(source.source())) {
                try {
                    int comma = source.source().indexOf(',');
                    if (comma < 0 || !DATA_HEADER.matcher(source.source().substring(0, comma)).matches()) {
                        throw new IOException("不支持的图片 data URI");
                    }
                    byte[] bytes = Base64.getDecoder().decode(source.source().substring(comma + 1).replaceAll("\\s+", ""));
                    String extension = imageExtension(bytes);
                    String fileName = DocumentAssetKey.fileName(index, extension);
                    StorageRecordDTO stored = upload(bytes, DocumentAssetKey.assetsBizTag(documentId), fileName);
                    uploaded[0]++;
                    return MarkdownImages.image(alt, MarkdownAssetLinkCodec.link(stored.getObjectKey()));
                } catch (Exception e) {
                    missing[0]++;
                    log.warn("document image externalization skipped. documentId={} imageIndex={}", documentId, index, e);
                    return MarkdownImages.missing(alt);
                }
            }
            try {
                if (source.source().startsWith(MarkdownAssetLinkCodec.SCHEME)) {
                    // 复用本版本已规范化的图片。
                    String objectKey = MarkdownAssetLinkCodec.objectKey(source.source(), documentId);
                    uploaded[0]++;
                    return MarkdownImages.image(alt, MarkdownAssetLinkCodec.link(objectKey));
                }
                if (source.source().startsWith("http://") || source.source().startsWith("https://")) {
                    return MarkdownImages.image(alt, source.source());
                }
            } catch (Exception e) {
                log.warn("document image source skipped. documentId={} imageIndex={}", documentId, index, e);
            }
            missing[0]++;
            log.warn("document image source unsupported. documentId={} imageIndex={}", documentId, index);
            return MarkdownImages.missing(alt);
        });
        log.info("document images processed. documentId={} available={} missing={}", documentId, uploaded[0], missing[0]);
        return MarkdownImages.withoutInlineData(rewritten);
    }

    /** 只读取图片头确认类型，不按 MIME 声明盲目信任扩展名，也不解压整个像素画布。 */
    private String imageExtension(byte[] bytes) throws IOException {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("无法识别内嵌图片格式");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                if (reader.getWidth(0) <= 0 || reader.getHeight(0) <= 0) throw new IOException("图片尺寸无效");
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                return "jpeg".equals(format) ? "jpg" : format;
            } finally {
                reader.dispose();
            }
        }
    }

    private StorageRecordDTO upload(byte[] bytes, String bizTag, String fileName) {
        R<StorageRecordDTO> response = remoteStorageService.uploadSmallFileWithName(new ImageFile(fileName, bytes),
                StorageSceneEnum.PRIVATE_DOC, bizTag, fileName, null);
        if (response == null || !Integer.valueOf(200).equals(response.getCode()) || response.getData() == null) {
            throw new ServiceException(DocumentError.DOCUMENT_IMAGE_UPLOAD_FAILED);
        }
        return response.getData();
    }

    @Override
    public DocumentImageReferencesResponse listImageReferences(DocumentVersionEntity version, Integer pageNo) {
        assertReady(version);
        if (pageNo != null && pageNo < 1) throw new ServiceException(CommonError.REQUEST_PARAM_INVALID, "页码必须大于零");
        String markdown = documentContentRepository.findById(version.getDocumentId())
                .orElseThrow(() -> new ServiceException(DocumentError.DOCUMENT_NOT_FOUND)).getMarkdown();
        List<ImageReference> references = new ArrayList<>();
        long unknown = 0;
        for (MarkdownImages.Image image : MarkdownImages.parse(markdown).images()) {
            if (!image.source().startsWith(MarkdownAssetLinkCodec.SCHEME)) continue;
            String key = MarkdownAssetLinkCodec.objectKey(image.source(), version.getDocumentId());
            int imageIndex = DocumentAssetKey.imageIndex(key);
            if (imageIndex < 0) continue;
            if (image.pageNo() == null) unknown++;
            if (pageNo != null && !pageNo.equals(image.pageNo())) continue;
            references.add(ImageReference.builder()
                    .imageIndex(imageIndex)
                    .objectKey(key).markdownLink(MarkdownAssetLinkCodec.link(key)).alt(image.alt())
                    .pageNo(image.pageNo()).pageLocation(image.pageNo() == null ? PageLocation.UNKNOWN : PageLocation.INFERRED)
                    .build());
        }
        return DocumentImageReferencesResponse.builder().resourceId(version.getResourceId()).documentId(version.getDocumentId())
                .version(version.getVersion()).images(references).unknownPageImageCount(unknown).build();
    }

    @Override
    public DocumentImageResolveResponse resolveImage(DocumentVersionEntity version, String assetRef) {
        assertReady(version);
        String key = MarkdownAssetLinkCodec.objectKey(assetRef, version.getDocumentId());
        // 复用列表接口的过滤与序号规则，保证“能解析的图必然在列表中可见”。
        if (listImageReferences(version, null).getImages().stream()
                .noneMatch(image -> key.equals(image.getObjectKey()))) {
            throw new ServiceException(DocumentError.DOCUMENT_IMAGE_NOT_FOUND);
        }
        long duration = properties.getDownloadUrlDurationSeconds();
        R<String> response = remoteStorageService.getDownloadUrl(key, duration, null);
        if (response == null || !Integer.valueOf(200).equals(response.getCode()) || response.getData() == null) {
            throw new ServiceException(DocumentError.DOCUMENT_DOWNLOAD_URL_APPLY_FAILED);
        }
        return DocumentImageResolveResponse.builder().resourceId(version.getResourceId()).documentId(version.getDocumentId())
                .version(version.getVersion()).objectKey(key).downloadUrl(response.getData()).expiresInSeconds(duration).build();
    }

    private void assertReady(DocumentVersionEntity version) {
        if (version.getDocumentStatus() == null || version.getDocumentStatus().getStatus() != DocumentStatusEnum.READY) {
            throw new ServiceException(DocumentError.DOCUMENT_PREVIEW_NOT_READY);
        }
    }

    @Override
    public String copyMarkdownAssets(String markdown, String sourceDocumentId, String targetDocumentId, List<String> copiedKeys) {
        if (markdown == null) return null;
        Map<String, String> targets = new HashMap<>();
        String copied = MarkdownImages.parse(markdown).rewrite(image -> {
            if (!image.source().startsWith(MarkdownAssetLinkCodec.SCHEME)) {
                return MarkdownImages.image(image.alt(), image.source());
            }
            if (!targets.containsKey(image.source())) {
                String target = null;
                try {
                    String sourceKey = MarkdownAssetLinkCodec.objectKey(image.source(), sourceDocumentId);
                    target = copyAsset(sourceKey, targetDocumentId, copiedKeys);
                } catch (Exception e) {
                    log.warn("document image copy skipped. sourceDocumentId={} documentId={}", sourceDocumentId, targetDocumentId, e);
                }
                targets.put(image.source(), target);
            }
            String target = targets.get(image.source());
            return target == null ? MarkdownImages.missing(image.alt()) : MarkdownImages.image(image.alt(), MarkdownAssetLinkCodec.link(target));
        });
        return MarkdownImages.withoutInlineData(copied);
    }

    private String copyAsset(String sourceKey, String targetDocumentId, List<String> copiedKeys) {
        String fileName = sourceKey.substring(sourceKey.lastIndexOf('/') + 1);
        String bizTag = DocumentAssetKey.assetsBizTag(targetDocumentId);
        R<StorageRecordDTO> response = remoteStorageService.copyFile(CopyReqDTO.builder().sourceObjectKey(sourceKey)
                .scene(StorageSceneEnum.PRIVATE_DOC).bizTag(bizTag).targetFileName(fileName).build());
        if (response == null || !Integer.valueOf(200).equals(response.getCode()) || response.getData() == null) {
            throw new ServiceException(DocumentError.DOCUMENT_FORK_FAILED);
        }
        copiedKeys.add(response.getData().getObjectKey());
        return response.getData().getObjectKey();
    }

    /** 图片已经在内存中；适配现有 MultipartFile 上传契约，不依赖测试用 MockMultipartFile。 */
    private record ImageFile(String fileName, byte[] bytes) implements MultipartFile {
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return fileName; }
        @Override public String getContentType() { return "application/octet-stream"; }
        @Override public boolean isEmpty() { return bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        @Override public void transferTo(File destination) throws IOException { Files.write(destination.toPath(), bytes); }
    }
}
