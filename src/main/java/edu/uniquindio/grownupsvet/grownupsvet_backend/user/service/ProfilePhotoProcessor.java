package edu.uniquindio.grownupsvet.grownupsvet_backend.user.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.exception.InvalidProfilePhotoException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

import static edu.uniquindio.grownupsvet.grownupsvet_backend.user.exception.InvalidProfilePhotoException.Reason.INVALID_CONTENT;
import static edu.uniquindio.grownupsvet.grownupsvet_backend.user.exception.InvalidProfilePhotoException.Reason.INVALID_DIMENSIONS;
import static edu.uniquindio.grownupsvet.grownupsvet_backend.user.exception.InvalidProfilePhotoException.Reason.TOO_LARGE;
import static edu.uniquindio.grownupsvet.grownupsvet_backend.user.exception.InvalidProfilePhotoException.Reason.UNSUPPORTED_TYPE;

@Component
public class ProfilePhotoProcessor {
    public static final int MAX_INPUT_BYTES = 2 * 1024 * 1024;
    public static final int MAX_INPUT_SIDE = 8_000;
    public static final long MAX_INPUT_PIXELS = 20_000_000L;
    public static final int MAX_OUTPUT_SIDE = 512;

    public ProcessedProfilePhoto process(byte[] input) {
        if (input.length > MAX_INPUT_BYTES) {
            throw new InvalidProfilePhotoException(TOO_LARGE);
        }

        try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            if (imageInput == null) {
                throw new InvalidProfilePhotoException(INVALID_CONTENT);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new InvalidProfilePhotoException(INVALID_CONTENT);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String format = normalizeFormat(reader.getFormatName());
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new InvalidProfilePhotoException(INVALID_CONTENT);
                }
                BufferedImage oriented = orient(decoded, readExifOrientation(input));
                BufferedImage resized = resize(oriented, format);
                byte[] output = encode(resized, format);
                if (output.length > MAX_INPUT_BYTES) {
                    throw new InvalidProfilePhotoException(TOO_LARGE);
                }
                return new ProcessedProfilePhoto(output, mediaType(format), resized.getWidth(), resized.getHeight());
            } finally {
                reader.dispose();
            }
        } catch (InvalidProfilePhotoException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new InvalidProfilePhotoException(INVALID_CONTENT, exception);
        }
    }

    private String normalizeFormat(String readerFormat) {
        String format = readerFormat.toLowerCase(Locale.ROOT);
        if (format.equals("jpg") || format.equals("jpeg")) {
            return "jpeg";
        }
        if (format.equals("png")) {
            return "png";
        }
        throw new InvalidProfilePhotoException(UNSUPPORTED_TYPE);
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || width > MAX_INPUT_SIDE || height > MAX_INPUT_SIDE
                || (long) width * height > MAX_INPUT_PIXELS) {
            throw new InvalidProfilePhotoException(INVALID_DIMENSIONS);
        }
    }

    private int readExifOrientation(byte[] input) {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(input)) {
            Metadata metadata = ImageMetadataReader.readMetadata(stream);
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            return directory == null ? 1 : directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
        } catch (Exception exception) {
            return 1;
        }
    }

    BufferedImage orient(BufferedImage source, int orientation) {
        if (orientation < 2 || orientation > 8) {
            return source;
        }
        int width = source.getWidth();
        int height = source.getHeight();
        boolean swapsSides = orientation >= 5;
        BufferedImage target = new BufferedImage(swapsSides ? height : width, swapsSides ? width : height,
                source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        AffineTransform transform = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> new AffineTransform();
        };
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, transform, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private BufferedImage resize(BufferedImage source, String format) {
        double scale = Math.min(1.0, (double) MAX_OUTPUT_SIDE / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int type = format.equals("png") && source.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(width, height, type);
        Graphics2D graphics = target.createGraphics();
        try {
            if (type == BufferedImage.TYPE_INT_RGB) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
            }
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, output) || output.size() == 0) {
            throw new InvalidProfilePhotoException(INVALID_CONTENT);
        }
        return output.toByteArray();
    }

    private String mediaType(String format) { return "image/" + format; }

    public record ProcessedProfilePhoto(byte[] content, String contentType, int width, int height) { }
}
