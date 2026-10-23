package org.traccar.reports.common;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ReportImageTest {

    private static byte[] image(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }

    @Test
    public void testValidImages() throws IOException {
        assertEquals("png", ReportImage.validate(image("png", 40, 20)));
        assertEquals("jpg", ReportImage.validate(image("jpeg", 40, 20)));
    }

    @Test
    public void testInvalidImages() throws IOException {
        byte[] png = image("png", 40, 20);

        assertThrows(IllegalArgumentException.class,
                () -> ReportImage.validate("not an image".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,
                () -> ReportImage.validate(Arrays.copyOf(png, png.length / 2)));
        assertThrows(IllegalArgumentException.class,
                () -> ReportImage.validate(image("gif", 40, 20)));
        assertThrows(IllegalArgumentException.class,
                () -> ReportImage.validate(image("png", ReportImage.DIMENSION_LIMIT + 1, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ReportImage.validate(new byte[ReportImage.SIZE_LIMIT + 1]));
    }

}
