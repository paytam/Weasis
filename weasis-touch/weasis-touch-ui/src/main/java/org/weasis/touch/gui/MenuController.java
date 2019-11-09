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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import org.controlsfx.control.Notifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.op.ByteLutCollection;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.dicom.codec.display.PresetWindowLevel;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.LoadLocalDicom;
import org.weasis.touch.Messages;
import org.weasis.touch.WeasisPreferences;
import org.weasis.touch.internal.MainWindowListener;

import com.jfoenix.controls.JFXColorPicker;
import com.jfoenix.controls.JFXToggleButton;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TouchEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.RowConstraints;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

public class MenuController {

    private static final Logger LOGGER = LoggerFactory.getLogger(MenuController.class);

    public static final String EXIT = "exit";
    public static final String LOCK = "lock";
    public static final String TOOLS = "tools";
    public static final String SETTINGS = "settings";
    public static final String FULL_SCREEN = "fullScreen";
    public static final String OPEN_FILE = "openFile";
    public static final String CONTRASTE2 = "contraste";
    public static final String LUT = "lut";
    public static final String RESET = "reset";

    public final BooleanProperty lockedProperty = new SimpleBooleanProperty(false);
    public BooleanProperty blurProperty = new SimpleBooleanProperty(false);
    public BooleanProperty showMeasureProperty = new SimpleBooleanProperty(true);
    public BooleanProperty editModeProperty = new SimpleBooleanProperty(false);
    public IntegerProperty measureInProgress = new SimpleIntegerProperty(0);

    // Style for menu
    private static final String BORDER = "-fx-border-color:#b34711;-fx-border-width:3;";
    private static final String NO_BORDER = "-fx-border-color:none;";
    private static final String UNSELECTED_BACKGROUND_COLOR = "-fx-background-color:#00000022;";
    private static final String SELECTED_BACKGROUND_COLOR = "-fx-background-color:#b3471144;";
    private static final String NO_BACKGROUND_COLOR = "-fx-background-color:none;";

    @FXML
    Group menuGroup;
    @FXML
    GridPane menu;
    @FXML
    Node buttonPlus;
    @FXML
    private Node exit;
    @FXML
    private Node reset;
    @FXML
    private Node lock;
    @FXML
    private Node openFile;
    @FXML
    private Node contraste;
    @FXML
    private Node settings;
    @FXML
    private Node lut;
    @FXML
    private Node fullScreen;
    @FXML
    private Node unlockBorder;
    @FXML
    private Node tools;
    @FXML
    private ProgressIndicator unlockProgress;
    @FXML
    private Node unlockProgressBorder;
    @FXML
    private Text info;
    @FXML
    GridPane contrasteGrid;
    @FXML
    private GridPane lutGrid;
    @FXML
    GridPane toolsGrid;
    @FXML
    BorderPane trashMeasure;
    @FXML
    SVGPath trashMeasureSVG;
    @FXML
    BorderPane exitMeasure;
    @FXML
    BorderPane line;
    @FXML
    BorderPane rectangle;
    @FXML
    JFXToggleButton showHideMeasure;
    @FXML
    JFXToggleButton editMode;
    @FXML
    JFXColorPicker colorPicker;

    private MainCanvas canvas;

    private FadeTransition ftMenuShow;
    private FadeTransition ftMenuHide;
    private FadeTransition ftLutShow;
    private FadeTransition ftLutHide;

    private Stage stage;
    private AnchorPane rootPane;
    private Node setting;
    private Node confirmation;

    private Preferences prefs;
    private MenuWindowLevelController menuWindowLevelController;
    public MenuToolsControlleur menuToolsControlleur;

