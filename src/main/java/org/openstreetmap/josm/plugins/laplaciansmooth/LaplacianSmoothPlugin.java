/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https:
 *
 * Copyright (c) 2026 JOSM Plugin Builder
 */
package org.openstreetmap.josm.plugins.laplaciansmooth;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.laplaciansmooth.actions.LaplacianSmoothAction;

public class LaplacianSmoothPlugin extends Plugin {
    
    private LaplacianSmoothAction action;
    
    public LaplacianSmoothPlugin(PluginInformation info) {
        super(info);
        action = new LaplacianSmoothAction();
        MainApplication.getMenu().toolsMenu.addSeparator();
        MainApplication.getMenu().toolsMenu.add(action);
    }
    
    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (action != null) {
            action.setEnabled(newFrame != null);
        }
    }
}
