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
package org.openstreetmap.josm.plugins.laplaciansmooth.actions;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.laplaciansmooth.gui.LaplacianSmoothDialog;
import org.openstreetmap.josm.tools.Shortcut;

import javax.swing.Action;
import javax.swing.JOptionPane;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import static org.openstreetmap.josm.tools.I18n.tr;

public class LaplacianSmoothAction extends JosmAction {
    
    public LaplacianSmoothAction() {
        super(
            tr("Laplacian smoothing"),
            "laplacian-smooth",
            tr("Laplacian smoothing of selected lines"),
            Shortcut.registerShortcut("tools:laplacian_smooth", 
                tr("Tools: {0}", tr("Laplacian smoothing")), 
                KeyEvent.VK_L, Shortcut.CTRL_SHIFT),
            true
        );
        putValue(Action.ACCELERATOR_KEY, getShortcut().getKeyStroke());
    }
    
    @Override
    public void actionPerformed(java.awt.event.ActionEvent e) {
        DataSet ds = MainApplication.getLayerManager().getEditDataSet();
        
        if (ds == null) {
            return;
        }
        
        List<Way> selectedWays = new ArrayList<>();
        for (Way way : ds.getSelectedWays()) {
            if (way.getNodesCount() >= 2) {
                selectedWays.add(way);
            }
        }
        
        if (selectedWays.isEmpty()) {
            JOptionPane.showMessageDialog(
                MainApplication.getMainFrame(),
                tr("Select at least one line."),
                tr("Laplacian smoothing"),
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        
        LaplacianSmoothDialog dialog = new LaplacianSmoothDialog(selectedWays);
        dialog.setVisible(true);
    }
}