    @FXML
    private void initialize() {
        LOGGER.info("initialize menu");

        ftMenuShow = new FadeTransition(Duration.millis(300), menu);
        ftMenuShow.setFromValue(0);
        ftMenuShow.setToValue(1);

        ftMenuHide = new FadeTransition(Duration.millis(300), menu);
        ftMenuHide.setFromValue(1);
        ftMenuHide.setToValue(0);
        ftMenuHide.setOnFinished(this::handleFadeTransitionMenuEnd);

        ftLutShow = new FadeTransition(Duration.millis(300), lutGrid);
        ftLutShow.setFromValue(0);
        ftLutShow.setToValue(1);

        ftLutHide = new FadeTransition(Duration.millis(300), lutGrid);
        ftLutHide.setFromValue(1);
        ftLutHide.setToValue(0);
        ftLutHide.setOnFinished(this::handleFadeTransitionLutEnd);

        menu.setVisible(false);
        lutGrid.setVisible(false);
        toolsGrid.setVisible(false);

        buttonPlus.setOnMouseClicked(this::handleBoutonPlusOnMouseClicked);
        buttonPlus.setOnTouchReleased(this::handleBoutonPlusOnTouchReleased);

        menu.setOnTouchMoved(this::handleOnTouchOverGridMenu);
        menu.setOnTouchStationary(this::handleOnTouchOverGridMenu);

        for (Node iterable_element : menu.getChildren()) {
            if (iterable_element.getId() != null) {
                iterable_element.setOnTouchMoved(this::handleOnTouchOverMenu);
                iterable_element.setOnTouchStationary(this::handleOnTouchOverMenu);
                iterable_element.setOnTouchReleased(this::handleOnTouchReleasedMenu);

                iterable_element.setOnMouseEntered(this::handleOnMouseEntredMenu);
                iterable_element.setOnMouseExited(this::handleOnMouseExitedMenu);
                iterable_element.setOnMouseClicked(this::handleOnMouseClickedMenu);
            }
        }

        lutGrid.setOnTouchMoved(this::handleOnTouchOverLutMenu);
        lutGrid.setOnTouchStationary(this::handleOnTouchOverLutMenu);

        initUnlock();
        unlockBorder.setOnTouchPressed(this::handleOnTouchPressedUnlock);
        unlockBorder.setOnTouchReleased(this::handleOnTouchReleasedUnlock);
        unlockBorder.setOnMouseClicked(this::handleOnClickedUnlock);

        unlockProgressBorder.setLayoutX(unlockBorder.getLayoutX() - 40);
        unlockProgressBorder.setLayoutY(unlockBorder.getLayoutY() - 40);

        prefs = Preferences.userRoot().node(SettingsController.class.getName());
        blurProperty.addListener((ChangeListener<Boolean>) (o, oldVal, newVal) -> {
            if (newVal) {
                menuGroup.setEffect(new GaussianBlur());
            } else {
                menuGroup.setEffect(null);
            }
        });

        buttonPlus.setOnMouseEntered(e -> {
            if (!e.isSynthesized()) {
                buttonPlus.setScaleX(buttonPlus.getScaleX() + 0.1);
                buttonPlus.setScaleY(buttonPlus.getScaleY() + 0.1);
            }
            e.consume();
        });
        buttonPlus.setOnMouseExited(e -> {
            if (!e.isSynthesized()) {
                buttonPlus.setScaleX(buttonPlus.getScaleX() - 0.1);
                buttonPlus.setScaleY(buttonPlus.getScaleY() - 0.1);
            }
            e.consume();
        });

        unlockBorder.setOnMouseEntered(e -> {
            if (!e.isSynthesized()) {
                unlockBorder.setScaleX(unlockBorder.getScaleX() + 0.1);
                unlockBorder.setScaleY(unlockBorder.getScaleY() + 0.1);
            }
            e.consume();
        });
        unlockBorder.setOnMouseExited(e -> {
            if (!e.isSynthesized()) {
                unlockBorder.setScaleX(unlockBorder.getScaleX() - 0.1);
                unlockBorder.setScaleY(unlockBorder.getScaleY() - 0.1);
            }
            e.consume();
        });
    }

    public void setParam(MainViewController mainViewController, Stage stage) {

        this.stage = stage;
        this.canvas = mainViewController.dicomViewer.mainCanvas;
        Scene scene = this.menuGroup.getScene();

        menuWindowLevelController = new MenuWindowLevelController(this, canvas);
        menuToolsControlleur = new MenuToolsControlleur(this, canvas);

        this.rootPane = (AnchorPane) scene.getRoot();
        scene.widthProperty().addListener((ChangeListener<Object>) (o, oldVal, newVal) -> {
            menuGroup.setLayoutX((double) newVal - (menuGroup.getBoundsInLocal().getWidth()
                - (menuGroup.getBoundsInLocal().getWidth() - menuGroup.getBoundsInLocal().getMaxX())) - 7);
        });
        scene.heightProperty().addListener((ChangeListener<Object>) (o, oldVal, newVal) -> {
            menuGroup
                .setLayoutY((double) newVal
                    - (menuGroup.getBoundsInLocal().getHeight()
                        - (menuGroup.getBoundsInLocal().getHeight() - menuGroup.getBoundsInLocal().getMaxY()))
                    - 7 - 70);
        });
    }

