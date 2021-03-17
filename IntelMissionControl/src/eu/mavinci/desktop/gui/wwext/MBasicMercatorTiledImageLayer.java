/*
 * This Source is licenced under the NASA OPEN SOURCE AGREEMENT VERSION 1.3
 *
 * Copyright (C) 2012 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration.
 * All Rights Reserved.
 *
 * Modifications by MAVinci GmbH, Germany (C) 2009-2016: add hiding of empty tiles
 *
 */
package eu.mavinci.desktop.gui.wwext;

import com.intel.missioncontrol.PublishSource;
import com.jogamp.opengl.util.texture.TextureData;
import eu.mavinci.desktop.main.debug.Debug;
import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.cache.BasicMemoryCache;
import gov.nasa.worldwind.cache.MemoryCache;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.Vec4;
import gov.nasa.worldwind.layers.mercator.MercatorSector;
import gov.nasa.worldwind.layers.mercator.MercatorTextureTile;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.retrieve.HTTPRetriever;
import gov.nasa.worldwind.retrieve.RetrievalPostProcessor;
import gov.nasa.worldwind.retrieve.Retriever;
import gov.nasa.worldwind.retrieve.URLRetriever;
import gov.nasa.worldwind.util.LevelSet;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.OGLUtil;
import gov.nasa.worldwind.util.WWIO;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.math.DoubleMath.log2;

@PublishSource(module = "World Wind", licenses = "nasa-world-wind")
public class MBasicMercatorTiledImageLayer extends MMercatorTiledImageLayer {
    private static final int MAX_BLACK_TILE_SIZE = 2000;
    private static final int MAX_BLACK_TILE_SIZE_SUSPICIOUS = 4500;
    private static final double THRESHOLD_BLACK_IMG = 2.0;
    private final Object fileLock = new Object();

    public MBasicMercatorTiledImageLayer(LevelSet levelSet) {
        super(levelSet);
        if (!WorldWind.getMemoryCacheSet().containsCache(MercatorTextureTile.class.getName())) {
            long size = Configuration.getLongValue(AVKey.TEXTURE_IMAGE_CACHE_SIZE, 3000000L);
            MemoryCache cache = new BasicMemoryCache((long)(0.85 * size), size);
            cache.setName("Texture Tiles");
            WorldWind.getMemoryCacheSet().addCache(MercatorTextureTile.class.getName(), cache);
        }
    }

    public MBasicMercatorTiledImageLayer(AVList params) {
        this(new LevelSet(params));
        this.setValue(AVKey.CONSTRUCTION_PARAMETERS, params);
    }

    protected void forceTextureLoad(MercatorTextureTile tile) {
        final URL textureURL = this.getDataFileStore().findFile(tile.getPath(), true);

        if (textureURL != null && !this.isTextureExpired(tile, textureURL)) {
            this.loadTexture(tile, textureURL);
        }
    }

    protected void requestTexture(DrawContext dc, MercatorTextureTile tile) {
        Vec4 centroid = tile.getCentroidPoint(dc.getGlobe());
        if (this.getReferencePoint() != null) {
            tile.setPriority(centroid.distanceTo3(this.getReferencePoint()));
        }

        RequestTask task = new RequestTask(tile, this);
        if (!WorldWind.getTaskService().isFull()) {
            WorldWind.getTaskService().addTask(task);
        }else{
            this.getRequestQ().add(task);
        }
    }

    private static class RequestTask implements Runnable, Comparable<RequestTask> {
        private final MBasicMercatorTiledImageLayer layer;
        private final MercatorTextureTile tile;

        private RequestTask(MercatorTextureTile tile, MBasicMercatorTiledImageLayer layer) {
            this.layer = layer;
            this.tile = tile;
        }

        public void run() {
            // TODO: check to ensure load is still needed

            final java.net.URL textureURL = this.layer.getDataFileStore().findFile(tile.getPath(), false);
            if (textureURL != null && !this.layer.isTextureExpired(tile, textureURL)) {
                int aval = this.layer.loadTexture(tile, textureURL);
                if (aval == 1) {
                    layer.getLevels().unmarkResourceAbsent(tile);
                    this.layer.firePropertyChange(AVKey.LAYER, null, this);
                    return;
                } else if (aval == 0) {
                    // Assume that something's wrong with the file and delete
                    // it.
                    this.layer.getDataFileStore().removeFile(textureURL);
                    layer.getLevels().markResourceAbsent(tile);
                    String message = Logging.getMessage("generic.DeletedCorruptDataFile", textureURL);
                    Logging.logger().info(message);
                    // restart download
                } else { // aval==-1
                    layer.getLevels().markResourceAbsent(tile);
                    return; // no redownload
                }
            }

            this.layer.downloadTexture(this.tile);
        }

