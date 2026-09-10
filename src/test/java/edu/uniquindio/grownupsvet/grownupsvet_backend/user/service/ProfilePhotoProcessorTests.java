package edu.uniquindio.grownupsvet.grownupsvet_backend.user.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class ProfilePhotoProcessorTests {
    private final ProfilePhotoProcessor processor = new ProfilePhotoProcessor();

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void appliesEveryExifOrientationWithoutLosingPixels(int orientation) {
        BufferedImage source = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
        int color = 1;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.setRGB(x, y, color++);
            }
        }

        BufferedImage result = processor.orient(source, orientation);
        int expectedWidth = orientation >= 5 ? source.getHeight() : source.getWidth();
        int expectedHeight = orientation >= 5 ? source.getWidth() : source.getHeight();
        assertThat(result.getWidth()).isEqualTo(expectedWidth);
        assertThat(result.getHeight()).isEqualTo(expectedHeight);

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int[] destination = destination(orientation, x, y, source.getWidth(), source.getHeight());
                assertThat(result.getRGB(destination[0], destination[1]))
                        .as("orientation %s, source (%s,%s)", orientation, x, y)
                        .isEqualTo(source.getRGB(x, y));
            }
        }
    }

    private int[] destination(int orientation, int x, int y, int width, int height) {
        return switch (orientation) {
            case 2 -> new int[]{width - 1 - x, y};
            case 3 -> new int[]{width - 1 - x, height - 1 - y};
            case 4 -> new int[]{x, height - 1 - y};
            case 5 -> new int[]{y, x};
            case 6 -> new int[]{height - 1 - y, x};
            case 7 -> new int[]{height - 1 - y, width - 1 - x};
            case 8 -> new int[]{y, width - 1 - x};
            default -> new int[]{x, y};
        };
    }
}