    /*****************************************************************
     * Main Menu *
     *****************************************************************/
    private void handleBoutonPlusOnTouchReleased(TouchEvent event) {
        buttonPlusTouchClic();
        event.consume();
    }

    private void handleBoutonPlusOnMouseClicked(MouseEvent event) {
        if (!event.isSynthesized()) {
            buttonPlusTouchClic();
        }
        event.consume();
    }

    private void buttonPlusTouchClic() {
        if (!lockedProperty.getValue()) {
            if (!menu.isVisible()) {
                if (canvas.isImageNull()) {
                    ((SVGPath) ((BorderPane) contraste).getChildren().get(0)).setFill(Color.GREY);
                    ((SVGPath) ((BorderPane) lut).getChildren().get(0)).setFill(Color.GREY);
                    ((SVGPath) ((BorderPane) reset).getChildren().get(0)).setFill(Color.GREY);
                    ((SVGPath) ((BorderPane) tools).getChildren().get(0)).setFill(Color.GREY);
                } else {
                    ((SVGPath) ((BorderPane) contraste).getChildren().get(0)).setFill(Color.rgb(0xb3, 0x47, 0x11));
                    ((SVGPath) ((BorderPane) lut).getChildren().get(0)).setFill(Color.rgb(0xb3, 0x47, 0x11));
                    ((SVGPath) ((BorderPane) reset).getChildren().get(0)).setFill(Color.rgb(0xb3, 0x47, 0x11));
                    ((SVGPath) ((BorderPane) tools).getChildren().get(0)).setFill(Color.rgb(0xb3, 0x47, 0x11));
                }
                ftMenuHide.stop();
                menu.setVisible(true);
                ftMenuShow.playFromStart();
                lockedProperty.set(true);
            }
        } else {
            close();
        }
    }

    private void handleOnMouseEntredMenu(MouseEvent event) {
        if (!event.isSynthesized()) {
            if (event.getSource().getClass() == BorderPane.class) {
                BorderPane bp = (BorderPane) event.getSource();

                if (!contrasteGrid.isVisible() && !lutGrid.isVisible() && !toolsGrid.isVisible()) {
                    displaySelection(bp);
                }
            }
        }
        event.consume();
    }

    private void displaySelection(BorderPane bp) {

        if (!contrasteGrid.isVisible() && !lutGrid.isVisible() && !toolsGrid.isVisible()) {
            bp.setStyle(SELECTED_BACKGROUND_COLOR);
            switch (bp.getId()) {
                case LOCK:
                    info.setText(Messages.getString("WeasisTouchMenu.lock"));
                    break;
                case EXIT:
                    info.setText(Messages.getString("WeasisTouchMenu.exit"));
                    break;
                case RESET:
                    info.setText(Messages.getString("WeasisTouchMenu.reset"));
                    break;
                case LUT:
                    info.setText(Messages.getString("WeasisTouchMenu.lut"));
                    break;
                case CONTRASTE2:
                    info.setText(Messages.getString("WeasisTouchMenu.windowLevel"));
                    break;
                case OPEN_FILE:
                    info.setText(Messages.getString("WeasisTouchMenu.open"));
                    break;
                case FULL_SCREEN:
                    info.setText(Messages.getString("WeasisTouchMenu.fullScreen"));
                    break;
                case SETTINGS:
                    info.setText(Messages.getString("WeasisTouchMenu.settings"));
                    break;
                case TOOLS:
                    info.setText(Messages.getString("WeasisTouchMenu.tools"));
                    break;
                default:
                    info.setText("");
                    break;
            }
        }
    }