        /**
         * @param that the task to compare
         * @return -1 if <code>this</code> less than <code>that</code>, 1 if greater than, 0 if equal
         * @throws IllegalArgumentException if <code>that</code> is null
         */
        public int compareTo(RequestTask that) {
            if (that == null) {
                String msg = Logging.getMessage("nullValue.RequestTaskIsNull");
                Logging.logger().severe(msg);
                throw new IllegalArgumentException(msg);
            }

            return this.tile.getPriority() == that.tile.getPriority()
                ? 0
                : this.tile.getPriority() < that.tile.getPriority() ? -1 : 1;
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final RequestTask that = (RequestTask)o;

            // Don't include layer in comparison so that requests are shared
            // among layers
            return !(tile != null ? !tile.equals(that.tile) : that.tile != null);
        }

        public int hashCode() {
            return (tile != null ? tile.hashCode() : 0);
        }

        public String toString() {
            return this.tile.toString();
        }
    }

    private boolean isTextureExpired(MercatorTextureTile tile, java.net.URL textureURL) {
        if (!WWIO.isFileOutOfDate(textureURL, tile.getLevel().getExpiryTime())) {
            return false;
        }

        // The file has expired. Delete it.
        this.getDataFileStore().removeFile(textureURL);
        String message = Logging.getMessage("generic.DataFileExpired", textureURL);
        Logging.logger().fine(message);
        return true;
    }

    protected TextureData absentTextureData = null;
    protected TextureData absentTextureData2 = null;

    HashMap<URL, Boolean> hideTile = new HashMap<>();

    /**
     * try to load a texture
     *
     * @param tile
     * @param textureURL
     * @return 0 == no data avaliable. 1 == all right, its loaded. -1 == data avaliable, but its black, dont show it!
     */
    protected int loadTexture(MercatorTextureTile tile, java.net.URL textureURL) {
        TextureData textureData;

        synchronized (this.fileLock) {
            textureData = readTexture(textureURL, this.isUseMipMaps());
        }

        if (textureData == null) {
            return 0;
        }

        try {
            Boolean hide = hideTile.get(textureURL);
            if (hide != null) {
                return -1;
            } else {
                long length = new File(textureURL.toURI()).length();
                if (length <= MAX_BLACK_TILE_SIZE) {
                    hideTile.put(textureURL, true);
                    return -1;
                }

                if (length <= MAX_BLACK_TILE_SIZE_SUSPICIOUS) {
                    if (isTileBlack(textureData)) {
                        hideTile.put(textureURL, true);
                        return -1;
                    }
                }
            }
        } catch (URISyntaxException e) {
            Debug.getLog().severe(e.getMessage());
        }

        tile.setTextureData(textureData);
        if (tile.getLevelNumber() != 0 || !this.isRetainLevelZeroTiles()) {
            this.addTileToCache(tile);
        }

        return 1;
    }

    private boolean isTileBlack(TextureData textureData) {
        Buffer buffer = textureData.getBuffer();

        if (buffer instanceof ByteBuffer) {
            ByteBuffer byteBuffer = ((ByteBuffer)buffer);
            byte[] bufferArray = byteBuffer.array();
            final int length = bufferArray.length / 3;

            double sum =
                -IntStream.range(0, length)
                    .boxed()
                    .map(
                        idx ->
                            String.valueOf(
                                (int)
                                    Math.round(
                                        0.1140 * (bufferArray[idx * 3] & 0xFF)
                                            + 0.5870 * (bufferArray[idx * 3 + 1] & 0xFF)
                                            + 0.2989 * (bufferArray[idx * 3 + 2] & 0xFF))))
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .entrySet()
                    .stream()
                    .map(
                        e -> {
                            return e.getValue() * (log2(e.getValue() / (double)length)) / (double)length;
                        })
                    .mapToDouble(Double::doubleValue)
                    .sum();

            if (sum < THRESHOLD_BLACK_IMG) {
                return true;
            }
        }

        return false;
    }

