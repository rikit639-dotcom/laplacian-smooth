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
package org.openstreetmap.josm.plugins.laplaciansmooth.gui;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.laplaciansmooth.algorithms.LineSmoother;
import org.openstreetmap.josm.spi.preferences.Config;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.openstreetmap.josm.tools.I18n.tr;

public class LaplacianSmoothDialog extends JDialog {
    
    private final List<Way> originalWays;
    
    private JSlider iterationsSlider;
    private JLabel iterationsValueLabel;
    private JSlider lambdaSlider;
    private JLabel lambdaValueLabel;
    private JCheckBox preserveEndpointsCheckBox;
    private JCheckBox preserveCornersCheckBox;
    private JCheckBox taubinCheckBox;
    private JCheckBox protectSpecialPointsCheckBox;
    private JButton weakPresetButton;
    private JButton mediumPresetButton;
    private JButton strongPresetButton;
    
    public LaplacianSmoothDialog(List<Way> ways) {
        super(MainApplication.getMainFrame(), tr("Laplacian smoothing"), true);
        this.originalWays = ways;
        
        setLayout(new BorderLayout());
        setSize(450, 400);
        setLocationRelativeTo(MainApplication.getMainFrame());
        
        initComponents();
        loadSettings();
    }
    
    private void initComponents() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        
        JPanel presetPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        presetPanel.setBorder(BorderFactory.createTitledBorder(tr("Presets")));
        
        weakPresetButton = new JButton(tr("Weak preset"));
        mediumPresetButton = new JButton(tr("Medium preset"));
        strongPresetButton = new JButton(tr("Strong preset"));
        
        weakPresetButton.addActionListener(e -> setPreset(2, 0.3));
        mediumPresetButton.addActionListener(e -> setPreset(5, 0.5));
        strongPresetButton.addActionListener(e -> setPreset(10, 0.7));
        
        presetPanel.add(weakPresetButton);
        presetPanel.add(mediumPresetButton);
        presetPanel.add(strongPresetButton);
        
        JPanel iterationsPanel = new JPanel(new BorderLayout(10, 5));
        iterationsPanel.setBorder(BorderFactory.createTitledBorder(tr("Iterations")));
        
        iterationsSlider = new JSlider(1, 20, 5);
        iterationsSlider.setMajorTickSpacing(5);
        iterationsSlider.setMinorTickSpacing(1);
        iterationsSlider.setPaintTicks(true);
        iterationsSlider.setPaintLabels(true);
        iterationsSlider.addChangeListener(e -> {
            int val = iterationsSlider.getValue();
            iterationsValueLabel.setText(String.valueOf(val));
        });
        
        iterationsValueLabel = new JLabel("5");
        iterationsValueLabel.setPreferredSize(new Dimension(30, 25));
        
        JPanel iterationsSliderPanel = new JPanel(new BorderLayout());
        iterationsSliderPanel.add(iterationsSlider, BorderLayout.CENTER);
        iterationsSliderPanel.add(iterationsValueLabel, BorderLayout.EAST);
        iterationsPanel.add(iterationsSliderPanel, BorderLayout.CENTER);
        
        JPanel lambdaPanel = new JPanel(new BorderLayout(10, 5));
        lambdaPanel.setBorder(BorderFactory.createTitledBorder(tr("Strength (λ)")));
        
        lambdaSlider = new JSlider(0, 100, 50);
        lambdaSlider.setMajorTickSpacing(25);
        lambdaSlider.setMinorTickSpacing(5);
        lambdaSlider.setPaintTicks(true);
        lambdaSlider.setPaintLabels(true);
        lambdaSlider.addChangeListener(e -> {
            int val = lambdaSlider.getValue();
            lambdaValueLabel.setText(String.format("%.1f", val / 100.0));
        });
        
        lambdaValueLabel = new JLabel("0.5");
        lambdaValueLabel.setPreferredSize(new Dimension(30, 25));
        