    private void handleOnMouseExitedMenu(MouseEvent event) {
        if (!event.isSynthesized()) {
            if (!contrasteGrid.isVisible() && !lutGrid.isVisible() && !toolsGrid.isVisible()) {
                if (event.getSource().getClass() == BorderPane.class) {
                    BorderPane bp = (BorderPane) event.getSource();
                    bp.setStyle(NO_BACKGROUND_COLOR);
                    info.setText("");
                }
            }
        }
        event.consume();
    }

    private void handleOnMouseClickedMenu(MouseEvent event) {
        if (event.getSource().getClass() == BorderPane.class && !event.isSynthesized()) {
            BorderPane bp = (BorderPane) event.getSource();
            if (!contrasteGrid.isVisible() && !lutGrid.isVisible() && !toolsGrid.isVisible()) {
                switch (bp.getId()) {
                    case LOCK:
                    case EXIT:
                    case RESET:
                    case FULL_SCREEN:
                        close();
                        break;
                    case OPEN_FILE:
                        ftMenuHide.playFrom(Duration.millis(250));
                }

                switch (bp.getId()) {
                    case LOCK:
                        lock();
                        break;
                    case EXIT:
                        exit();
                        break;
                    case RESET:
                        reset();
                        break;
                    case LUT:
                        lut();
                        break;
                    case CONTRASTE2:
                        menuWindowLevelController.windowLevel();
                        break;
                    case OPEN_FILE:
                        flagOpenFile = true;
                        break;
                    case FULL_SCREEN:
                        fullScreen();
                        break;
                    case SETTINGS:
                        settings();
                        break;
                    case TOOLS:
                        menuToolsControlleur.tools();
                        break;
                    default:
                        break;
                }
            } else {
                if (contrasteGrid.isVisible()) {
                    menuWindowLevelController.ftContrasteHide.play();
                    contraste.setStyle(NO_BACKGROUND_COLOR);
                } else if (lutGrid.isVisible()) {
                    ftLutHide.play();
                    lut.setStyle(NO_BACKGROUND_COLOR);
                } else if (toolsGrid.isVisible()) {
                    menuToolsControlleur.ftToolsHide.play();
                    tools.setStyle(NO_BACKGROUND_COLOR);
                }
                displaySelection(bp);
            }
        }
        event.consume();
    }

    private void handleOnTouchOverMenu(TouchEvent event) {
        if (event.getSource().getClass() == BorderPane.class) {
            BorderPane bp = (BorderPane) event.getSource();

            if (contrasteGrid.isVisible() && bp.getId().compareTo(CONTRASTE2) != 0) {
                menuWindowLevelController.ftContrasteHide.play();
            } else if (lutGrid.isVisible() && bp.getId().compareTo(LUT) != 0) {
                ftLutHide.play();
            } else if (toolsGrid.isVisible() && bp.getId().compareTo(TOOLS) != 0) {
                menuToolsControlleur.ftToolsHide.play();
            }

            for (Node iterable_element : menu.getChildren()) {
                iterable_element.setStyle(NO_BACKGROUND_COLOR);
            }

            displaySelection(bp);
        }
        event.getTouchPoint().ungrab();
        event.consume();
    }

    private void handleOnTouchReleasedMenu(TouchEvent event) {
        if (event.getTouchCount() == 1) {
            if (event.getSource().getClass() == BorderPane.class) {

                BorderPane bp = (BorderPane) event.getSource();
                switch (bp.getId()) {
                    case LOCK:
                    case EXIT:
                    case RESET:
                    case FULL_SCREEN:
                    case OPEN_FILE:
                        close();
                        break;

                }
                switch (bp.getId()) {
                    case LOCK:
                        lock();
                        break;
                    case EXIT:
                        exit();
                        break;
                    case RESET:
                        reset();
                        break;
                    case LUT:
                        lut();
                        break;
                    case CONTRASTE2:
                        menuWindowLevelController.windowLevel();
                        break;
                    case OPEN_FILE:
                        flagOpenFile = true;
                        break;
                    case FULL_SCREEN:
                        fullScreen();
                        break;
                    case SETTINGS:
                        settings();
                        break;
                    case TOOLS:
                        menuToolsControlleur.tools();
                        break;
                    default:
                        break;
                }
            }
        }
        event.consume();
    }

