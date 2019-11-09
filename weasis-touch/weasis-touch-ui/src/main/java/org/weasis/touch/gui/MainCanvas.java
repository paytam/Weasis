/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Gérôme Pasquier - initial API and implementation
 *     Nicolas Roduit - implementation
 *******************************************************************************/
package org.weasis.touch.gui;

import java.awt.image.DataBuffer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.prefs.Preferences;

import org.controlsfx.control.Notifications;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.image.AffineTransformOp;
import org.weasis.core.api.image.FilterOp;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.ImageOpNode.Param;
import org.weasis.core.api.image.LutShape;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.PseudoColorOp;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.op.ByteLutCollection;
import org.weasis.core.api.image.util.KernelData;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.util.LangUtil;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.codec.display.OverlayOp;
import org.weasis.dicom.codec.display.PresetWindowLevel;
import org.weasis.dicom.codec.display.ShutterOp;
import org.weasis.dicom.codec.display.WindowAndPresetsOp;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.touch.Messages;
import org.weasis.touch.WeasisPreferences;

import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.util.Duration;

public class MainCanvas extends Canvas {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainCanvas.class);

    private static final Double WINDOW_DEFAULT = 700.0;
    private static final Double LEVEL_DEFAULT = 300.0;
    private static final Double MINIMUM_ZOOM_FACTOR = 0.5001;
    private static final Double MAXIMUM_ZOOM_FACTOR = 20.0;
    public static final String ZOOM_FACTOR_SCREEN = "zoomFactorScreen";
    public static final String TRANSLATE_Y = "translateY";
    public static final String TRANSLATE_X = "translateX";
    public static final String PRESET_LIST_WINDOW_LEVEL = "PresetListWindowLevel";
    public static final String IS_PRESET_WINDOW_LEVEL = "isPresetWindowLevel";
    public static final String INFO_MODE = "infoMode";

    private Preferences prefs;

    public MainCanvasTouchController canvasTouchController;
    public MainCanvasMouseController canvasMouseController;
    public MainCanvasScrollController canvasScrollController;
    private MainCanvasKeyBoardController canvasKeyBoardController;
    public ScrollController scrollController;

    protected final HashMap<String, Object> actionsInView = new HashMap<>();
    private final SimpleOpManager manager;
    private volatile WritableImage image;
    protected MediaSeries<DicomImageElement> series = null;
    private DicomImageElement sourceImage;
    protected int tileOffset;
    protected InfoLayer infoLayer;
    public Mesures mesures;
    public BooleanProperty showMeasureProperty = new SimpleBooleanProperty(true);
    public IntegerProperty measureInProgress = new SimpleIntegerProperty(0);
    public BooleanProperty editModeProperty = new SimpleBooleanProperty(false);
    public IntegerProperty indexNewWindowLevelKeyboardProperty = new SimpleIntegerProperty();
    private PauseTransition waitBeforeNextNotification = new PauseTransition(Duration.seconds(10));
    private volatile boolean flagNotification = true;

    public MainCanvas(MainViewController mainViewController) {
        prefs = Preferences.userRoot().node(SettingsController.class.getName());
        this.tileOffset = 0;
        this.manager = new SimpleOpManager();
        manager.addImageOperationAction(new WindowAndPresetsOp());
        manager.addImageOperationAction(new FilterOp());
        manager.addImageOperationAction(new PseudoColorOp());
        manager.addImageOperationAction(new ShutterOp());
        manager.addImageOperationAction(new OverlayOp());
        manager.addImageOperationAction(new AffineTransformOp());
        initOpManager();

        canvasTouchController = new MainCanvasTouchController(this);
        canvasMouseController = new MainCanvasMouseController(this);
        canvasScrollController = new MainCanvasScrollController(this);
        canvasKeyBoardController = new MainCanvasKeyBoardController(this);
        this.canvasMouseController.measureInProgress.bindBidirectional(measureInProgress);
        this.canvasTouchController.measureInProgress.bindBidirectional(measureInProgress);
        this.indexNewWindowLevelKeyboardProperty
            .bind(this.canvasKeyBoardController.indexNewWindowLevelKeyboardProperty);

        this.widthProperty().addListener((ChangeListener<Number>) (observable, oldValue, newValue) -> {
            if (this.image != null) {
                double cH = this.getHeight();
                double iW = image.getWidth();
                double iH = image.getHeight();

                Point2D point = canvasPointToImagePoint((double) oldValue / 2, cH / 2);
                setActionsInView(ZOOM_FACTOR_SCREEN, Math.min((double) newValue / iW, cH / iH));
                updateTranslate(point, (double) newValue / 2, cH / 2);
                zoomEnd();
                draw();
            }
        });
        this.heightProperty().addListener((ChangeListener<Number>) (observable, oldValue, newValue) -> {
            if (this.image != null) {
                double cW = this.getWidth();
                double iW = image.getWidth();
                double iH = image.getHeight();

                Point2D point = canvasPointToImagePoint(cW / 2, (double) oldValue / 2);
                setActionsInView(ZOOM_FACTOR_SCREEN, Math.min(cW / iW, (double) newValue / iH));
                updateTranslate(point, cW / 2, (double) newValue / 2);
                zoomEnd();
                draw();
            }
        });

        this.showMeasureProperty.addListener(e -> this.draw());

        this.infoLayer = new InfoLayer(this);
        this.setInfoMode(2);
        waitBeforeNextNotification.setOnFinished(e -> flagNotification = true);
    }

    public void initOpManager() {
        this.setActionsInView(ActionW.ZOOM.cmd(), 1.0);
        this.setActionsInView(TRANSLATE_X, 0);
        this.setActionsInView(TRANSLATE_Y, 0);
        this.setActionsInView(ActionW.SCROLL_SERIES.cmd(), 0);
        this.setActionsInView(ActionW.DEFAULT_PRESET.cmd(), null);
        this.setActionsInView(IS_PRESET_WINDOW_LEVEL, true);
        this.setActionsInView(ActionW.LUT_SHAPE.cmd(), null);
        this.setActionsInView(ActionW.PRESET.cmd(), null);
        this.setActionsInView(ActionW.WINDOW.cmd(), null);
        this.setActionsInView(ActionW.LEVEL.cmd(), null);
        this.setActionsInView(ActionW.LEVEL_MIN.cmd(), null);
        this.setActionsInView(ActionW.LEVEL_MAX.cmd(), null);
        this.setActionsInView(ActionW.INVERT_LUT.cmd(), false);
        this.setActionsInView(ActionW.LUT.cmd(), ByteLutCollection.Lut.GRAY.getByteLut());
        if (scrollController != null) {
            scrollController.setValue(0);
        }

        manager.setParamValue(AffineTransformOp.OP_NAME, AffineTransformOp.P_AFFINE_MATRIX, null);
        manager.setParamValue(AffineTransformOp.OP_NAME, AffineTransformOp.P_INTERPOLATION, Imgproc.INTER_LINEAR);

        manager.setParamValue(FilterOp.OP_NAME, FilterOp.P_KERNEL_DATA, KernelData.NONE);

        manager.setParamValue(PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT, ByteLutCollection.Lut.GRAY.getByteLut());
        manager.setParamValue(PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT_INVERSE, false);

        manager.setParamValue(ShutterOp.OP_NAME, ShutterOp.P_SHOW, true);
        manager.setParamValue(OverlayOp.OP_NAME, OverlayOp.P_SHOW, true);

        manager.setParamValue(WindowOp.OP_NAME, WindowOp.P_APPLY_WL_COLOR, true);
        manager.setParamValue(WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd(), true);
        manager.setParamValue(WindowOp.OP_NAME, ActionW.DEFAULT_PRESET.cmd(), true);
        manager.setParamValue(WindowOp.OP_NAME, ActionW.PRESET.cmd(), null);

        manager.setParamValue(PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT_INVERSE, false);
        manager.setParamValue(PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT, null);
    }

    public void setScrollBar(ScrollController sc) {
        this.scrollController = sc;
    }

    public void setSeries(MediaSeries<DicomImageElement> newSeries, DicomImageElement selectedMedia) {
        MediaSeries<? extends ImageElement> oldsequence = this.series;
        this.series = newSeries;

        if (oldsequence == null && newSeries == null) {
            return;
        }
        if (oldsequence != null && oldsequence.equals(newSeries) && image != null) {
            return;
        }

        mesures = new Mesures(this);
        mesures.editModeProperty.bindBidirectional(editModeProperty);
        editModeProperty.set(false);
        measureInProgress.set(0);

        closingSeries(oldsequence);

        initOpManager();
        try {
            if (newSeries == null) {
                setImage(null);
            } else {
                DicomImageElement media = selectedMedia;
                if (selectedMedia == null) {
                    media = newSeries.getMedia(tileOffset < 0 ? 0 : tileOffset,
                        (Filter<DicomImageElement>) actionsInView.get(ActionW.FILTERED_SERIES.cmd()),
                        getCurrentSortComparator());
                }
                manager.handleImageOpEvent(new ImageOpEvent(ImageOpEvent.OpEvent.SeriesChange, series, media, null));
                setImage(media);

                if (scrollController != null) {
                    Filter<DicomImageElement> filter =
                        (Filter<DicomImageElement>) actionsInView.get(ActionW.FILTERED_SERIES.cmd());
                    scrollController.setMaxValue(this.series.size(filter) - 1);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Unexpected error:", e); //$NON-NLS-1$
            image = null;
        } finally {
            // TODO update image state to controller ?
        }

        // Set the sequence to the state OPEN
        if (newSeries != null) {
            newSeries.setOpen(true);
        }
    }

    protected void closingSeries(MediaSeries<? extends ImageElement> mediaSeries) {
        if (mediaSeries == null) {
            return;
        }
        boolean open = false;
        mediaSeries.setOpen(open);
        // TODO setSelected and setFocused must be global to all view as open
        mediaSeries.setSelected(false, null);
        mediaSeries.setFocused(false);
    }

    public Comparator<DicomImageElement> getCurrentSortComparator() {
        SeriesComparator<DicomImageElement> sort =
            (SeriesComparator<DicomImageElement>) actionsInView.get(ActionW.SORTSTACK.cmd());
        Boolean reverse = (Boolean) actionsInView.get(ActionW.INVERSESTACK.cmd());
        return (reverse != null && reverse) ? sort.getReversOrderComparator() : sort;
    }

    public int getFrameIndex() {
        if (series instanceof Series) {
            return ((Series<DicomImageElement>) series).getImageIndex(sourceImage,
                (Filter<DicomImageElement>) actionsInView.get(ActionW.FILTERED_SERIES.cmd()),
                getCurrentSortComparator());
        }
        return -1;
    }

    public void setActionsInView(String action, Object value) {
        setActionsInView(action, value, false);
    }

    public void setActionsInView(String action, Object value, Boolean repaint) {
        if (action != null) {
            actionsInView.put(action, value);
            if (repaint) {
                draw();
            }
        }
    }

    public Boolean isImageNull() {
        return image == null;
    }

    private void propagatePreset(DicomImageElement img) {
        ImageOpNode node = manager.getNode(WindowOp.OP_NAME);
        if (node != null && (Boolean) this.getActionValue(IS_PRESET_WINDOW_LEVEL)) {
            // if (node != null && (PresetWindowLevel)this.getActionValue(ActionW.PRESET.cmd()) != null) { // fonctionne
            // pas au 1er passage
            boolean isDefaultPresetSelected =
                LangUtil.getNULLtoTrue((Boolean) getActionValue(ActionW.DEFAULT_PRESET.cmd()));
            PresetWindowLevel oldPreset = (PresetWindowLevel) getActionValue(ActionW.PRESET.cmd());
            PresetWindowLevel newPreset = null;
            boolean pixelPadding = LangUtil
                .getNULLtoTrue((Boolean) manager.getParamValue(WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd()));

            List<PresetWindowLevel> newPresetList = img.getPresetList(pixelPadding);
            this.setActionsInView(PRESET_LIST_WINDOW_LEVEL, newPresetList);

            if (oldPreset != null) {
                if (isDefaultPresetSelected) {
                    newPreset = img.getDefaultPreset(pixelPadding);
                } else {
                    for (PresetWindowLevel preset : newPresetList) {
                        if (preset.getName().equals(oldPreset.getName())) {
                            newPreset = preset;
                            break;
                        }
                    }
                    // set default preset when the old preset is not available any more
                    if (newPreset == null) {
                        newPreset = img.getDefaultPreset(pixelPadding);
                        isDefaultPresetSelected = true;
                    }
                }
            }
            if (newPreset == null) {
                newPreset = newPresetList.get(0);
            }
            if (newPreset != null) {
                Double windowValue = newPreset.getWindow();
                Double levelValue = newPreset.getLevel();
                LutShape lutShapeItem = newPreset.getLutShape();

                Double levelMin = (Double) getActionValue(ActionW.LEVEL_MIN.cmd());
                Double levelMax = (Double) getActionValue(ActionW.LEVEL_MAX.cmd());

                PresentationStateReader prReader =
                    (PresentationStateReader) getActionValue(PresentationStateReader.TAG_PR_READER);
                if (levelMin == null || levelMax == null) {
                    levelMin = Math.min(levelValue - windowValue / 2.0, img.getMinValue(prReader, pixelPadding));
                    levelMax = Math.max(levelValue + windowValue / 2.0, img.getMaxValue(prReader, pixelPadding));
                } else {
                    levelMin = Math.min(levelMin, img.getMinValue(prReader, pixelPadding));
                    levelMax = Math.max(levelMax, img.getMaxValue(prReader, pixelPadding));
                }

                node.setParam(ActionW.PRESET.cmd(), newPreset);
                node.setParam(ActionW.DEFAULT_PRESET.cmd(), isDefaultPresetSelected);
                node.setParam(ActionW.WINDOW.cmd(), windowValue);
                node.setParam(ActionW.LEVEL.cmd(), levelValue);
                node.setParam(ActionW.LEVEL_MIN.cmd(), levelMin);
                node.setParam(ActionW.LEVEL_MAX.cmd(), levelMax);
                node.setParam(ActionW.LUT_SHAPE.cmd(), lutShapeItem);

                setActionsInView(ActionW.PRESET.cmd(), newPreset);
                setActionsInView(ActionW.DEFAULT_PRESET.cmd(), isDefaultPresetSelected);
                setActionsInView(ActionW.WINDOW.cmd(), windowValue);
                setActionsInView(ActionW.LEVEL.cmd(), levelValue);
                setActionsInView(ActionW.LEVEL_MIN.cmd(), levelMin);
                setActionsInView(ActionW.LEVEL_MAX.cmd(), levelMax);
                setActionsInView(ActionW.LUT_SHAPE.cmd(), lutShapeItem);
                setActionsInView(IS_PRESET_WINDOW_LEVEL, true);
            }
        }
    }

    public void setImage(DicomImageElement img) {
        if (img != null && img != sourceImage) {
            sourceImage = img;
            manager.handleImageOpEvent(new ImageOpEvent(ImageOpEvent.OpEvent.ResetDisplay, null, img, null));
            manager.clearNodeIOCache();
            manager.setFirstNode(img.getImage(null));
            propagatePreset(img);
            image = SwingFXUtils.toFXImage(ImageConversion.toBufferedImage(process()), null);
            draw();
        }
    }

    public PlanarImage process() {
        PlanarImage source = manager.getFirstNodeInputImage();
        if (source != null) {
            List<ImageOpNode> operations = manager.getOperations();
            for (int i = 0; i < operations.size(); i++) {
                ImageOpNode op = operations.get(i);
                try {
                    if (i > 0) {
                        op.setParam(Param.INPUT_IMG, operations.get(i - 1).getParam(Param.OUTPUT_IMG));
                    }
                    if (op.isEnabled()) {
                        op.process();
                    } else {
                        // Skip this operation
                        op.setParam(Param.OUTPUT_IMG, op.getParam(Param.INPUT_IMG));
                    }
                } catch (Exception e) {
                    LOGGER.error("Error in {}", op.getName(), e);
                    op.setParam(Param.OUTPUT_IMG, op.getParam(Param.INPUT_IMG));
                }
            }
        } else {
            manager.clearNodeIOCache();
        }
        return manager.getLastNodeOutputImage();
    }

    /*****************************************************************
     * Display *
     *****************************************************************/
    public void drawCursor(double x, double y) {
        this.draw();
        GraphicsContext gc = getGraphicsContext2D();
        gc.setStroke(Color.rgb(0xb3, 0x47, 0x11));
        gc.strokeLine(x - 5, y - 40, x, y);
        gc.strokeOval(x - 10, y - 10, 20, 20);
    }

    public void draw() {
        if (image == null) {
            return;
        }

        double cW = this.getWidth();
        double cH = this.getHeight();
        double iW = image.getWidth();
        double iH = image.getHeight();

        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, cW, cH);

        setActionsInView(ZOOM_FACTOR_SCREEN, Math.min(cW / iW, cH / iH));

        double totZoomFactor =
            (Double) this.getActionValue(ActionW.ZOOM.cmd()) * (double) actionsInView.get(ZOOM_FACTOR_SCREEN);

        if ((Double) this.getActionValue(ActionW.ZOOM.cmd()) == 1) {
            this.setActionsInView(TRANSLATE_X, (cW - iW * (double) actionsInView.get(ZOOM_FACTOR_SCREEN)) / 2);
            this.setActionsInView(TRANSLATE_Y, (cH - iH * (double) actionsInView.get(ZOOM_FACTOR_SCREEN)) / 2);
        } else {
            this.setActionsInView(TRANSLATE_X, Math.min(cW / 2, (double) this.actionsInView.get(TRANSLATE_X)));
            this.setActionsInView(TRANSLATE_Y, Math.min(cH / 2, (double) this.actionsInView.get(TRANSLATE_Y)));
            this.setActionsInView(TRANSLATE_X,
                Math.max(-iW * totZoomFactor + (cW / 2), (double) this.actionsInView.get(TRANSLATE_X)));
            this.setActionsInView(TRANSLATE_Y,
                Math.max(-iH * totZoomFactor + (cH / 2), (double) this.actionsInView.get(TRANSLATE_Y)));
        }

        gc.drawImage(image, (double) this.actionsInView.get(TRANSLATE_X), (double) this.actionsInView.get(TRANSLATE_Y),
            iW * totZoomFactor, iH * totZoomFactor);

        // center of canvas
//        if ((int) this.getActionValue(INFO_MODE) != 1) {
//            gc.setFill(Color.rgb(0xb3, 0x47, 0x11));
//            gc.setStroke(Color.rgb(0xb3, 0x47, 0x11));
//            gc.strokeOval(cW / 2, cH / 2, 5, 5);
//        }

        // text
        infoLayer.draw(gc, sourceImage);

        if (showMeasureProperty.get()) {
            mesures.draw();
        }
    }

    public void setInfoMode(Integer mode) {
        this.setActionsInView(INFO_MODE, mode);
        switch (mode) {
            case 0:
                infoLayer.displayPreferences.put(LayerAnnotation.MIN_ANNOTATIONS, true);
                infoLayer.visible = true;
                break;
            case 1:
                infoLayer.displayPreferences.put(LayerAnnotation.MIN_ANNOTATIONS, false);
                infoLayer.visible = false;
                break;
            case 2:
                infoLayer.displayPreferences.put(LayerAnnotation.MIN_ANNOTATIONS, false);
                infoLayer.visible = true;
                break;
        }
        draw();
    }

    /*****************************************************************
     * Zoom / translation *
     *****************************************************************/
    public Point2D canvasPointToImagePoint(double xCanvas, double yCanvas) {
        double x = (xCanvas - (double) this.actionsInView.get(TRANSLATE_X))
            / ((double) actionsInView.get(ZOOM_FACTOR_SCREEN) * (Double) this.getActionValue(ActionW.ZOOM.cmd()));
        double y = (yCanvas - (double) this.actionsInView.get(TRANSLATE_Y))
            / ((double) actionsInView.get(ZOOM_FACTOR_SCREEN) * (Double) this.getActionValue(ActionW.ZOOM.cmd()));

        return new Point2D(x, y);
    }

    public Point2D canvasPointToImagePoint(Point2D canvasPoint) {
        return canvasPointToImagePoint(canvasPoint.getX(), canvasPoint.getY());
    }

    public Point2D imagePointToCanvasPoints(double xImage, double yImage) {
        double x =
            xImage * (double) actionsInView.get(ZOOM_FACTOR_SCREEN) * (Double) this.getActionValue(ActionW.ZOOM.cmd())
                + (double) this.actionsInView.get(TRANSLATE_X);
        double y =
            yImage * (double) actionsInView.get(ZOOM_FACTOR_SCREEN) * (Double) this.getActionValue(ActionW.ZOOM.cmd())
                + (double) this.actionsInView.get(TRANSLATE_Y);

        return new Point2D(x, y);
    }

    public Point2D imagePointToCanvasPoints(Point2D imagePoint) {
        return imagePointToCanvasPoints(imagePoint.getX(), imagePoint.getY());
    }

    private void updateTranslate(Point2D posInImage, double xCanvas, double yCanvas) {
        if (image != null) {
            this.setActionsInView(TRANSLATE_X, xCanvas - (posInImage.getX()
                * (double) actionsInView.get(ZOOM_FACTOR_SCREEN) * (Double) this.getActionValue(ActionW.ZOOM.cmd())));
            this.setActionsInView(TRANSLATE_Y, yCanvas - (posInImage.getY()
                * (double) actionsInView.get(ZOOM_FACTOR_SCREEN) * (Double) this.getActionValue(ActionW.ZOOM.cmd())));
        }
    }

    public void zoom(double zoomFactor, double xCanvas, double yCanvas) {
        if (image != null) {
            Point2D point = canvasPointToImagePoint(xCanvas, yCanvas);

            this.setActionsInView(ActionW.ZOOM.cmd(), zoomFactor * (Double) this.getActionValue(ActionW.ZOOM.cmd()));
            updateTranslate(point, xCanvas, yCanvas);
            draw();
        }
    }

    public void zoomEnd() {
        if (image != null) {
            if ((Double) this.getActionValue(ActionW.ZOOM.cmd()) < MINIMUM_ZOOM_FACTOR) {
                Point2D point = canvasPointToImagePoint(this.getWidth() / 2, this.getHeight() / 2);
                this.setActionsInView(ActionW.ZOOM.cmd(), MINIMUM_ZOOM_FACTOR);
                updateTranslate(point, this.getWidth() / 2, this.getHeight() / 2);
                Notifications notification = buildNofications();
                notification.title(Messages.getString("WeasisTouchNotification.zoomMinTitle"));
                notification.text(Messages.getString("WeasisTouchNotification.zoomMinText"));
                if (prefs.getBoolean(WeasisPreferences.SHOW_NOTIFICATIONS.name(),
                    (boolean) WeasisPreferences.SHOW_NOTIFICATIONS.defaultValue())) {
                    showNotification(notification);
                }
            } else if ((Double) this.getActionValue(ActionW.ZOOM.cmd())
                * (double) actionsInView.get(ZOOM_FACTOR_SCREEN) > MAXIMUM_ZOOM_FACTOR) {
                Point2D point = canvasPointToImagePoint(this.getWidth() / 2, this.getHeight() / 2);
                this.setActionsInView(ActionW.ZOOM.cmd(),
                    MAXIMUM_ZOOM_FACTOR / (double) actionsInView.get(ZOOM_FACTOR_SCREEN));
                updateTranslate(point, this.getWidth() / 2, this.getHeight() / 2);
                Notifications notification = buildNofications();
                notification.title(Messages.getString("WeasisTouchNotification.zoomMaxTitle"));
                notification.text(Messages.getString("WeasisTouchNotification.zoomMaxText"));
                if (prefs.getBoolean(WeasisPreferences.SHOW_NOTIFICATIONS.name(),
                    (boolean) WeasisPreferences.SHOW_NOTIFICATIONS.defaultValue())) {
                    showNotification(notification);
                }
            }
            draw();
        }
    }

    private Notifications buildNofications() {
        return Notifications.create().graphic(null).hideAfter(Duration.seconds(2)).position(Pos.TOP_CENTER)
            .hideCloseButton().owner(this).darkStyle();
    }

    private void showNotification(Notifications notification) {
        if (flagNotification) {
            notification.show();
            waitBeforeNextNotification.playFromStart();
            flagNotification = false;
        }
    }

    public void translate(double x, double y) {
        if (image != null) {
            this.setActionsInView(TRANSLATE_X, (double) this.actionsInView.get(TRANSLATE_X) + x);
            this.setActionsInView(TRANSLATE_Y, (double) this.actionsInView.get(TRANSLATE_Y) + y);
            if ((Double) this.getActionValue(ActionW.ZOOM.cmd()) == 1) {
                this.setActionsInView(ActionW.ZOOM.cmd(), 1.00000001);
            }

            draw();
        }
    }

    /*****************************************************************
     * scroll / reset *
     *****************************************************************/
    public void scroll(int index) {
        if (image != null) {
            Filter<DicomImageElement> filter =
                (Filter<DicomImageElement>) actionsInView.get(ActionW.FILTERED_SERIES.cmd());
            if (index != (int) this.actionsInView.get(ActionW.SCROLL_SERIES.cmd()) && index >= 0
                && index < series.size(filter)) {
                DicomImageElement imgElement = series.getMedia(index + tileOffset, filter, getCurrentSortComparator());
                if (imgElement != null) {
                    this.setActionsInView(ActionW.SCROLL_SERIES.cmd(), index);
                    setImage(imgElement);

                    this.applyWindownLevel((double) actionsInView.get(ActionW.WINDOW.cmd()),
                        (double) actionsInView.get(ActionW.LEVEL.cmd()));

                    draw();
                }
            }
        }
    }

    public void reset() {
        if (image != null) {
            this.setInvertLUT(false);
            manager.setParamValue(PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT, ByteLutCollection.Lut.GRAY.getByteLut());
            this.setActionsInView(ActionW.LUT.cmd(), ByteLutCollection.Lut.GRAY.getByteLut());
            setPresetWindowLevel(((List<PresetWindowLevel>) getActionValue(PRESET_LIST_WINDOW_LEVEL)).get(0).getName());
            this.setActionsInView(ActionW.ZOOM.cmd(), 1.0);
            draw();
        }
    }

    /*****************************************************************
     * LUT (preset / invert) *
     *****************************************************************/
    public void setLut(ByteLut lut) {
        if (image != null) {
            manager.setParamValue(PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT, lut);
            this.setActionsInView(ActionW.LUT.cmd(), lut);
            image = SwingFXUtils.toFXImage(ImageConversion.toBufferedImage(process()), null);
            draw();
        }
    }

    public void setTempLut(ByteLut lut) {
        if (image != null) {
            manager.setParamValue(PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT, lut);
            image = SwingFXUtils.toFXImage(ImageConversion.toBufferedImage(process()), null);
            draw();
        }
    }

    public void setInvertLUT(Boolean invert) {
        if (image != null) {
            manager.setParamValue(PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT, this.getActionValue(ActionW.LUT.cmd()));
            manager.setParamValue(PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT_INVERSE, invert);
            this.setActionsInView(ActionW.INVERT_LUT.cmd(), invert);
            image = SwingFXUtils.toFXImage(ImageConversion.toBufferedImage(process()), null);
            draw();
        }
    }

    /*****************************************************************
     * Window / Level - manual and preset *
     *****************************************************************/
    public void contraste(double deltaWindow, double deltaLevel) {
        if (image != null) {
            ImageOpNode node = manager.getNode(WindowOp.OP_NAME);
            if (node != null) {
                int imageDataType = ImageConversion.convertToDataType(sourceImage.getImage().type());
                Double windowValue = (Double) node.getParam(ActionW.WINDOW.cmd());
                Double levelValue = (Double) node.getParam(ActionW.LEVEL.cmd());
                boolean pixelPadding = LangUtil.getNULLtoTrue((Boolean) node.getParam(ActionW.IMAGE_PIX_PADDING.cmd()));
                PresentationStateReader prReader =
                    (PresentationStateReader) getActionValue(PresentationStateReader.TAG_PR_READER);

                if (windowValue == null) {
                    windowValue = WINDOW_DEFAULT;
                }
                if (levelValue == null) {
                    levelValue = LEVEL_DEFAULT;
                }
                windowValue += deltaWindow;
                levelValue += deltaLevel;

                Double levelMin = (Double) node.getParam(ActionW.LEVEL_MIN.cmd());
                Double levelMax = (Double) node.getParam(ActionW.LEVEL_MAX.cmd());
                if (levelMin == null || levelMax == null) {
                    levelMin =
                        Math.min(levelValue - windowValue / 2.0, sourceImage.getMinValue(prReader, pixelPadding));
                    levelMax =
                        Math.max(levelValue + windowValue / 2.0, sourceImage.getMaxValue(prReader, pixelPadding));
                } else {
                    levelMin = Math.min(levelMin, sourceImage.getMinValue(prReader, pixelPadding));
                    levelMax = Math.max(levelMax, sourceImage.getMaxValue(prReader, pixelPadding));
                }

                double minWindow = imageDataType >= DataBuffer.TYPE_FLOAT ? 0.00001 : 1.0;
                double maxWindow = Math.max(minWindow, levelMax - levelMin);

                windowValue = windowValue < minWindow ? minWindow : windowValue > maxWindow ? maxWindow : windowValue;
                node.setParam(ActionW.WINDOW.cmd(), windowValue);
                this.setActionsInView(ActionW.WINDOW.cmd(), windowValue);

                levelValue = levelValue < levelMin ? levelMin : levelValue > levelMax ? levelMax : levelValue;

                node.setParam(ActionW.LEVEL.cmd(), levelValue);
                node.setParam(ActionW.LEVEL_MIN.cmd(), levelMin);
                node.setParam(ActionW.LEVEL_MAX.cmd(), levelMax);

                this.setActionsInView(ActionW.LEVEL.cmd(), levelValue);
                this.setActionsInView(ActionW.LEVEL_MIN.cmd(), levelMin);
                this.setActionsInView(ActionW.LEVEL_MAX.cmd(), levelMax);
                this.setActionsInView(ActionW.PRESET.cmd(), null);
                this.setActionsInView(ActionW.DEFAULT_PRESET.cmd(), false);
                this.setActionsInView(IS_PRESET_WINDOW_LEVEL, false);

                image = SwingFXUtils.toFXImage(ImageConversion.toBufferedImage(process()), null);
                draw();
            }
        }
    }

    public void setWindowLevel(Double window, Double level) {
        if (image != null) {
            this.applyWindownLevel(window, level);
        }
    }

    private void applyWindownLevel(double window, double level) {
        boolean update = false;

        if (manager.setParamValue(WindowOp.OP_NAME, ActionW.WINDOW.cmd(), window)) {
            update = true;
            this.setActionsInView(ActionW.WINDOW.cmd(), manager.getParamValue(WindowOp.OP_NAME, ActionW.WINDOW.cmd()));
        }
        if (manager.setParamValue(WindowOp.OP_NAME, ActionW.LEVEL.cmd(), level)) {
            update = true;
            this.setActionsInView(ActionW.LEVEL.cmd(), manager.getParamValue(WindowOp.OP_NAME, ActionW.LEVEL.cmd()));
        }
        if (update) {
            image = SwingFXUtils.toFXImage(ImageConversion.toBufferedImage(process()), null);
            draw();
        }
    }

    public void setTemporairePresetWindowLevel(String temporairePresetWindowLevelName) {
        setPresetWindowLevel(temporairePresetWindowLevelName, true);
    }

    public void setPresetWindowLevel(String presetWindowLevelName) {
        setPresetWindowLevel(presetWindowLevelName, false);
    }

    private void setPresetWindowLevel(String presetWindowLevelName, Boolean temporaire) {
        if (image != null) {
            PresetWindowLevel newPreset = null;

            for (PresetWindowLevel presetWindowLevel : (List<PresetWindowLevel>) this
                .getActionValue(PRESET_LIST_WINDOW_LEVEL)) {
                if (presetWindowLevelName.compareTo(presetWindowLevel.getName()) == 0) {
                    newPreset = presetWindowLevel;
                }
            }

            if (newPreset != null) {
                ImageOpNode node = manager.getNode(WindowOp.OP_NAME);
                Filter<DicomImageElement> filter =
                    (Filter<DicomImageElement>) actionsInView.get(ActionW.FILTERED_SERIES.cmd());
                DicomImageElement imgElement = series.getMedia(
                    (int) getActionValue(ActionW.SCROLL_SERIES.cmd()) + tileOffset, filter, getCurrentSortComparator());
                boolean pixelPadding = LangUtil
                    .getNULLtoTrue((Boolean) manager.getParamValue(WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd()));

                Double windowValue = newPreset.getWindow();
                Double levelValue = newPreset.getLevel();
                LutShape lutShapeItem = newPreset.getLutShape();

                Double levelMin = (Double) getActionValue(ActionW.LEVEL_MIN.cmd());
                Double levelMax = (Double) getActionValue(ActionW.LEVEL_MAX.cmd());

                PresentationStateReader prReader =
                    (PresentationStateReader) getActionValue(PresentationStateReader.TAG_PR_READER);
                if (levelMin == null || levelMax == null) {
                    levelMin = Math.min(levelValue - windowValue / 2.0, imgElement.getMinValue(prReader, pixelPadding));
                    levelMax = Math.max(levelValue + windowValue / 2.0, imgElement.getMaxValue(prReader, pixelPadding));
                } else {
                    levelMin = Math.min(levelMin, imgElement.getMinValue(prReader, pixelPadding));
                    levelMax = Math.max(levelMax, imgElement.getMaxValue(prReader, pixelPadding));
                }

                node.setParam(ActionW.PRESET.cmd(), newPreset);
                node.setParam(ActionW.DEFAULT_PRESET.cmd(), false);
                node.setParam(ActionW.WINDOW.cmd(), windowValue);
                node.setParam(ActionW.LEVEL.cmd(), levelValue);
                node.setParam(ActionW.LEVEL_MIN.cmd(), levelMin);
                node.setParam(ActionW.LEVEL_MAX.cmd(), levelMax);
                node.setParam(ActionW.LUT_SHAPE.cmd(), lutShapeItem);

                if (!temporaire) {
                    this.setActionsInView(ActionW.PRESET.cmd(), newPreset);
                    this.setActionsInView(ActionW.DEFAULT_PRESET.cmd(), false);
                    this.setActionsInView(ActionW.WINDOW.cmd(), windowValue);
                    this.setActionsInView(ActionW.LEVEL.cmd(), levelValue);
                    this.setActionsInView(ActionW.LEVEL_MIN.cmd(), levelMin);
                    this.setActionsInView(ActionW.LEVEL_MAX.cmd(), levelMax);
                    this.setActionsInView(ActionW.LUT_SHAPE.cmd(), lutShapeItem);
                    this.setActionsInView(IS_PRESET_WINDOW_LEVEL, true);
                }

                image = SwingFXUtils.toFXImage(ImageConversion.toBufferedImage(process()), null);
                draw();
            }
        }
    }

    /*****************************************************************
     * Measure *
     *****************************************************************/
    public void addMeasure(Integer measureType, Color c) {
        measureInProgress.set(measureType);
        if (measureType == Mesure.LINE) {
            this.mesures.addMesure(new LineMesure((Integer) this.getActionValue(ActionW.SCROLL_SERIES.cmd()), c,
                sourceImage.getPixelSize(), sourceImage.getPixelSpacingUnit(), this));
        } else if (measureType == Mesure.RECTANGLE) {
            this.mesures.addMesure(new RectangleMesure((Integer) this.getActionValue(ActionW.SCROLL_SERIES.cmd()), c,
                sourceImage.getPixelSize(), sourceImage.getPixelSpacingUnit(), this));
        }
    }

    /*****************************************************************
     * Other *
     *****************************************************************/
    public MediaSeriesGroup getGroupID() {
        // TODO Auto-generated method stub
        return null;
    }

    public OpManager getDisplayOpManager() {
        return manager;
    }

    public MediaSeries<DicomImageElement> getSeries() {
        return series;
    }

    public Object getActionValue(String action) {
        if (action == null) {
            return null;
        }
        return actionsInView.get(action);
    }

    public double getViewScale() {
        return (Double) this.getActionValue(ActionW.ZOOM.cmd()) * (Double) this.actionsInView.get(ZOOM_FACTOR_SCREEN);
    }
}