        JPanel lambdaSliderPanel = new JPanel(new BorderLayout());
        lambdaSliderPanel.add(lambdaSlider, BorderLayout.CENTER);
        lambdaSliderPanel.add(lambdaValueLabel, BorderLayout.EAST);
        lambdaPanel.add(lambdaSliderPanel, BorderLayout.CENTER);
        
        JPanel optionsPanel = new JPanel();
        optionsPanel.setBorder(BorderFactory.createTitledBorder(tr("Options")));
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        
        preserveEndpointsCheckBox = new JCheckBox(tr("Smooth: Preserve endpoints"));
        preserveCornersCheckBox = new JCheckBox(tr("Smooth: Preserve sharp corners (>45°)"));
        taubinCheckBox = new JCheckBox(tr("Smooth: Preserve volume (Taubin method)"));
        protectSpecialPointsCheckBox = new JCheckBox(tr("Smooth: Protect special points (junctions, tagged nodes)"));
        preserveEndpointsCheckBox.setSelected(true);
        preserveCornersCheckBox.setSelected(false);
        taubinCheckBox.setSelected(false);
        protectSpecialPointsCheckBox.setSelected(true);
        
        optionsPanel.add(preserveEndpointsCheckBox);
        optionsPanel.add(preserveCornersCheckBox);
        optionsPanel.add(taubinCheckBox);
        optionsPanel.add(protectSpecialPointsCheckBox);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton applyButton = new JButton(tr("Apply"));
        JButton cancelButton = new JButton(tr("Cancel"));
        JButton saveButton = new JButton(tr("Save as default settings"));
        
        applyButton.addActionListener(this::applyChanges);
        cancelButton.addActionListener(e -> dispose());
        saveButton.addActionListener(this::saveSettings);
        