    private void handleOnTouchOverGridMenu(TouchEvent event) {
        for (Node iterable_element : menu.getChildren()) {
            iterable_element.setStyle(NO_BACKGROUND_COLOR);
        }
        if (contrasteGrid.isVisible()) {
            menuWindowLevelController.ftContrasteHide.play();
            contraste.setStyle(NO_BACKGROUND_COLOR);
        } else if (lutGrid.isVisible()) {
            ftLutHide.play();
            lut.setStyle(NO_BACKGROUND_COLOR);
        } else if (toolsGrid.isVisible()) {
            menuToolsControlleur.ftToolsHide.play();
            tools.setStyle(NO_BACKGROUND_COLOR);
        }
        info.setText("");
        event.getTouchPoint().ungrab();
        event.consume();
    }

    /////////////////
    // action
    /////////////////
    private void lock() {
        menuGroup.getChildren().add(unlockBorder);
        lockedProperty.set(true);
        if (prefs.getBoolean(WeasisPreferences.SHOW_NOTIFICATIONS.name(),
            (boolean) WeasisPreferences.SHOW_NOTIFICATIONS.defaultValue())) {
            Notifications notification =
                Notifications.create().title(Messages.getString("WeasisTouchNotification.lockTitle")).graphic(null)
                    .hideAfter(Duration.seconds(3)).position(Pos.TOP_CENTER).hideCloseButton().darkStyle()
                    .owner(this.menu).text(Messages.getString("WeasisTouchNotification.lockText"));
            notification.show();
        }
    }

    private void exit() {
        if (prefs.getBoolean(WeasisPreferences.ASK_EXIT.name(), (boolean) WeasisPreferences.ASK_EXIT.defaultValue())) {

            blurProperty.setValue(true);
            try {
                FXMLLoader loaderConfirmation = new FXMLLoader();
                loaderConfirmation.setResources(Messages.RESOURCE_BUNDLE);
                loaderConfirmation.setLocation(this.getClass().getResource("ConfirmationView.fxml"));
                loaderConfirmation.setClassLoader(this.getClass().getClassLoader());
                confirmation = loaderConfirmation.load();
                ConfirmationController confirmationController = loaderConfirmation.getController();
                confirmationController.setType(ConfirmationController.TYPE_EXIT, this);

                if (!this.rootPane.getChildren().contains(confirmation)) {
                    this.rootPane.getChildren().add(confirmation);
                }
            } catch (IOException e) {
                LOGGER.error("Exit", e);
            }
        } else {
            Platform.exit();
            System.exit(0);
        }
    }

    public void exit(Boolean res) {
        if (res) {
            Platform.exit();
            System.exit(0);
        } else {
            hideConfirmation();
        }
    }

    private void reset() {
        if (!canvas.isImageNull()) {
            if (prefs.getBoolean(WeasisPreferences.ASK_RESET.name(),
                (boolean) WeasisPreferences.ASK_RESET.defaultValue())) {

                blurProperty.setValue(true);
                try {
                    FXMLLoader loaderConfirmation = new FXMLLoader();
                    loaderConfirmation.setResources(Messages.RESOURCE_BUNDLE);
                    loaderConfirmation.setLocation(this.getClass().getResource("ConfirmationView.fxml"));
                    loaderConfirmation.setClassLoader(this.getClass().getClassLoader());
                    confirmation = loaderConfirmation.load();
                    ConfirmationController confirmationController = loaderConfirmation.getController();
                    confirmationController.setType(ConfirmationController.TYPE_RESET, this);

                    if (!this.rootPane.getChildren().contains(confirmation)) {
                        this.rootPane.getChildren().add(confirmation);
                    }
                } catch (IOException e) {
                    LOGGER.error("Reset", e);
                }

            } else {
                canvas.reset();
            }
        }
    }

    public void reset(boolean res) {
        if (res && !canvas.isImageNull()) {
            canvas.reset();
        }
        hideConfirmation();
    }

    private ArrayList<LUTCanvas> lcArray = new ArrayList<>();