    private static TextureData readTexture(java.net.URL url, boolean useMipMaps) {
        try {
            return OGLUtil.newTextureData(Configuration.getMaxCompatibleGLProfile(), url, useMipMaps);
        } catch (Exception e) {
            String msg = Logging.getMessage("layers.TextureLayer.ExceptionAttemptingToReadTextureFile", url.toString());
            Logging.logger().log(java.util.logging.Level.SEVERE, msg, e);
            return null;
        }
    }

    private void addTileToCache(MercatorTextureTile tile) {
        WorldWind.getMemoryCache(MercatorTextureTile.class.getName()).add(tile.getTileKey(), tile);
    }

    protected void downloadTexture(final MercatorTextureTile tile) {
        if (!WorldWind.getRetrievalService().isAvailable()) {
            return;
        }

        java.net.URL url;
        try {
            url = tile.getResourceURL();
            if (url == null) {
                return;
            }

            if (WorldWind.getNetworkStatus().isHostUnavailable(url)) {
                return;
            }
        } catch (java.net.MalformedURLException e) {
            Logging.logger()
                .log(
                    java.util.logging.Level.SEVERE,
                    Logging.getMessage("layers.TextureLayer.ExceptionCreatingTextureUrl", tile),
                    e);
            return;
        }

        Retriever retriever;

        if ("http".equalsIgnoreCase(url.getProtocol()) || "https".equalsIgnoreCase(url.getProtocol())) {
            retriever = new HTTPRetriever(url, new DownloadPostProcessor(tile, this));
            retriever.setValue(URLRetriever.EXTRACT_ZIP_ENTRY, "true"); // supports
            // legacy
            // layers
        } else {
            Logging.logger().severe(Logging.getMessage("layers.TextureLayer.UnknownRetrievalProtocol", url.toString()));
            return;
        }

        // Apply any overridden timeouts.
        Integer cto = getIntegerValue(this, AVKey.URL_CONNECT_TIMEOUT);
        if (cto != null && cto > 0) {
            retriever.setConnectTimeout(cto);
        }

        Integer cro = getIntegerValue(this, AVKey.URL_READ_TIMEOUT);
        if (cro != null && cro > 0) {
            retriever.setReadTimeout(cro);
        }

        Integer srl = getIntegerValue(this, AVKey.RETRIEVAL_QUEUE_STALE_REQUEST_LIMIT);
        if (srl != null && srl > 0) {
            retriever.setStaleRequestLimit(srl);
        }

        WorldWind.getRetrievalService().runRetriever(retriever, tile.getPriority());
    }

    private void saveBuffer(java.nio.ByteBuffer buffer, java.io.File outFile) throws java.io.IOException {
        synchronized (this.fileLock) // synchronized with read of file in
        {
            WWIO.saveBuffer(buffer, outFile);
        }
    }

    protected void dataDownloadSucceded(MercatorTextureTile tile, Retriever retriever) {}

    private static class DownloadPostProcessor implements RetrievalPostProcessor {
        // TODO: Rewrite this inner class, factoring out the generic parts.
        private final MercatorTextureTile tile;
        private final MBasicMercatorTiledImageLayer layer;

        public DownloadPostProcessor(MercatorTextureTile tile, MBasicMercatorTiledImageLayer layer) {
            this.tile = tile;
            this.layer = layer;
        }

