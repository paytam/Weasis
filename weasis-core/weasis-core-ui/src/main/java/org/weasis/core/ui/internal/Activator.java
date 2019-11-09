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
package org.weasis.core.ui.internal;

import java.io.File;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FileUtil;

public class Activator implements BundleActivator {
    
    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        File dataFolder = AppProperties.getBundleDataFolder(bundleContext);
        if (dataFolder != null) {
            FileUtil.readProperties(new File(dataFolder, "persitence.properties"), BundleTools.LOCAL_UI_PERSISTENCE);//$NON-NLS-1$
        }
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        File dataFolder = AppProperties.getBundleDataFolder(bundleContext);
        if (dataFolder != null) {
            File file = new File(dataFolder, "persitence.properties"); //$NON-NLS-1$
            FileUtil.prepareToWriteFile(file);
            FileUtil.storeProperties(file, BundleTools.LOCAL_UI_PERSISTENCE, null);
        }
    }
}