    private void lut() {
        if (!canvas.isImageNull()) {
            if (lcArray.isEmpty()) {
                loadLuts();
            } else {
                String actuallLUT = "";
                ByteLut byteLut = (ByteLut) canvas.getActionValue(ActionW.LUT.cmd());
                if (byteLut != null) {
                    actuallLUT = byteLut.getName();
                }

                for (LUTCanvas lc : lcArray) {
                    if (lc.getName().compareTo(actuallLUT) == 0) {
                        lc.setCurentLUT(true);
                    } else {
                        lc.setCurentLUT(false);
                    }
                }
            }
            InvertLUTToggleButton invertLUTToggleButton =
                (InvertLUTToggleButton) lutGrid.getChildren().get(lutGrid.getChildren().size() - 1);
            invertLUTToggleButton.setToggleButton((Boolean) canvas.getActionValue(ActionW.INVERT_LUT.cmd()));
            if (!lutGrid.isVisible()) {
                lutGrid.setVisible(true);
                ftLutShow.playFromStart();
            }
        } else {
            this.close();
        }
    }

    public static final int MAX_COLUMN = 5;

    private void loadLuts() {
        List<ByteLut> luts = new ArrayList<>();
        luts.add(ByteLutCollection.Lut.GRAY.getByteLut());
        ByteLutCollection.readLutFilesFromResourcesDir(luts, ResourceUtil.getResource("luts"));

        if (luts != null) {

            // clean lutGrid
            lutGrid.getChildren().remove(0, lutGrid.getChildren().size());
            lutGrid.getRowConstraints().remove(0, lutGrid.getRowConstraints().size());
            lutGrid.getColumnConstraints().remove(0, lutGrid.getColumnConstraints().size());

            // create new lutGrid
            int nbColumn = MAX_COLUMN;
            int nbRow = 1;

            if ((luts.size() + 1) / MAX_COLUMN > 0) {
                nbRow = (int) Math.ceil((luts.size() + 1) / (double) MAX_COLUMN);
            } else {
                nbColumn = (luts.size() + 1) % MAX_COLUMN;
            }
            for (int r = 0; r < nbRow; r++) {
                lutGrid.getRowConstraints().add(new RowConstraints(40));
            }
            for (int c = 0; c < nbColumn; c++) {
                lutGrid.getColumnConstraints()
                    .add(new ColumnConstraints(menu.getColumnConstraints().get(0).getPrefWidth()));
            }

            // fill lutGrid
            int index = 0;
            String actuallLUT = "";
            ByteLut byteLut = (ByteLut) canvas.getActionValue(ActionW.LUT.cmd());
            if (byteLut != null) {
                actuallLUT = byteLut.getName();
            }
            for (ByteLut bLut : luts) {
                lcArray.add(new LUTCanvas(bLut, canvas, this, menu.getColumnConstraints().get(0).getPrefWidth(), 40));
                lutGrid.add(lcArray.get(index), index % MAX_COLUMN, index / MAX_COLUMN);
                if (lcArray.get(index).getName().compareTo(actuallLUT) == 0) {
                    lcArray.get(index).setCurentLUT(true);
                }
                index++;
            }

            lutGrid.add(new InvertLUTToggleButton(canvas), index % MAX_COLUMN, index / MAX_COLUMN);

            lutGrid.setLayoutY((-50) * nbRow - 15.0);
        }
    }

    Boolean flagOpenFile = false;

    private void openFile() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(Messages.getString("WeasisTouchMenu.openTitle"));
        File file = directoryChooser.showDialog(stage);