        buttonPanel.add(applyButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        
        mainPanel.add(presetPanel);
        mainPanel.add(iterationsPanel);
        mainPanel.add(lambdaPanel);
        mainPanel.add(optionsPanel);
        
        add(mainPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        
        mainPanel.setFocusable(true);
        mainPanel.requestFocusInWindow();
    }
    
    private void setPreset(int iterations, double lambda) {
        iterationsSlider.setValue(iterations);
        lambdaSlider.setValue((int)(lambda * 100));
    }
    
    private void loadSettings() {
        org.openstreetmap.josm.spi.preferences.IPreferences prefs = Config.getPref();
        
        int iterations = Integer.parseInt(prefs.get("laplacian_smooth.iterations", "5"));
        double lambda = Double.parseDouble(prefs.get("laplacian_smooth.lambda", "0.5"));
        boolean preserveEndpoints = Boolean.parseBoolean(prefs.get("laplacian_smooth.preserve_endpoints", "true"));
        boolean preserveCorners = Boolean.parseBoolean(prefs.get("laplacian_smooth.preserve_corners", "false"));
        boolean useTaubin = Boolean.parseBoolean(prefs.get("laplacian_smooth.use_taubin", "false"));
        boolean protectSpecial = Boolean.parseBoolean(prefs.get("laplacian_smooth.protect_special", "true"));
        
        iterationsSlider.setValue(iterations);
        lambdaSlider.setValue((int)(lambda * 100));
        preserveEndpointsCheckBox.setSelected(preserveEndpoints);
        preserveCornersCheckBox.setSelected(preserveCorners);
        taubinCheckBox.setSelected(useTaubin);
        protectSpecialPointsCheckBox.setSelected(protectSpecial);
    }
    
    private void saveSettings(ActionEvent e) {
        org.openstreetmap.josm.spi.preferences.IPreferences prefs = Config.getPref();
        
        prefs.put("laplacian_smooth.iterations", String.valueOf(iterationsSlider.getValue()));
        prefs.put("laplacian_smooth.lambda", String.valueOf(lambdaSlider.getValue() / 100.0));
        prefs.put("laplacian_smooth.preserve_endpoints", String.valueOf(preserveEndpointsCheckBox.isSelected()));
        prefs.put("laplacian_smooth.preserve_corners", String.valueOf(preserveCornersCheckBox.isSelected()));
        prefs.put("laplacian_smooth.use_taubin", String.valueOf(taubinCheckBox.isSelected()));
        prefs.put("laplacian_smooth.protect_special", String.valueOf(protectSpecialPointsCheckBox.isSelected()));
        
        JOptionPane.showMessageDialog(this, 
            tr("Settings saved by default"), 
            tr("Laplacian smoothing"), 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void applyChanges(ActionEvent e) {
        int iterations = iterationsSlider.getValue();
        double lambda = lambdaSlider.getValue() / 100.0;
        boolean preserveEndpoints = preserveEndpointsCheckBox.isSelected();
        boolean preserveCorners = preserveCornersCheckBox.isSelected();
        boolean useTaubin = taubinCheckBox.isSelected();
        boolean protectSpecial = protectSpecialPointsCheckBox.isSelected();
        
        setVisible(false);

        JDialog pd = new JDialog(MainApplication.getMainFrame(), tr("Laplacian smoothing"), false);
        pd.setResizable(false);
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        bar.setString(tr("Calculating geometry..."));
        bar.setPreferredSize(new Dimension(300, 22));
        JButton cancelBtn = new JButton(tr("Cancel"));
        
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 12, 0);
        p.add(bar, gbc);
        
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        p.add(cancelBtn, gbc);
        
        pd.add(p, BorderLayout.CENTER);
        pd.pack();
        pd.setLocationRelativeTo(MainApplication.getMainFrame());
        
        long totalWorkUnits = 0;
        for (Way way : originalWays) {
            int nodeCount = way.getNodesCount();
            if (nodeCount >= 2) {
                totalWorkUnits += (long) nodeCount * iterations;
            }
        }
        final long finalTotalWorkUnits = totalWorkUnits;
        final AtomicLong processedPoints = new AtomicLong(0);
        
        SwingWorker<BulkMoveCommand, Integer> worker = new SwingWorker<BulkMoveCommand, Integer>() {
            @Override
            protected BulkMoveCommand doInBackground() {
                DataSet ds = MainApplication.getLayerManager().getEditDataSet();
                if (ds != null) {
                    ds.setSelected(new java.util.ArrayList<>(originalWays));
                }
                java.util.Map<Node, org.openstreetmap.josm.data.coor.LatLon> nodeMoves = new ConcurrentHashMap<>();
                originalWays.parallelStream().forEach(way -> {
                    if (isCancelled()) return;
                    List<Node> wayNodes = way.getNodes();
                    List<EastNorth> points = new ArrayList<>(wayNodes.size());
                    for (Node node : wayNodes) points.add(node.getEastNorth());
                    
                    java.util.Set<Integer> fixedExtra = new java.util.HashSet<>();
                    if (protectSpecial) {
                        for (int i = 0; i < wayNodes.size(); i++) {
                            Node n = wayNodes.get(i);
                            if (n.isTagged() || n.getParentWays().size() > 1) fixedExtra.add(i);
                        }
                    }

                    List<EastNorth> result = LineSmoother.smoothLaplacianWithProgress(
                        points, iterations, lambda, preserveEndpoints, preserveCorners, useTaubin, way.isClosed(),
                        fixedExtra,
                        () -> {
                            if (isCancelled()) return true;
                            long currentTotal = processedPoints.incrementAndGet();
                            if (currentTotal % 2000 == 0 || currentTotal == finalTotalWorkUnits) {
                                publish((int) (currentTotal * 100 / finalTotalWorkUnits));
                            }
                            return false;
                        }
                    );
                    
                    if (isCancelled() || result == null) return;
                    for (int i = 0; i < wayNodes.size(); i++) {
                        Node node = wayNodes.get(i);
                        org.openstreetmap.josm.data.coor.LatLon nCoor = 
                            org.openstreetmap.josm.data.projection.ProjectionRegistry.getProjection().eastNorth2latlon(result.get(i));
                        if (!node.getCoor().equalsEpsilon(nCoor, 1e-9)) nodeMoves.put(node, nCoor);
                    }
                });
                return isCancelled() ? null : new BulkMoveCommand(ds, nodeMoves, originalWays);
            }

            @Override
            protected void process(List<Integer> chunks) {
                if (!chunks.isEmpty()) bar.setValue(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                try {
                    BulkMoveCommand cmd = get();
                    if (cmd != null) org.openstreetmap.josm.data.UndoRedoHandler.getInstance().add(cmd);
                } catch (Exception ignored) {}
                pd.dispose();
                dispose();
            }
        };
        
        cancelBtn.addActionListener(ae -> worker.cancel(true));
        pd.setVisible(true);
        worker.execute();
    }

    private static class BulkMoveCommand extends org.openstreetmap.josm.command.Command {
        private final Node[] nodes;
        private final org.openstreetmap.josm.data.coor.LatLon[] newCoords, oldCoords;
        private final boolean[] oldModified;
        private final List<Way> affectedWays;
        private final List<List<Node>> waysNodesCache;

        public BulkMoveCommand(DataSet ds, java.util.Map<Node, org.openstreetmap.josm.data.coor.LatLon> moves, List<Way> ways) {
            super(ds);
            this.affectedWays = ways;
            this.waysNodesCache = new ArrayList<>(ways.size());
            for (Way w : ways) waysNodesCache.add(new ArrayList<>(w.getNodes()));
            
            int size = moves.size();
            this.nodes = new Node[size];
            this.newCoords = new org.openstreetmap.josm.data.coor.LatLon[size];
            this.oldCoords = new org.openstreetmap.josm.data.coor.LatLon[size];
            this.oldModified = new boolean[size];
            int i = 0;
            for (java.util.Map.Entry<Node, org.openstreetmap.josm.data.coor.LatLon> entry : moves.entrySet()) {
                nodes[i] = entry.getKey();
                newCoords[i] = entry.getValue();
                oldCoords[i] = nodes[i].getCoor();
                oldModified[i] = nodes[i].isModified();
                i++;
            }
        }

        @Override
        public boolean executeCommand() {
            return move(newCoords, true);
        }

        @Override
        public void undoCommand() {
            move(oldCoords, false);
        }

        private boolean move(org.openstreetmap.josm.data.coor.LatLon[] coords, boolean mod) {
            DataSet ds = getAffectedDataSet();
            if (ds == null) return false;
            ds.beginUpdate();
            try {
                for (Way w : affectedWays) w.setNodes(java.util.Collections.emptyList());
                for (int i = 0; i < nodes.length; i++) {
                    nodes[i].setCoor(coords[i]);
                    nodes[i].setModified(mod || oldModified[i]);
                }
                for (int i = 0; i < affectedWays.size(); i++) affectedWays.get(i).setNodes(waysNodesCache.get(i));
            } finally { ds.endUpdate(); }
            return true;
        }

        @Override public void fillModifiedData(java.util.Collection<org.openstreetmap.josm.data.osm.OsmPrimitive> m, java.util.Collection<org.openstreetmap.josm.data.osm.OsmPrimitive> d, java.util.Collection<org.openstreetmap.josm.data.osm.OsmPrimitive> a) { m.addAll(java.util.Arrays.asList(nodes)); }
        @Override public String getDescriptionText() { return tr("Laplacian smoothing ({0} lines)", affectedWays.size()); }
        @Override public java.util.Collection<org.openstreetmap.josm.data.osm.OsmPrimitive> getParticipatingPrimitives() { return java.util.Arrays.asList(nodes); }
    }
}
