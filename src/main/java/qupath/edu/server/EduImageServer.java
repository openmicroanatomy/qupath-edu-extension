package qupath.edu.server;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.api.EduAPI;
import qupath.lib.images.servers.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.List;

/**
 * EduImageServer is based on the OpenslideImageServer implementation
 *
 * TODO:
 *  - Associated images
 *  - Bounds
 *  - BioFormats support [These changes need to be made on the server most likely]
 *
 * @author Pete Bankhead, Aaron Yli-Hallila
 *
 */
public class EduImageServer extends AbstractTileableImageServer {

    private final static Logger logger = LoggerFactory.getLogger(EduImageServer.class);

    private ImageServerMetadata originalMetadata;

    private Color backgroundColor;

    private int boundsX, boundsY, boundsWidth, boundsHeight;

    private URI uri;
    private String[] args;

    private String serverURI;


    private static double readJsonPropertyOrDefault(JsonObject json, String parameter, double defaultValue) {
        if (json.has(parameter)) {
            return json.get(parameter).getAsDouble();
        } else {
            return defaultValue;
        }
    }

    public EduImageServer(URI uri, String...args) throws IOException {
        super();
        this.uri = uri;

        // Ensure the garbage collector has run - otherwise any previous attempts to load the required native library
        // from different classloader are likely to cause an error (although upon first further investigation it seems this doesn't really solve the problem...)
        System.gc();

        initialize(args);
    }

    private void initialize(String... args) throws IOException {
        Optional<JsonObject> properties = EduAPI.getSlideProperties(uri);

        if (properties.isEmpty()) {
            throw new IOException("Error when loading remote slide, properties were empty. See log for more information");
        }

        JsonObject json = properties.get();

        this.serverURI = json.get("openslide.remoteserver.uri").getAsString();

        int width = json.get("openslide.level[0].width").getAsInt();
        int height = json.get("openslide.level[0].height").getAsInt();

        // Bounds currently not supported by EduImageServer
        boundsX = 0;
        boundsY = 0;
        boundsWidth = width;
        boundsHeight = height;

        int tileWidth = (int) readJsonPropertyOrDefault(json, "openslide.level[0].tile-width", 256);
        int tileHeight = (int) readJsonPropertyOrDefault(json, "openslide.level[0].tile-height", 256);

        double pixelWidth = readJsonPropertyOrDefault(json, "openslide.mpp-x", Double.NaN);
        double pixelHeight = readJsonPropertyOrDefault(json, "openslide.mpp-y", Double.NaN);
        double magnification = readJsonPropertyOrDefault(json, "openslide.objective-power", Double.NaN);

        // Make sure the pixel sizes are valid
        if (pixelWidth <= 0 || pixelHeight <= 0 || Double.isInfinite(pixelWidth) || Double.isInfinite(pixelHeight)) {
            logger.warn("Invalid pixel sizes {} and {}, will use default", pixelWidth, pixelHeight);
            pixelWidth = Double.NaN;
            pixelHeight = Double.NaN;
        }

        // Loop through the series again & determine downsamples - assume the image is not cropped for now
        int levelCount = json.get("openslide.level-count").getAsInt();
        var resolutionBuilder = new ImageServerMetadata.ImageResolutionLevel.Builder(width, height);
        for (int i = 0; i < levelCount; i++) {
            // When requesting downsamples from OpenSlide, these seem to be averaged from the width & height ratios:
            // https://github.com/openslide/openslide/blob/7b99a8604f38280d14a34db6bda7a916563f96e1/src/openslide.c#L272
            // However this can result in inexact floating point values whenever the 'true' downsample is
            // almost certainly an integer value, therefore we prefer to use our own calculation.
            // Other ImageServer implementations can also draw on our calculation for consistency (or override it if they can do better).
            int w = json.get("openslide.level[" + i + "].width").getAsInt();
            int h = json.get("openslide.level[" + i + "].height").getAsInt();
            resolutionBuilder.addLevel(w, h);
        }
        var levels = resolutionBuilder.build();

        String path = uri.toString();

        int z = 1;

        if (json.has("openslidex.depth")) {
            z = json.get("openslidex.depth").getAsInt();
        }

        String name;

        if (uri.getFragment() != null) {
            name = EduAPI.d(uri.getFragment());
        } else {
            name = uri.getPath().substring(1);
        }

        this.args = args;
        originalMetadata = new ImageServerMetadata.Builder(getClass(),
                path, boundsWidth, boundsHeight).
                channels(ImageChannel.getDefaultRGBChannels()). // Assume 3 channels (RGB)
                name(name).
                rgb(true).
                pixelType(PixelType.UINT8).
                preferredTileSize(tileWidth, tileHeight).
                pixelSizeMicrons(pixelWidth, pixelHeight).
                magnification(magnification).
                levels(levels).
                sizeZ(z).
                build();

        // Try to get a background color
        try {
            String bg = json.get("openslide.background-color").getAsString();
            if (bg != null) {
                if (!bg.startsWith("#"))
                    bg = "#" + bg;
                backgroundColor = Color.decode(bg);
            }
        } catch (Exception e) {
            backgroundColor = null;
            logger.debug("Unable to find background color: {}", e.getLocalizedMessage());
        }
    }

    @Override
    public Collection<URI> getURIs() {
        return Collections.singletonList(uri);
    }

    @Override
    protected String createID() {
        return getClass().getName() + ": " + uri.toString();
    }

    @Override
    public void close() {
        logger.debug("Edu ImageServer closed. Should this do something?");
    }

    @Override
    public String getServerType() {
        return "Edu";
    }

    @Override
    public BufferedImage readTile(TileRequest tileRequest) {
        int tileX = tileRequest.getImageX() + boundsX;
        int tileY = tileRequest.getImageY() + boundsY;
        int level = tileRequest.getLevel();
        int tileWidth = tileRequest.getTileWidth();
        int tileHeight = tileRequest.getTileHeight();
        int depth = tileRequest.getZ();

        URI uriRegion = EduAPI.getRenderRegionURL(
                this.serverURI,
                uri.getPath().substring(1),
                tileX, tileY,
                level,
                tileWidth,
                tileHeight,
                depth
        );

        try {
            return ImageIO.read(uriRegion.toURL());
        } catch (IOException e) {
            if (backgroundColor == null && e.getCause().getClass() != FileNotFoundException.class) {
                logger.error("Error when loading remotely tile", e);
            }
        }

        if (backgroundColor != null) {
            BufferedImage img = new BufferedImage(tileWidth, tileHeight, BufferedImage.TYPE_INT_ARGB);

            Graphics2D g2d = img.createGraphics();
            g2d.setColor(backgroundColor);
            g2d.fillRect(0, 0, tileWidth, tileHeight);
            g2d.dispose();

            return img;
        }

        return null;
    }

    @Override
    public List<String> getAssociatedImageList() {
        return Collections.emptyList();
    }

    @Override
    protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
        return ImageServerBuilder.DefaultImageServerBuilder.createInstance(EduServerBuilder.class, getMetadata(), uri, args);
    }

    @Override
    public BufferedImage getAssociatedImage(String name) {
        return null;
    }

    @Override
    public ImageServerMetadata getOriginalMetadata() {
        return originalMetadata;
    }
}

