/*******************************************************************************
 * Copyright (C) 2009-2018 Weasis Team and others
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.launcher;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.Properties;

import org.osgi.framework.BundleContext;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class WeasisLoader {

    public static final String LBL_LOADING = Messages.getString("WebStartLoader.load"); //$NON-NLS-1$
    public static final String LBL_DOWNLOADING = Messages.getString("WebStartLoader.download"); //$NON-NLS-1$
    public static final String FRM_TITLE =
        String.format(Messages.getString("WebStartLoader.title"), System.getProperty("weasis.name")); //$NON-NLS-1$ //$NON-NLS-2$
    public static final String PRG_STRING_FORMAT = Messages.getString("WebStartLoader.end"); //$NON-NLS-1$

    private final ProgressBar loadProgress;
    private final Label progressText;

    private final File resPath;
    private final Properties localProperties;

    private volatile int maxProgress = 100;
    private volatile int curProgress = 0;

    public WeasisLoader(File resPath, Properties localProperties) {
        this.resPath = resPath;
        this.localProperties = localProperties;
        this.loadProgress = new ProgressBar();
        this.progressText = new Label(LBL_DOWNLOADING + "\n" + LBL_LOADING);
    }

    public void writeLabel(String text) {
        if (progressText != null) {
            Platform.runLater(
                () -> progressText.setText(text + "\n" + String.format(PRG_STRING_FORMAT, curProgress, maxProgress)));
        }
    }

    public Properties getLocalProperties() {
        return localProperties;
    }

    /*
     * Set maximum value for progress bar
     */
    public void setMax(int max) {
        maxProgress = max;
    }

    /*
     * Set actual value of progress bar
     */
    public void setValue(final int val) {
        curProgress = val;
        if (loadProgress != null) {
            Platform.runLater(() -> loadProgress.setProgress(curProgress / (double) maxProgress));
        }
    }

    public void setFelix(Map<String, String> serverProp, BundleContext bundleContext, Properties modulesi18n) {
        AutoProcessor.process(serverProp, modulesi18n, bundleContext, this);
    }

    public void start(final Stage initStage) throws Exception {
        StackPane stackPane = new StackPane();
        final ImageView splash = new ImageView();
        Image img = null;
        if (resPath != null) {
            File iconFile = new File(resPath, "images" + File.separator + "about.png"); //$NON-NLS-1$ //$NON-NLS-2$
            if (iconFile.canRead()) {
                String localUrl;
                try {
                    localUrl = iconFile.toURI().toURL().toString();
                    img = new Image(localUrl);
                } catch (MalformedURLException e) {
                    System.err.println(e.getMessage());
                }
            }
        }
        if (img == null) {
            splash.setFitWidth(350);
            splash.setFitHeight(75);
            Label l = new Label(FRM_TITLE);
            l.setStyle("-fx-font-size: 20;");
            stackPane.getChildren().add(splash);
            stackPane.getChildren().add(l);
        } else {
            splash.setImage(img);
            stackPane.getChildren().add(splash);
        }

        Bounds bound = splash.getBoundsInLocal();
        Pane splashLayout = new VBox();
        loadProgress.setPrefWidth(bound.getWidth());
        progressText.setTextAlignment(TextAlignment.CENTER);
        progressText.setPrefWidth(bound.getWidth());
        progressText.setStyle("-fx-font-size: 11;");
        progressText.setAlignment(Pos.CENTER);
        splashLayout.getChildren().addAll(stackPane, loadProgress, progressText);
        splashLayout.setStyle(
            "-fx-padding: 5; -fx-background-color: cornsilk; -fx-border-width:5; -fx-border-color: linear-gradient(to bottom, chocolate, derive(chocolate, 50%)"
                + ");");
        splashLayout.setEffect(new DropShadow());
        Scene splashScene = new Scene(splashLayout);
        initStage.initStyle(StageStyle.UNDECORATED);
        // initStage.initModality(Modality.APPLICATION_MODAL);
        initStage.setScene(splashScene);
        // initStage.centerOnScreen(); => does not really center, see
        // http://stackoverflow.com/questions/29558449/javafx-center-stage-on-screen
        initStage.show();

        Rectangle2D primScreenBounds = Screen.getPrimary().getVisualBounds();
        initStage.setX((primScreenBounds.getWidth() - initStage.getWidth()) / 2);
        initStage.setY((primScreenBounds.getHeight() - initStage.getHeight()) / 2);
    }

    public void close() {

    }
}
