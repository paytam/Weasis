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

import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javafx.application.Application;

public class AppLauncher extends WeasisLauncher implements Singleton.SingletonApp {

    public AppLauncher(WeasisApp weasisApp, ConfigData configData) {
        super(weasisApp, configData);
    }

    public static void main(String[] argv) throws Exception {
        Application.launch(WeasisApp.class, argv);
    }

    @Override
    public void newActivation(List<String> arguments) {
        waitWhenStarted();
        if (mTracker != null) {
            executeCommands(arguments, null);
        }
    }

    private void waitWhenStarted() {
        synchronized (this) {
            int loop = 0;
            boolean runLoop = true;
            while (runLoop && !frameworkLoaded) {
                try {
                    TimeUnit.MILLISECONDS.sleep(100);
                    loop++;
                    if (loop > 300) { // Let 30s max to setup Felix framework
                        runLoop = false;
                    }
                } catch (InterruptedException e) {
                    runLoop = false;
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Override
    public boolean canStartNewActivation(Properties prop) {
        boolean sameUser = configData.isPropertyValueSimilar(P_WEASIS_USER, prop.getProperty(P_WEASIS_USER));
        boolean sameConfig =
            configData.isPropertyValueSimilar(P_WEASIS_CONFIG_HASH, prop.getProperty(P_WEASIS_CONFIG_HASH));
        return sameUser && sameConfig;
    }

    @Override
    protected void stopSingletonServer() {
        Singleton.stop();
    }
}
