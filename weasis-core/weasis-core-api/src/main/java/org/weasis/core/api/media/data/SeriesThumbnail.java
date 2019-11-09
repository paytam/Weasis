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

import java.io.File;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.FxUtil;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.util.FileUtil;

import javafx.beans.property.BooleanProperty;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

public class SeriesThumbnail extends Thumbnail {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeriesThumbnail.class);

    private MediaSeries.MEDIA_POSITION mediaPosition = MediaSeries.MEDIA_POSITION.MIDDLE;
    private final MediaSeries<? extends MediaElement> series;
    protected BooleanProperty lockProperty;


    public SeriesThumbnail(final MediaSeries<? extends MediaElement> sequence, int thumbnailSize) {
        super(thumbnailSize);
        if (sequence == null) {
            throw new IllegalArgumentException("Sequence cannot be null"); //$NON-NLS-1$
        }
        this.series = sequence;

        // media can be null for seriesThumbnail
        MediaElement media = sequence.getMedia(MEDIA_POSITION.MIDDLE, null, null);
        // Handle special case for DICOM SR
        if (media == null) {
            List<MediaElement> specialElements = (List<MediaElement>) series.getTagValue(TagW.DicomSpecialElementList);
            if (specialElements != null && !specialElements.isEmpty()) {
                media = specialElements.get(0);
            }
        }
        /*
         * Do not remove the image from the cache after building the thumbnail when the series is associated to a
         * explorerModel (stream should be closed at least when closing the application or when free the cache).
         */
        init(media, series.getTagValue(TagW.ExplorerModel) != null, null);
    }

    @Override
    protected void init(MediaElement media, boolean keepMediaCache, OpManager opManager) {
        super.init(media, keepMediaCache, opManager);
    }

    @Override
    public void registerListeners() {
        super.registerListeners();
    }

    public void reBuildThumbnail(MediaSeries.MEDIA_POSITION position) {
        reBuildThumbnail(null, position);
    }

    public void reBuildThumbnail() {
        reBuildThumbnail(null, mediaPosition);
    }

    public synchronized void reBuildThumbnail(File file, MediaSeries.MEDIA_POSITION position) {
        MediaElement media = series.getMedia(position, null, null);
        // Handle special case for DICOM SR
        if (media == null) {
            List<MediaElement> specialElements = (List<MediaElement>) series.getTagValue(TagW.DicomSpecialElementList);
            if (specialElements != null && !specialElements.isEmpty()) {
                media = specialElements.get(0);
            }
        }
        if (file != null || media != null) {
            mediaPosition = position;
            if (thumbnailPath != null && thumbnailPath.getPath().startsWith(AppProperties.FILE_CACHE_DIR.getPath())) {
                FileUtil.delete(thumbnailPath); // delete old temp file
            }
            thumbnailPath = file;
            readable = true;
            /*
             * Do not remove the image from the cache after building the thumbnail when the series is associated to a
             * explorerModel (stream should be closed at least when closing the application or when free the cache).
             */
            buildThumbnail(media, series.getTagValue(TagW.ExplorerModel) != null, null);
        }
    }

    @Override
    protected void drawOverIcon(GraphicsContext gc, double x, double y, double width, double height) {
            if (series.isOpen()) {
                gc.setFill(Color.BLACK);
                gc.fillRect(x, y, 13, 13);
                gc.setFill(Color.GREENYELLOW);
                gc.fillOval(x + 2.0, y + 2.0, 9, 9);
            }

            Font font = FxUtil.getArialFont();
            gc.setFont(font);
            double fontHeight =  FxUtil.getFontHeight(font);
            Integer splitNb = (Integer) series.getTagValue(TagW.SplitSeriesNumber);
            if (splitNb != null) {
                Text nb = new Text("#" + splitNb); //$NON-NLS-1$
                nb.setFont(font);
                double w = nb.getBoundsInLocal().getWidth();
                gc.setFill(Color.BLACK);
                double sx = x + width - 2 - w;
                gc.fillRect(sx - 2, y, w + 4, fontHeight);
                gc.setFill(Color.ORANGE);
                gc.fillText(nb.getText(), sx, y + fontHeight - 3.0);
            }

            Text nbImg = new Text("[" + series.size(null) + "]"); //$NON-NLS-1$ //$NON-NLS-2$
            nbImg.setFont(font);
            double hbleft = y + height - 3;
            double w = nbImg.getBoundsInLocal().getWidth();
            gc.setFill(Color.BLACK);
            gc.fillRect(x, y + height - fontHeight, w + 4, fontHeight);
            gc.setFill(Color.ORANGE);
            gc.fillText(nbImg.getText(), x + 2.0, hbleft);
        
            if (getSeries().getSeriesLoader() != null && series.getFileSize() > 0.0) {
                String val = FileUtil.humanReadableByte(series.getFileSize(), false);
                gc.setFill(Color.BLACK);
                gc.fillRect(x, hbleft - 9 - fontHeight, w + FxUtil.getTextBounds(val, font).getWidth(), fontHeight);
                gc.setFill(Color.ORANGE);
                gc.fillText(val, x + 2, hbleft - 12);
            }
    }

    public MediaSeries<? extends MediaElement> getSeries() {
        return series;
    }
}
