package com.oriole.wisepen.document.util;

import com.oriole.wisepen.document.config.DocumentProperties;
import com.oriole.wisepen.document.domain.entity.DocumentPdfMetaEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.oriole.wisepen.document.api.constant.DocumentConstants.WATERMARK_SIZE;


@Slf4j
@Component
@RequiredArgsConstructor
public class WatermarkPreProcessor {

    private final DocumentProperties documentProperties;

    /**
     * 对原始 PDF 进行安全加工的聚合入口。
     * <p>
     * 此处发生了一次隐式的二次 I/O
     * 先完成 inject 的落盘（save），然后再重新 load 提取 meta，以避免 XREF 偏移量等信息不准确
     *
     * @param source 原始 PDF
     * @param target 加工后的 PDF 输出路径
     */
    public DocumentPdfMetaEntity processAndExtractMeta(File source, File target) throws IOException {
        // 预埋空水印占位 Form XObject（/WisepenWM）和追加烧录免责声明，生成 hooked PDF 并落盘
        injectHooksAndDisclaimer(source, target);
        // 重新读取已落盘的 hooked PDF，提取用于增量更新的物理坐标元信息
        DocumentPdfMetaEntity meta = extractPdfMeta(target);
        // 写入全局约定的 Padding 定长大小
        meta.setAppendixSize(WATERMARK_SIZE);
        return meta;
    }

    /**
     * 在 PDF 中预埋空的 Form XObject（/WisepenWM）并追加底部免责声明。
     *
     * @param source    干净的 PDF 源文件（经过 Office 转换或直接来自上传）
     * @param hookedPdf 输出路径，写入预埋后的 PDF
     */
    private void injectHooksAndDisclaimer(File source, File hookedPdf) throws IOException {
        COSName wmName = COSName.getPDFName("WisepenWM");
        try (PDDocument doc = PDDocument.load(source)) {
            // 加载 CJK 字体（可能为 null，为空时中文行静默跳过）
            PDFont cjkRegular = loadCjkFont(doc, false);
            PDFont cjkBold = loadCjkFont(doc, true);

            // 创建空 Form XObject（内容仅 "q Q"，作为占位符）
            PDFormXObject emptyForm = new PDFormXObject(doc);
            PDPage firstPage = doc.getPage(0);
            emptyForm.setBBox(firstPage.getMediaBox());
            try (OutputStream cs = ((COSStream) emptyForm.getCOSObject()).createOutputStream()) {
                cs.write("q Q\n".getBytes(StandardCharsets.US_ASCII));
            }

            // 遍历每一页
            for (PDPage page : doc.getPages()) {
                // 将 /WisepenWM 声明到当前页的资源字典中，使其成为合法的可调用组件
                PDResources resources = page.getResources();
                if (resources == null) {
                    resources = new PDResources();
                    page.setResources(resources);
                }
                resources.put(wmName, emptyForm);

                // 在页面现有内容的上追加 /WisepenWM Do 调用流
                PDStream callStream = new PDStream(doc);
                try (OutputStream callCs = callStream.createOutputStream()) {
                    callCs.write("q /WisepenWM Do Q\n".getBytes(StandardCharsets.US_ASCII));
                }

                // 将新指令流合并到页面原有的 Contents 数组中
                COSBase existing = page.getCOSObject().getDictionaryObject(COSName.CONTENTS);
                COSArray contents;
                if (existing instanceof COSArray existingArr) {
                    contents = existingArr;
                } else {
                    contents = new COSArray();
                    if (existing != null) {
                        contents.add(existing);
                    }
                }
                contents.add(callStream.getCOSObject());
                page.getCOSObject().setItem(COSName.CONTENTS, contents);

                // 追加底部法律免责声明
                drawLegalDisclaimer(doc, page, cjkRegular, cjkBold);
            }

            doc.save(hookedPdf);
        }
        log.debug("watermark hook embedded. source={} hookedPdf={}", source.getName(), hookedPdf.getName());
    }

