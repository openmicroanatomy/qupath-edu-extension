package qupath.edu.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.servers.FileFormatInfo;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;

import java.awt.image.BufferedImage;
import java.net.URI;

public class EduServerBuilder implements ImageServerBuilder<BufferedImage> {

    private static Logger logger = LoggerFactory.getLogger(EduServerBuilder.class);

    static {
        // TODO: Does this require some initial checks?
    }

    @Override
    public ImageServer<BufferedImage> buildServer(URI uri, String...args) {
        try {
            return new EduImageServer(uri, args);
        } catch (Exception e) {
            logger.warn("Unable to open {} with EduServer: {}", uri, e.getLocalizedMessage());
        }

        return null;
    }

    @Override
    public UriImageSupport<BufferedImage> checkImageSupport(URI uri, String...args) {
        float supportLevel = supportLevel(uri, args);
        return UriImageSupport.createInstance(this.getClass(), supportLevel, DefaultImageServerBuilder.createInstance(this.getClass(), uri, args));
    }

    private static float supportLevel(URI uri, String...args) {
        FileFormatInfo.ImageCheckType type = FileFormatInfo.checkType(uri);

        if (type.isURL() && uri.getScheme().startsWith("http")) {
            // TODO: Validate that is proper URL we can handle.
            return 4f;
        }

        return 0f;
    }

    @Override
    public String getName() {
        return "EduServer Builder";
    }

    @Override
    public String getDescription() {
        return "EduServer provides access to slides served remotely that are supported by OpenSlide";
    }

    @Override
    public Class<BufferedImage> getImageType() {
        return BufferedImage.class;
    }
}
