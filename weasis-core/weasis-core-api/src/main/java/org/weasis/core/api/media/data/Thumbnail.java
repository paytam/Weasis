/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.media.data;


import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.controlsfx.control.PopOver;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.FxUtil;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.ImageProcessor;

import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.geometry.Bounds;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TouchEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;

public class Thumbnail extends Canvas implements Thumbnailable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Thumbnail.class);

    public static final File THUMBNAIL_CACHE_DIR =
        AppProperties.buildAccessibleTempDirectory(AppProperties.FILE_CACHE_DIR.getName(), "thumb"); //$NON-NLS-1$
    public static final ExecutorService THUMB_LOADER = ThreadUtil.buildNewSingleThreadExecutor("Thumbnail Loader"); //$NON-NLS-1$

    public static final int MIN_SIZE = 48;
    public static final int DEFAULT_SIZE = 176;
    public static final int MAX_SIZE = 256;

    private static final NativeCache<Thumbnail, PlanarImage> mCache =
        new NativeCache<Thumbnail, PlanarImage>(30_000_000) {

            @Override
            protected void afterEntryRemove(Thumbnail key, PlanarImage img) {
                if (img != null) {
                    img.release();
                }
            }
        };

    protected BooleanProperty lockedProperty = new SimpleBooleanProperty(false);
    protected volatile boolean readable = true;
    protected AtomicBoolean loading = new AtomicBoolean(false);
    protected File thumbnailPath = null;

    protected MediaElement media;
    protected boolean keepMediaCache;
    protected OpManager opManager;

    public Thumbnail(int thumbnailSize) {
        super(thumbnailSize, thumbnailSize);
        pause.setOnFinished(this::pause);
    }

    public Thumbnail(final MediaElement media, int thumbnailSize, boolean keepMediaCache, OpManager opManager) {
        this(media, thumbnailSize, keepMediaCache, opManager, null);
    }

    public Thumbnail(final MediaElement media, int thumbnailSize, boolean keepMediaCache, OpManager opManager,
        BooleanProperty lockedProperty) {
        super(thumbnailSize, thumbnailSize);
        if (media == null) {
            throw new IllegalArgumentException("image cannot be null"); //$NON-NLS-1$
        }
        if (lockedProperty != null) {
            this.lockedProperty.bind(lockedProperty);
        }
        init(media, keepMediaCache, opManager);
        pause.setOnFinished(this::pause);
    }

    /**
     * @param media
     * @param keepMediaCache
     *            if true will remove the media from cache after building the thumbnail. Only when media is an image.
     */
    protected void init(MediaElement media, boolean keepMediaCache, OpManager opManager) {
        // this.setFont(FontTools.getFont10());
        buildThumbnail(media, keepMediaCache, opManager);
    }

    @Override
    public void registerListeners() {
        this.setOnDragDetected(this::handleOnDragDetected);
        this.setOnDragDone(this::handleOnDragDone);

        this.setOnTouchPressed(this::handleTouchPress);
        this.setOnTouchReleased(this::handleTouchRelease);
        
        removeMouseAndKeyListener();
    }

    public static PlanarImage createThumbnail(PlanarImage source) {
        if (source == null) {
            return null;
        }
        return ImageProcessor.buildThumbnail(source, new Dimension(Thumbnail.MAX_SIZE, Thumbnail.MAX_SIZE), true);
    }

    protected synchronized void buildThumbnail(MediaElement media, boolean keepMediaCache, OpManager opManager) {
        this.media = media;
        this.keepMediaCache = keepMediaCache;
        this.opManager = opManager;

        repaint();
    }

    public void repaint() {
        GuiExecutor.executeFX(this::draw);
    }

    private void draw() {
        double width = getWidth();
        double height = getHeight();
        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, this.getWidth(), this.getHeight());
        double x = 0;
        double y = 0;
        final PlanarImage thumbnail = Thumbnail.this.getImage(media, keepMediaCache, opManager);
        if (thumbnail == null) {
            Image icon = MimeInspector.unknownIcon;
            String type = Messages.getString("Thumbnail.unknown"); //$NON-NLS-1$
            if (media != null) {
                String mime = media.getMimeType();
                if (mime != null) {
                    if (mime.startsWith("image")) { //$NON-NLS-1$
                        type = Messages.getString("Thumbnail.img"); //$NON-NLS-1$
                        icon = MimeInspector.imageIcon;
                    } else if (mime.startsWith("video")) { //$NON-NLS-1$
                        type = Messages.getString("Thumbnail.video"); //$NON-NLS-1$
                        icon = MimeInspector.videoIcon;
                    } else if (mime.startsWith("audio")) { //$NON-NLS-1$
                        type = Messages.getString("Thumbnail.audio"); //$NON-NLS-1$
                        icon = MimeInspector.audioIcon;
                    } else if (mime.equals("sr/dicom")) { //$NON-NLS-1$
                        type = Messages.getString("Thumbnail.dicom_sr"); //$NON-NLS-1$
                        icon = MimeInspector.textIcon;
                    } else if (mime.startsWith("txt")) { //$NON-NLS-1$
                        type = Messages.getString("Thumbnail.text"); //$NON-NLS-1$
                        icon = MimeInspector.textIcon;
                    } else if (mime.endsWith("html")) { //$NON-NLS-1$
                        type = Messages.getString("Thumbnail.html"); //$NON-NLS-1$
                        icon = MimeInspector.htmlIcon;
                    } else if (mime.equals("application/pdf")) { //$NON-NLS-1$
                        type = Messages.getString("Thumbnail.pdf"); //$NON-NLS-1$
                        icon = MimeInspector.pdfIcon;
                    } else if (mime.equals("wf/dicom")) { //$NON-NLS-1$
                        type = "ECG"; //$NON-NLS-1$
                        icon = MimeInspector.ecgIcon;
                    } else if (mime.startsWith("audio") || mime.equals("au/dicom")) { //$NON-NLS-1$ //$NON-NLS-2$
                        type = Messages.getString("Thumbnail.audio"); //$NON-NLS-1$
                        icon = MimeInspector.audioIcon;
                    } else {
                        type = mime;
                    }
                }

                Font font = gc.getFont();
                Bounds fb = FxUtil.getTextBounds(type, font);
                double x1 = x + (getWidth() - icon.getWidth()) / 2;
                double y1 = y + (height - fb.getHeight() - 5.0 - icon.getHeight()) / 2;
                gc.drawImage(icon, x1, y1);
                x1 = x + (getWidth() - fb.getWidth()) / 2.0;
                y1 = y + (getHeight() - icon.getHeight()) / 2;
                gc.strokeText(type, x1, y1 + icon.getHeight() + fb.getHeight() + 5.0);
            }

        } else {
            width = thumbnail.width();
            height = thumbnail.height();
            x += (getWidth() - width) / 2;
            y += (getHeight() - height) / 2;

            WritableImage image = SwingFXUtils.toFXImage(ImageConversion.toBufferedImage(thumbnail), null);
            if (image != null) {
                gc.drawImage(image, x, y);
            }
        }
        drawOverIcon(gc, x, y, width, height);
    }

    protected void drawOverIcon(GraphicsContext gc, double x, double y, double width, double height) {

    }

    @Override
    public File getThumbnailPath() {
        return thumbnailPath;
    }

    protected synchronized PlanarImage getImage(final MediaElement media, final boolean keepMediaCache,
        final OpManager opManager) {
        PlanarImage cacheImage;
        if ((cacheImage = mCache.get(this)) == null && readable && loading.compareAndSet(false, true)) {
            try {
                Task<Boolean> thumbnailReader = new Task<Boolean>() {
                    
                    @Override
                    protected Boolean call() throws Exception {
                        loadThumbnail(media, keepMediaCache, opManager);
                        // Force UI to repaint with the image
                        repaint();
                        return Boolean.TRUE;
                    }
                };
                THUMB_LOADER.execute(thumbnailReader);
            } catch (Exception e) {
                LOGGER.error("Cannot build thumbnail!", e);//$NON-NLS-1$
                loading.set(false);
            }

        }
        return cacheImage;
    }

    private void loadThumbnail(final MediaElement media, final boolean keepMediaCache, final OpManager opManager)
        throws Exception {
        try {
            File file = thumbnailPath;
            boolean noPath = file == null || !file.canRead();
            if (noPath && media != null) {
                String path = (String) media.getTagValue(TagW.ThumbnailPath);
                if (path != null) {
                    file = new File(path);
                    if (file.canRead()) {
                        noPath = false;
                        thumbnailPath = file;
                    }
                }
            }
            if (noPath) {
                if (media instanceof ImageElement) {
                    final ImageElement image = (ImageElement) media;
                    PlanarImage imgPl = image.getImage(opManager);
                    if (imgPl != null) {
                        PlanarImage img = image.getRenderedImage(imgPl);
                        final PlanarImage thumb = createThumbnail(img);
                        if (thumb != null) {
                            try {
                                file = File.createTempFile("tumb_", ".jpg", Thumbnail.THUMBNAIL_CACHE_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                            } catch (IOException e) {
                                LOGGER.error("Cannot create file for thumbnail!", e);//$NON-NLS-1$
                            }
                        }
                        try {
                            if (thumb != null && file != null) {
                                MatOfInt map = new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 80);
                                if (ImageProcessor.writeImage(thumb.toMat(), file, map)) {
                                    /*
                                     * Write the thumbnail in temp folder, better than getting the thumbnail directly
                                     * from t.getAsBufferedImage() (it is true if the image is big and cannot handle all
                                     * the tiles in memory)
                                     */
                                    image.setTag(TagW.ThumbnailPath, file.getPath());
                                    thumbnailPath = file;
                                    return;
                                } else {
                                    // out of memory
                                }

                            }

                            if (thumb == null || thumb.width() <= 0) {
                                readable = false;
                                ImageConversion.releasePlanarImage(thumb);
                            } else {
                                mCache.put(this, thumb);
                            }
                        } finally {
                            if (!keepMediaCache) {
                                // Prevent to many files open on Linux (Ubuntu => 1024) and close image stream
                                image.removeImageFromCache();
                            }
                        }
                    } else {
                        readable = false;
                    }
                }
            } else {
                Load ref = new Load(file);
                // loading images sequentially, only one thread pool
                Future<PlanarImage> future = ImageElement.IMAGE_LOADER.submit(ref);
                PlanarImage thumb = null;
                try {
                    PlanarImage img = future.get();
                    if (img == null) {
                        thumb = null;
                    } else {
                        int width = img.width();
                        int height = img.height();
                        int thumbnailSize = (int) getHeight();
                        if (width > thumbnailSize || height > thumbnailSize) {
                            thumb =
                                ImageProcessor.buildThumbnail(img, new Dimension(thumbnailSize, thumbnailSize), true);
                        } else {
                            thumb = img;
                        }
                    }

                } catch (InterruptedException e) {
                    // Re-assert the thread's interrupted status
                    Thread.currentThread().interrupt();
                    // We don't need the result, so cancel the task too
                    future.cancel(true);
                } catch (ExecutionException e) {
                    LOGGER.error("Cannot read thumbnail pixel data!: {}", file, e);//$NON-NLS-1$
                }
                if ((thumb == null && media != null) || (thumb != null && thumb.width() <= 0)) {
                    readable = false;
                } else {
                    mCache.put(this, thumb);
                }
            }
        } finally {
            loading.set(false);
        }
    }

    protected void removeImageFromCache() {
        // Unload image from memory
        mCache.remove(this);
    }

    @Override
    public void dispose() {
        removeImageFromCache();

        if (thumbnailPath != null && thumbnailPath.getPath().startsWith(AppProperties.FILE_CACHE_DIR.getPath())) {
            FileUtil.delete(thumbnailPath);
        }

        removeMouseAndKeyListener();
    }

    @Override
    public void removeMouseAndKeyListener() {
        // MouseListener[] listener = this.getMouseListeners();
        // MouseMotionListener[] motionListeners = this.getMouseMotionListeners();
        // KeyListener[] keyListeners = this.getKeyListeners();
        // MouseWheelListener[] wheelListeners = this.getMouseWheelListeners();
        // for (int i = 0; i < listener.length; i++) {
        // this.removeMouseListener(listener[i]);
        // }
        // for (int i = 0; i < motionListeners.length; i++) {
        // this.removeMouseMotionListener(motionListeners[i]);
        // }
        // for (int i = 0; i < keyListeners.length; i++) {
        // this.removeKeyListener(keyListeners[i]);
        // }
        // for (int i = 0; i < wheelListeners.length; i++) {
        // this.removeMouseWheelListener(wheelListeners[i]);
        // }
    }

    class Load implements Callable<PlanarImage> {

        private final File path;

        public Load(File path) {
            this.path = path;
        }

        @Override
        public PlanarImage call() throws Exception {
            return ImageProcessor.readImageWithCvException(path);
        }
    }

    /*****************************************************************
     * DRAG *
     *****************************************************************/
    public void handleOnDragDetected(MouseEvent event) {
        /* drag was detected, start drag-and-drop gesture */
        LOGGER.trace("onDragDetected");
        if (!lockedProperty.getValue()) {
            /* allow any transfer mode */
            Thumbnail thumb = (Thumbnail) event.getSource();
            if (thumb != null) {
                Dragboard db = thumb.startDragAndDrop(TransferMode.COPY);

                SnapshotParameters sp = new SnapshotParameters();
                db.setDragView((thumb).snapshot(sp, null));

                /* put a string on dragboard */
                ClipboardContent content = new ClipboardContent();
                content.putString(thumb.toString());
                db.setContent(content);
            }
        }
        event.consume();
    }

    public void handleOnDragDone(DragEvent event) {
        /* the drag-and-drop gesture ended */
        LOGGER.trace("onDragDone");
        /* if the data was successfully moved, clear it */
        if (event.getTransferMode() == TransferMode.COPY) {
            // vbox.getChildren().remove((Node)child);
            // im1.setImage(null);
            // ((ImageView) child).setImage(event.getDragboard().getImage());
        }
        event.consume();
    }

    public void setLockedProperty(BooleanProperty lockedProperty) {
        this.lockedProperty.bind(lockedProperty);
    }

    /*****************************************************************
     * TOUCH *
     *****************************************************************/
    private static ArrayList<String> doubleTab1Finger =
        new ArrayList<>(Arrays.asList("press", "release", "press", "release"));

    private ArrayList<String> touchevent = new ArrayList<>();
    private PauseTransition pause = new PauseTransition(Duration.millis(130));

    PopOver popOver = new PopOver();
    // HiddenSidesPane pane = new HiddenSidesPane(this, new Text("bonjour vous"),new Text("bonjour vous"),new
    // Text("bonjour vous"),new Text("bonjour vous"));
    // InfoOverlay over = new InfoOverlay(this, "hello coucou bonour");

    private void handleTouchPress(TouchEvent event) {
        if (!lockedProperty.getValue()) {
            pause.stop();
            touchevent.add("press");
            pause.playFromStart();
        }
        event.consume();
    }

    private void handleTouchRelease(TouchEvent event) {
        if (!lockedProperty.getValue()) {
            pause.stop();
            touchevent.add("release");
            pause.playFromStart();
        }
        event.consume();
    }

    private void pause(ActionEvent e) {
        if (touchevent.equals(doubleTab1Finger)) {
            popOver.setContentNode(new Text("place your text \nhere"));
            popOver.setAnimated(true);
            popOver.setAutoFix(true);
            popOver.setDetachable(false);

            popOver.show(this);
        }
        touchevent.clear();
    }

}