    /**
     * 在页面底部追加双语法律免责声明（烧录，永久可见）。
     *
     * <p>布局（从上到下）：
     * <pre>
     *   [CJK Bold 红色 6.5pt] 仅供学术交流与课堂教学使用
     *   [CJK Regular 灰色 5.5pt] 严禁出版或公开发布，违者须承担全部侵权法律后果。
     *   [CJK Regular 灰色 5.5pt] 文档已启用隐形溯源技术，必要时将依法向著作权人或法院披露泄露者信息。
     *   [Helvetica-Bold 红色 6.5pt] Strictly for Academic and Instructional Use.
     *   [Helvetica 灰色 5.5pt] Publication or online distribution is prohibited...
     *   [Helvetica 灰色 5.5pt] Embedded tracking mechanisms are active...
     * </pre>
     * 总高度约 50pt，从页面底边 5pt 起始。若 CJK 字体不可用，中文三行静默跳过。
     *
     * @param cjkRegular CJK 常规字体（null 则跳过中文行）
     * @param cjkBold    CJK 粗体字体（null 时回退到 cjkRegular，仍为 null 则跳过中文行）
     */
    private void drawLegalDisclaimer(PDDocument doc, PDPage page,
                                  PDFont cjkRegular, PDFont cjkBold) throws IOException {
        PDRectangle box = page.getMediaBox();
        float baseY = box.getLowerLeftY() + 5f;
        float marginX = 10f;
        float lineH = 8f;

        // y 坐标从底部向上依次排列（英文在下，中文在上）
        float yEn3 = baseY;
        float yEn2 = baseY + lineH;
        float yEn1 = baseY + lineH * 2f;
        float yCn3 = baseY + lineH * 3f;
        float yCn2 = baseY + lineH * 4f;
        float yCn1 = baseY + lineH * 5f;

        // resetContext=true：PDFBox 自动以 q/Q 包裹，隔离图形状态
        try (PDPageContentStream cs = new PDPageContentStream(
                doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

            // ── 英文第 3 行（灰色） ──────────────────────────────────────────
            drawText(cs, PDType1Font.HELVETICA, 5.5f,
                    new java.awt.Color(50, 50, 50), marginX, yEn3,
                    "Embedded tracking mechanisms are active; violator information will be "
                            + "disclosed to copyright owners or courts upon lawful request.");

            // ── 英文第 2 行（灰色） ──────────────────────────────────────────
            drawText(cs, PDType1Font.HELVETICA, 5.5f,
                    new java.awt.Color(50, 50, 50), marginX, yEn2,
                    "Publication or online distribution is prohibited. "
                            + "Violators assume full legal liability for any infringement.");

            // ── 英文第 1 行（加粗红色） ──────────────────────────────────────
            drawText(cs, PDType1Font.HELVETICA_BOLD, 6.5f,
                    new java.awt.Color(204, 0, 0), marginX, yEn1,
                    "Strictly for Academic and Instructional Use.");

            // ── 中文三行（需要 CJK 字体） ────────────────────────────────────
            if (cjkRegular != null) {
                PDFont boldFont = (cjkBold != null) ? cjkBold : cjkRegular;

                drawText(cs, cjkRegular, 5.5f,
                        new java.awt.Color(50, 50, 50), marginX, yCn3,
                        "文档已启用隐形溯源技术，必要时将依法向著作权人或法院披露泄露者信息。");

                drawText(cs, cjkRegular, 5.5f,
                        new java.awt.Color(50, 50, 50), marginX, yCn2,
                        "严禁出版或公开发布，违者须承担全部侵权法律后果。");

                drawText(cs, boldFont, 6.5f,
                        new java.awt.Color(204, 0, 0), marginX, yCn1,
                        "仅供内部学术交流与课堂教学使用");
            }
        }
    }

    /** 在指定坐标绘制单行文字，独立管理图形状态以避免颜色/字体污染。 */
    private static void drawText(PDPageContentStream cs,
                                 PDFont font, float fontSize,
                                 java.awt.Color color,
                                 float x, float y,
                                 String text) throws IOException {
        cs.saveGraphicsState();
        cs.setNonStrokingColor(color);
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
        cs.restoreGraphicsState();
    }

    /**
     * 从配置路径加载 CJK 字体，失败时静默返回 null。
     *
     * @param bold 为 true 则读取粗体字体路径，否则读取常规字体路径
     * @return 加载成功的 PDFont，或 null（路径未配置 / 文件不存在 / 加载失败）
     */
    private PDFont loadCjkFont(PDDocument doc, boolean bold) {
        String path = bold
                ? documentProperties.getWatermark().getFonts().getBoldPath()
                : documentProperties.getWatermark().getFonts().getRegularPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        File fontFile = new File(path);
        if (!fontFile.exists()) {
            log.warn("cjk font not found. path={}", path);
            return null;
        }
        try (java.io.FileInputStream fis = new java.io.FileInputStream(fontFile)) {
            return PDType0Font.load(doc, fis, true);
        } catch (IOException e) {
            log.warn("cjk font load failed. path={}", path, e);
            return null;
        }
    }

    /**
     * 通过对象身份比对，在 COSDocument 中找到 /WisepenWM 空容器的真实对象号。
     * <p>
     * 从第一页的 Resources 字典中取出 /WisepenWM 引用的 COSStream 实例，
     * 然后在 XREF 对象表中按 {@code ==} 比对找到匹配的 COSObjectKey。
     */
    private int locatePlaceholderObjNum(PDDocument doc, COSDocument cos) {
        try {
            PDResources res = doc.getPage(0).getResources();
            if (res == null) return 0;
            COSDictionary xobjDict = (COSDictionary) res.getCOSObject()
                    .getDictionaryObject(COSName.XOBJECT);
            if (xobjDict == null) return 0;

            // 拿到第一页引用的 WisepenWM 对象实例
            COSBase wmBase = xobjDict.getDictionaryObject(COSName.getPDFName("WisepenWM"));
            if (wmBase == null) return 0;

            // 在全局 XREF 表中通过身份比对找出它的对象号
            for (COSObject obj : cos.getObjectsByType(COSName.XOBJECT)) {
                if (obj.getObject() == wmBase) {
                    return (int) obj.getObjectNumber();
                }
            }
        } catch (Exception e) {
            log.warn("pre hook object number locate failed.", e);
        }
        return 0;
    }

    /**
     * 从本地 PDF 文件中读取 PDF 结构信息，用于后续增量更新式水印附录的预计算。
     * 在文件落盘后执行，以确保获取的物理字节偏移量准确
     * <p>
     * 采集内容：
     * <ul>
     *   <li>{@code originalSize}：文件字节大小</li>
     *   <li>{@code xrefOffset}：最后一个 XREF 段的字节偏移（startxref 值）</li>
     *   <li>{@code lastObjectId}：PDF 中最高的对象编号</li>
     *   <li>每页的对象编号、代号及媒体框尺寸</li>
     * </ul>
     */
    private DocumentPdfMetaEntity extractPdfMeta(File pdfFile) throws IOException {
        DocumentPdfMetaEntity meta = new DocumentPdfMetaEntity();

        // 提取原文件的绝对物理大小，作为追加的起始游标 (currentOffset)
        meta.setOriginalSize(pdfFile.length());

        try (PDDocument doc = PDDocument.load(pdfFile)) {
            COSDocument cos = doc.getDocument();

            // 提取老路由表的物理位置，用于追加时构建 /Prev 单向链表
            meta.setXrefOffset(cos.getStartXref());

            // 遍历寻找最大的对象编号，追加时生成的新对象将在此基础上递增分配
            int maxObjNum = 0;
            for (COSObject obj : cos.getObjects()) {
                if (obj.getObjectNumber() > maxObjNum) {
                    maxObjNum = (int) obj.getObjectNumber();
                }
            }
            meta.setLastObjectId(maxObjNum);

            // 提取 /Root 对象编号，供增量更新 Trailer 引用
            COSBase rootItem = cos.getTrailer().getItem(COSName.ROOT);
            if (rootItem instanceof COSObject cosRoot) {
                meta.setCatalogObjNum((int) cosRoot.getObjectNumber());
            }

            // 提取页面元数据（宽、高），供后续计算暗水印的平铺网格数 (Rows/Cols)
            Map<COSDictionary, long[]> dictToObj = new HashMap<>();
            for (COSObject obj : cos.getObjectsByType(COSName.PAGE)) {
                if (obj.getObject() instanceof COSDictionary dict) {
                    dictToObj.put(dict, new long[]{obj.getObjectNumber(), obj.getGenerationNumber()});
                }
            }

            List<DocumentPdfMetaEntity.PageMeta> pages = new ArrayList<>();
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                PDPage page = doc.getPage(i);
                long[] objInfo = dictToObj.get(page.getCOSObject());
                PDRectangle box = page.getMediaBox();

                DocumentPdfMetaEntity.PageMeta pm = new DocumentPdfMetaEntity.PageMeta();
                pm.setObjNum(objInfo != null ? (int) objInfo[0] : 0);
                pm.setGenNum(objInfo != null ? (int) objInfo[1] : 0);
                pm.setWidthPt(box.getWidth());
                pm.setHeightPt(box.getHeight());
                pages.add(pm);
            }
            meta.setPages(pages);

            // 找出预埋的 /WisepenWM Form XObject 的对象编号
            meta.setPreHookObjNum(locatePlaceholderObjNum(doc, cos));
        }
        return meta;
    }
}
