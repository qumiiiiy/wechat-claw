package com.weida.bot.wechatilinkbot.infrastructure.pptx;

import com.weida.bot.wechatilinkbot.model.PresentationRequest;
import com.weida.bot.wechatilinkbot.model.PresentationSlide;
import com.weida.bot.wechatilinkbot.model.PresentationSlide.BulletSlide;
import com.weida.bot.wechatilinkbot.model.PresentationSlide.EndingSlide;
import com.weida.bot.wechatilinkbot.model.PresentationSlide.PictureSlide;
import com.weida.bot.wechatilinkbot.model.PresentationSlide.TableSlide;
import com.weida.bot.wechatilinkbot.model.PresentationSlide.TitleSlide;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.apache.poi.sl.usermodel.PictureData.PictureType;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pure PPTX renderer. Builds an in-memory {@link XMLSlideShow} from a
 * validated {@link PresentationRequest} and serializes it to bytes; no
 * filesystem is touched here. The capability layer owns atomic-write
 * concerns.
 *
 * <p>Five layouts are supported in fixed slide order, matching the sealed
 * hierarchy in {@link PresentationSlide}: title, bullet, picture, table,
 * ending. Any layout can repeat as long as the per-slide data
 * validates.</p>
 */
@Component
public class PptxDocumentRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(PptxDocumentRenderer.class);

    private static final Pattern FORMULA_INJECTION_PREFIX =
            Pattern.compile("^[=+\\-@\\t\\r]");

    private final PptxDocumentProperties properties;

    public PptxDocumentRenderer(PptxDocumentProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties 不能为空");
    }

    public boolean isAvailable() {
        return properties.isEnabled();
    }

    public byte[] render(PresentationRequest request) {
        if (!isAvailable()) {
            throw new PptxGenerationException("PPTX 渲染未启用");
        }
        Objects.requireNonNull(request, "request 不能为空");
        validateRequest(request);

        try (XMLSlideShow slideShow = new XMLSlideShow();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (PresentationSlide slide : request.slides()) {
                renderSlide(slideShow, slide);
            }
            slideShow.write(output);
            byte[] bytes = output.toByteArray();
            if (bytes.length > properties.getMaxOutputBytes()) {
                throw new PptxGenerationException(
                        "生成的 PPTX 超过最大字节数：" + properties.getMaxOutputBytes());
            }
            LOG.info(
                    "pptx_rendered title={} slides={} bytes={}",
                    request.title(),
                    request.slides().size(),
                    bytes.length);
            return bytes;
        } catch (IOException exception) {
            throw new PptxGenerationException("PPTX 渲染失败", exception);
        }
    }

    private void validateRequest(PresentationRequest request) {
        if (request.title().length() > properties.getMaxTextCharacters()) {
            throw new PptxGenerationException(
                    "title 超过最大字符数：" + properties.getMaxTextCharacters());
        }
        List<PresentationSlide> slides = request.slides();
        if (slides.size() > properties.getMaxSlides()) {
            throw new PptxGenerationException(
                    "slide 数量超过最大限制：" + properties.getMaxSlides());
        }
        for (int i = 0; i < slides.size(); i++) {
            validateSlide(slides.get(i), i + 1);
        }
    }

    private void validateSlide(PresentationSlide slide, int index) {
        if (slide instanceof TitleSlide ts) {
            rejectInjection(ts.title(), "slide " + index + " title");
            rejectInjection(ts.subtitle(), "slide " + index + " subtitle");
        } else if (slide instanceof BulletSlide bs) {
            rejectInjection(bs.heading(), "slide " + index + " heading");
            if (bs.bullets().size() > properties.getMaxBullets()) {
                throw new PptxGenerationException(
                        "slide " + index + " bullets 超过最大限制："
                                + properties.getMaxBullets());
            }
            for (int b = 0; b < bs.bullets().size(); b++) {
                rejectInjection(
                        bs.bullets().get(b),
                        "slide " + index + " bullet " + (b + 1));
            }
        } else if (slide instanceof PictureSlide ps) {
            rejectInjection(ps.heading(), "slide " + index + " heading");
            rejectInjection(ps.caption(), "slide " + index + " caption");
            rejectInjection(ps.picturePath(), "slide " + index + " picturePath");
        } else if (slide instanceof TableSlide ts) {
            rejectInjection(ts.heading(), "slide " + index + " heading");
            if (ts.rows().size() > properties.getMaxTableRows()) {
                throw new PptxGenerationException(
                        "slide " + index + " table 行数超过最大限制："
                                + properties.getMaxTableRows());
            }
            int columns = -1;
            for (int r = 0; r < ts.rows().size(); r++) {
                List<String> row = ts.rows().get(r);
                if (columns < 0) {
                    columns = row.size();
                }
                if (row.size() != columns) {
                    throw new PptxGenerationException(
                            "slide " + index + " 表格第 " + (r + 1)
                                    + " 行列数与第一行不一致");
                }
                if (columns > properties.getMaxTableColumns()) {
                    throw new PptxGenerationException(
                            "slide " + index + " 表格列数超过最大限制："
                                    + properties.getMaxTableColumns());
                }
                for (String cell : row) {
                    rejectInjection(cell, "slide " + index + " 表格内容");
                }
            }
        } else if (slide instanceof EndingSlide es) {
            rejectInjection(es.message(), "slide " + index + " message");
        }
    }

    private void rejectInjection(String value, String where) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (FORMULA_INJECTION_PREFIX.matcher(value).find()) {
            throw new PptxGenerationException(
                    where + " 以公式注入前缀开头（首字符 0x"
                            + Integer.toHexString(value.charAt(0)) + "），拒绝写入");
        }
    }

    private void renderSlide(XMLSlideShow slideShow, PresentationSlide slide) {
        if (slide instanceof TitleSlide ts) {
            renderTitleSlide(slideShow, ts);
        } else if (slide instanceof BulletSlide bs) {
            renderBulletSlide(slideShow, bs);
        } else if (slide instanceof PictureSlide ps) {
            renderPictureSlide(slideShow, ps);
        } else if (slide instanceof TableSlide ts) {
            renderTableSlide(slideShow, ts);
        } else if (slide instanceof EndingSlide es) {
            renderEndingSlide(slideShow, es);
        }
    }

    private void renderTitleSlide(XMLSlideShow slideShow, TitleSlide slide) {
        XSLFSlide xs = slideShow.createSlide();
        addTextBox(xs, 50, 200, 600, 80, slide.title(), 36, true);
        if (!slide.subtitle().isEmpty()) {
            addTextBox(xs, 50, 320, 600, 60, slide.subtitle(), 24, false);
        }
    }

    private void renderBulletSlide(XMLSlideShow slideShow, BulletSlide slide) {
        XSLFSlide xs = slideShow.createSlide();
        addTextBox(xs, 50, 50, 600, 60, slide.heading(), 28, true);
        int top = 130;
        for (String bullet : slide.bullets()) {
            addTextBox(xs, 80, top, 560, 40, "• " + bullet, 20, false);
            top += 50;
        }
    }

    private void renderPictureSlide(XMLSlideShow slideShow, PictureSlide slide) {
        XSLFSlide xs = slideShow.createSlide();
        addTextBox(xs, 50, 50, 600, 50, slide.heading(), 28, true);
        Path path = Path.of(slide.picturePath());
        if (Files.isRegularFile(path)) {
            try {
                PictureType type = inferPictureType(path.getFileName().toString());
                byte[] pictureBytes = Files.readAllBytes(path);
                org.apache.poi.xslf.usermodel.XSLFPictureData pictureData =
                        slideShow.addPicture(pictureBytes, type);
                org.apache.poi.xslf.usermodel.XSLFPictureShape shape =
                        xs.createPicture(pictureData);
                shape.setAnchor(new java.awt.geom.Rectangle2D.Double(
                        50, 110, 600, 280));
            } catch (IOException exception) {
                // Pictures are best-effort: missing files render a blank
                // slide rather than failing the whole deck.
                LOG.warn("pptx_picture_missing path={}", slide.picturePath());
            }
        } else {
            LOG.warn("pptx_picture_missing path={}", slide.picturePath());
        }
        if (!slide.caption().isEmpty()) {
            addTextBox(xs, 50, 420, 600, 50, slide.caption(), 18, false);
        }
    }

    private static PictureType inferPictureType(String fileName) {
        String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".png")) return PictureType.PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return PictureType.JPEG;
        if (lower.endsWith(".gif")) return PictureType.GIF;
        if (lower.endsWith(".bmp")) return PictureType.BMP;
        if (lower.endsWith(".tiff") || lower.endsWith(".tif")) return PictureType.TIFF;
        return PictureType.PNG;
    }

    private void renderTableSlide(XMLSlideShow slideShow, TableSlide slide) {
        XSLFSlide xs = slideShow.createSlide();
        addTextBox(xs, 50, 50, 600, 50, slide.heading(), 28, true);
        List<List<String>> rows = slide.rows();
        int columns = rows.get(0).size();
        XSLFTable table = xs.createTable(rows.size(), columns);
        table.setAnchor(new java.awt.geom.Rectangle2D.Double(
                50, 130, 600, rows.size() * 32.0));
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            for (int c = 0; c < columns; c++) {
                table.getCell(r, c).setText(row.get(c));
            }
        }
    }

    private void renderEndingSlide(XMLSlideShow slideShow, EndingSlide slide) {
        XSLFSlide xs = slideShow.createSlide();
        addTextBox(xs, 50, 240, 600, 80, slide.message(), 36, true);
    }

    private static void addTextBox(
            XSLFSlide slide, int x, int y, int width, int height,
            String text, int fontSize, boolean bold) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new java.awt.geom.Rectangle2D.Double(
                x, y, width, height));
        box.setText(text);
        XSLFTextParagraph paragraph = box.getTextParagraphs().get(0);
        for (XSLFTextRun run : paragraph.getTextRuns()) {
            run.setFontSize((double) fontSize);
            run.setBold(bold);
        }
    }
}