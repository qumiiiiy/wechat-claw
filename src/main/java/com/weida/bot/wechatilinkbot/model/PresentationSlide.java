package com.weida.bot.wechatilinkbot.model;

import java.util.List;
import java.util.Objects;

/**
 * Slide-level payloads for {@link PresentationRequest}. Sealed hierarchy
 * keeps the renderer's switch over layout kinds exhaustive at compile
 * time. Each variant carries only the data needed by its corresponding
 * layout; cross-variant fields (pictures, tables) are intentionally
 * repeated rather than pushed up to the parent so the renderer can switch
 * on type without untangling.
 */
public sealed interface PresentationSlide
        permits PresentationSlide.TitleSlide,
        PresentationSlide.BulletSlide,
        PresentationSlide.PictureSlide,
        PresentationSlide.TableSlide,
        PresentationSlide.EndingSlide {

    record TitleSlide(String title, String subtitle) implements PresentationSlide {
        public TitleSlide {
            Objects.requireNonNull(title, "title 不能为空");
            if (title.isBlank()) {
                throw new IllegalArgumentException("title 不能为空");
            }
            subtitle = subtitle == null ? "" : subtitle;
        }
    }

    record BulletSlide(String heading, List<String> bullets) implements PresentationSlide {
        public BulletSlide {
            Objects.requireNonNull(heading, "heading 不能为空");
            if (heading.isBlank()) {
                throw new IllegalArgumentException("heading 不能为空");
            }
            if (bullets == null || bullets.isEmpty()) {
                throw new IllegalArgumentException("bullets 不能为空");
            }
            bullets = List.copyOf(bullets);
        }
    }

    record PictureSlide(String heading, String caption, String picturePath)
            implements PresentationSlide {
        public PictureSlide {
            Objects.requireNonNull(heading, "heading 不能为空");
            if (heading.isBlank()) {
                throw new IllegalArgumentException("heading 不能为空");
            }
            caption = caption == null ? "" : caption;
            Objects.requireNonNull(picturePath, "picturePath 不能为空");
            if (picturePath.isBlank()) {
                throw new IllegalArgumentException("picturePath 不能为空");
            }
        }
    }

    record TableSlide(String heading, List<List<String>> rows) implements PresentationSlide {
        public TableSlide {
            Objects.requireNonNull(heading, "heading 不能为空");
            if (heading.isBlank()) {
                throw new IllegalArgumentException("heading 不能为空");
            }
            if (rows == null || rows.isEmpty()) {
                throw new IllegalArgumentException("rows 不能为空");
            }
            List<List<String>> copy = new java.util.ArrayList<>(rows.size());
            for (List<String> row : rows) {
                if (row == null || row.isEmpty()) {
                    throw new IllegalArgumentException("表格行不能为空");
                }
                copy.add(List.copyOf(row));
            }
            rows = List.copyOf(copy);
        }
    }

    record EndingSlide(String message) implements PresentationSlide {
        public EndingSlide {
            Objects.requireNonNull(message, "message 不能为空");
            if (message.isBlank()) {
                throw new IllegalArgumentException("message 不能为空");
            }
        }
    }
}