        if (file == null) {
            LOGGER.trace("no file to open (file == null)");
        } else {
            MainWindowListener listener =
                BundlePreferences.getService(AppProperties.getBundleContext(), MainWindowListener.class);
            if (listener != null) {
                LoadLocalDicom dicom = new LoadLocalDicom(new File[] { file }, true, listener.getModel());
                DicomModel.LOADING_EXECUTOR.execute(dicom);
            }
        }
    }

    private void fullScreen() {
        Stage s = (Stage) menu.getScene().getWindow();
        if (s.isFullScreen()) {
            s.setFullScreen(false);
        } else {
            s.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
            s.setFullScreen(true);
        }
    }

    private void settings() {

        blurProperty.setValue(true);
        try {
            FXMLLoader loaderSettings = new FXMLLoader();
            loaderSettings.setResources(Messages.RESOURCE_BUNDLE);
            loaderSettings.setLocation(this.getClass().getResource("SettingsView.fxml"));
            loaderSettings.setClassLoader(this.getClass().getClassLoader());
            setting = loaderSettings.load();
            SettingsController settingsController = loaderSettings.getController();
            settingsController.setParam(this);

            if (!this.rootPane.getChildren().contains(setting)) {
                this.rootPane.getChildren().add(setting);
                settingsController.loadFirstPage();
            }
        } catch (IOException e) {
            LOGGER.error("Load settings", e);
        }

        ftMenuHide.playFromStart();
    }

    public void hideSettings() {
        blurProperty.setValue(false);
        this.rootPane.getChildren().remove(setting);
    }

    public void hideConfirmation() {
        blurProperty.setValue(false);
        this.rootPane.getChildren().remove(confirmation);
    }

    /*****************************************************************
     * TouchEvent (lock) *
     *****************************************************************/
    private static final Duration UNLOCK_TIME = Duration.millis(800);

    private final Timeline timeline = new Timeline();
    private IntegerProperty progressProperty = new SimpleIntegerProperty();
    private final KeyValue kv = new KeyValue(progressProperty, 100);
    private final KeyFrame kf = new KeyFrame(UNLOCK_TIME, kv);

    private void initUnlock() {
        menuGroup.getChildren().remove(unlockBorder);
        timeline.getKeyFrames().add(kf);
        unlockProgress.progressProperty().bind(progressProperty.divide(100.0));
        unlockProgress.managedProperty().bind(unlockProgress.visibleProperty());
        unlockProgress.setVisible(false);
    }

    private void handleOnTouchPressedUnlock(TouchEvent event) {
        if (lockedProperty.getValue()) {
            unlockProgress.setVisible(true);
            timeline.playFrom(Duration.ZERO);
        }
        event.consume();
    }

    private void handleOnTouchReleasedUnlock(TouchEvent event) {
        if (unlockProgress.getProgress() < 1) {
            timeline.stop();
        } else {
            lockedProperty.set(false);
            menuGroup.getChildren().remove(unlockBorder);
        }
        progressProperty.set(0);
        unlockProgress.setVisible(false);

        event.consume();
    }

    private void handleOnClickedUnlock(MouseEvent event) {
        if (!event.isSynthesized()) {
            lockedProperty.set(false);
            menuGroup.getChildren().remove(unlockBorder);
        }
    }

    /*****************************************************************
     * LUT *
     *****************************************************************/
    private void handleOnTouchOverLutMenu(TouchEvent event) {
        for (Node iterable_element : lutGrid.getChildren()) {
            if (iterable_element.getClass() == LUTCanvas.class) {
                LUTCanvas lc = (LUTCanvas) iterable_element;
                lc.unselect();
            }
        }
        ByteLut byteLut = (ByteLut) canvas.getActionValue(ActionW.LUT.cmd());
        if (byteLut != null) {
            canvas.setLut(byteLut);
        }
        event.getTouchPoint().ungrab();
        event.consume();
    }

    /*****************************************************************
     * FadeTransition *
     *****************************************************************/
    private void handleFadeTransitionMenuEnd(ActionEvent event) {
        if (!menuGroup.getChildren().contains(unlockBorder)) {
            lockedProperty.set(false);
        }
        menu.setVisible(false);
        for (Node iterable_element : menu.getChildren()) {
            iterable_element.setStyle(NO_BACKGROUND_COLOR);
        }
        info.setText("");

        if (flagOpenFile) {
            openFile();
            flagOpenFile = false;
        }
        event.consume();
    }

    private void handleFadeTransitionLutEnd(ActionEvent event) {
        lutGrid.setVisible(false);
        event.consume();
    }

    public void close() {
        if ((PresetWindowLevel) canvas.getActionValue(ActionW.PRESET.cmd()) != null) {
            canvas.setPresetWindowLevel(((PresetWindowLevel) canvas.getActionValue(ActionW.PRESET.cmd())).getName());
        }
        ByteLut byteLut = (ByteLut) canvas.getActionValue(ActionW.LUT.cmd());
        if (byteLut != null) {
            canvas.setLut(byteLut);
        }
        if (menu.isVisible() && ftMenuHide.getCurrentRate() == 0.0d) {
            ftMenuHide.playFromStart();
        }
        menuWindowLevelController.closeWindowLevel();
        if (lutGrid.isVisible() && ftLutHide.getCurrentRate() == 0.0d) {
            ftLutHide.playFromStart();
        }
        menuToolsControlleur.closeTools();
    }
}