        public ByteBuffer run(Retriever retriever) {
            if (retriever == null) {
                String msg = Logging.getMessage("nullValue.RetrieverIsNull");
                Logging.logger().severe(msg);
                throw new IllegalArgumentException(msg);
            }

            try {
                if (!retriever.getState().equals(Retriever.RETRIEVER_STATE_SUCCESSFUL)) {
                    return null;
                }

                URLRetriever r = (URLRetriever)retriever;
                ByteBuffer buffer = r.getBuffer();

                if (retriever instanceof HTTPRetriever) {
                    HTTPRetriever htr = (HTTPRetriever)retriever;
                    if (htr.getResponseCode() == HttpURLConnection.HTTP_NO_CONTENT) {
                        // Mark tile as missing to avoid excessive attempts
                        this.layer.getLevels().markResourceAbsent(this.tile);
                        return null;
                    } else if (htr.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        // Also mark tile as missing, but for an unknown reason.
                        this.layer.getLevels().markResourceAbsent(this.tile);
                        return null;
                    }
                }

                this.layer.dataDownloadSucceded(tile, retriever);

                final File outFile = this.layer.getDataFileStore().newFile(this.tile.getPath());
                if (outFile == null) {
                    return null;
                }

                if (outFile.exists()) {
                    return buffer;
                }

                // TODO: Better, more generic and flexible handling of
                // file-format type
                if (buffer != null) {
                    String contentType = r.getContentType();
                    if (contentType == null) {
                        // TODO: logger message
                        return null;
                    }

                    if (contentType.contains("xml") || contentType.contains("html") || contentType.contains("text")) {
                        this.layer.getLevels().markResourceAbsent(this.tile);

                        StringBuffer sb = new StringBuffer();
                        while (buffer.hasRemaining()) {
                            sb.append((char)buffer.get());
                        }
                        // TODO: parse out the message if the content is xml or
                        // html.
                        Logging.logger().severe(sb.toString());

                        return null;
                    } else if (contentType.contains("dds")) {
                        this.layer.saveBuffer(buffer, outFile);
                    } else if (contentType.contains("zip")) {
                        // Assume it's zipped DDS, which the retriever would
                        // have unzipped into the buffer.
                        this.layer.saveBuffer(buffer, outFile);
                    } else if (contentType.contains("image")) {
                        BufferedImage image = this.layer.convertBufferToImage(buffer);
                        if (image != null) {
                            image = this.layer.modifyImage(image);
                            if (this.layer.isTileValid(image)) {
                                if (!this.layer.transformAndSave(image, tile.getMercatorSector(), outFile)) {
                                    image = null;
                                }
                            } else {
                                this.layer.getLevels().markResourceAbsent(this.tile);
                                return null;
                            }
                        }

                        if (image == null) {
                            // Just save whatever it is to the cache.
                            this.layer.saveBuffer(buffer, outFile);
                        }
                    }

                    if (buffer != null) {
                        this.layer.firePropertyChange(AVKey.LAYER, null, this);
                    }

                    return buffer;
                }
            } catch (java.io.IOException e) {
                this.layer.getLevels().markResourceAbsent(this.tile);
                Logging.logger()
                    .log(
                        java.util.logging.Level.SEVERE,
                        Logging.getMessage("layers.TextureLayer.ExceptionSavingRetrievedTextureFile", tile.getPath()),
                        e);
            }

            return null;
        }
    }

    protected boolean isTileValid(BufferedImage image) {
        // override in subclass to check image tile
        // if false is returned, then tile is marked absent
        return true;
    }

    protected BufferedImage modifyImage(BufferedImage image) {
        // override in subclass to modify image tile
        return image;
    }

    private BufferedImage convertBufferToImage(ByteBuffer buffer) {
        try {
            InputStream is = new ByteArrayInputStream(buffer.array());
            return ImageIO.read(is);
        } catch (IOException e) {
            return null;
        }
    }

    private boolean transformAndSave(BufferedImage image, MercatorSector sector, File outFile) {
        try {
            image = transform(image, sector);
            String extension = outFile.getName().substring(outFile.getName().lastIndexOf('.') + 1);
            synchronized (this.fileLock) // synchronized with read of file in
            {
                return ImageIO.write(image, extension, outFile);
            }
        } catch (IOException e) {
            return false;
        }
    }

    private BufferedImage transform(BufferedImage image, MercatorSector sector) {
        int type = image.getType();
        if (type == 0) {
            type = BufferedImage.TYPE_INT_RGB;
        }

        BufferedImage trans = new BufferedImage(image.getWidth(), image.getHeight(), type);
        double miny = sector.getMinLatPercent();
        double maxy = sector.getMaxLatPercent();
        for (int y = 0; y < image.getHeight(); y++) {
            double sy = 1.0 - y / (double)(image.getHeight() - 1);
            Angle lat = Angle.fromRadians(sy * sector.getDeltaLatRadians() + sector.getMinLatitude().radians);
            double dy = 1.0 - (MercatorSector.gudermannianInverse(lat) - miny) / (maxy - miny);
            dy = Math.max(0.0, Math.min(1.0, dy));
            int iy = (int)(dy * (image.getHeight() - 1));

            for (int x = 0; x < image.getWidth(); x++) {
                trans.setRGB(x, y, image.getRGB(x, iy));
            }
        }

        return trans;
    }
}
