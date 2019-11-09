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

import com.jfoenix.controls.JFXToggleButton;

import javafx.event.ActionEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class InvertLUTToggleButton extends JFXToggleButton {

    private MainCanvas canvas;

    public InvertLUTToggleButton(MainCanvas canvas) {
        this.canvas = canvas;

        this.setText("Invert");
        this.setFont(new Font(14));
        this.setToggleColor(Color.rgb(0xb3, 0x47, 0x11));
        this.setOnAction(this::handleOnActionToggleButton);
    }

    private void handleOnActionToggleButton(ActionEvent event) {
        JFXToggleButton toggleButton = (JFXToggleButton) event.getSource();
        this.canvas.setInvertLUT(toggleButton.isSelected());
    }

    public void setToggleButton(Boolean state) {
        this.setSelected(state);
    }

    @Override
    public String getUserAgentStylesheet() {
        return JFXToggleButton.class.getResource("/com/jfoenix/assets/css/controls/jfx-toggle-button.css").toExternalForm();
    }
}
