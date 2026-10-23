package org.traccar.reports.common;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Validation for the logo and stamp images embedded in PDF reports.
 */
public final class ReportImage {

    public static final int SIZE_LIMIT = 500000;
    public static final int DIMENSION_LIMIT = 4000;
    public static final String[] EXTENSIONS = {"png", "jpg", "jpeg"};

    private ReportImage() {
    }

    /**
     * Checks that the data is a PNG or JPEG image that actually decodes and returns its file extension.
     * The detected format is used, not the uploaded content type.
     *
     * @throws IllegalArgumentException if the data is too large, not a supported image or can't be decoded
     */
    public static String validate(byte[] data) {
        if (data.length > SIZE_LIMIT) {
            throw new IllegalArgumentException("Image size limit exceeded");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = input != null ? ImageIO.getImageReaders(input) : null;
            if (readers == null || !readers.hasNext()) {
                throw new IllegalArgumentException("Not a valid image");
            }
            ImageReader reader = readers.next();
            try {
                String extension = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "png" -> "png";
                    case "jpeg", "jpg" -> "jpg";
                    default -> throw new IllegalArgumentException("Only PNG and JPEG images are supported");
                };
                reader.setInput(input, true, true);
                // check dimensions before decoding so a small file can't expand into a huge bitmap
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > DIMENSION_LIMIT || height > DIMENSION_LIMIT) {
                    throw new IllegalArgumentException(
                            "Image dimensions must be at most " + DIMENSION_LIMIT + " x " + DIMENSION_LIMIT);
                }
                reader.read(0);
                return extension;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Not a valid image", e);
        }
    }

    public static String mimeType(String extension) {
        return "png".equals(extension) ? "image/png" : "image/jpeg";
    }

}
