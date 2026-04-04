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

import static org.openstreetmap.josm.tools.I18n.tr;

public class LaplacianSmoothDialog extends JDialog {
    
    private final List<Way> originalWays;
    
    private JSlider iterationsSlider;
    private JLabel iterationsValueLabel;
    private JSlider lambdaSlider;
    private JLabel lambdaValueLabel;
    private JCheckBox preserveEndpointsCheckBox;
    private JCheckBox preserveCornersCheckBox;
    private JButton weakPresetButton;
    private JButton mediumPresetButton;
    private JButton strongPresetButton;
    private JProgressBar progressBar;
    
    public LaplacianSmoothDialog(List<Way> ways) {
        super(MainApplication.getMainFrame(), tr("Laplacian smoothing"), true);
        this.originalWays = ways;
        
        setLayout(new BorderLayout());
        setSize(450, 350);
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
        preserveEndpointsCheckBox.setSelected(true);
        preserveCornersCheckBox.setSelected(false);
        
        optionsPanel.add(preserveEndpointsCheckBox);
        optionsPanel.add(preserveCornersCheckBox);
        
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        
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
        add(progressBar, BorderLayout.NORTH);
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
        
        iterationsSlider.setValue(iterations);
        lambdaSlider.setValue((int)(lambda * 100));
        preserveEndpointsCheckBox.setSelected(preserveEndpoints);
        preserveCornersCheckBox.setSelected(preserveCorners);
    }
    
    private void saveSettings(ActionEvent e) {
        org.openstreetmap.josm.spi.preferences.IPreferences prefs = Config.getPref();
        
        prefs.put("laplacian_smooth.iterations", String.valueOf(iterationsSlider.getValue()));
        prefs.put("laplacian_smooth.lambda", String.valueOf(lambdaSlider.getValue() / 100.0));
        prefs.put("laplacian_smooth.preserve_endpoints", String.valueOf(preserveEndpointsCheckBox.isSelected()));
        prefs.put("laplacian_smooth.preserve_corners", String.valueOf(preserveCornersCheckBox.isSelected()));
        
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
        
        setVisible(false);
        
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                DataSet ds = MainApplication.getLayerManager().getEditDataSet();
                
                java.util.Collection<org.openstreetmap.josm.data.osm.OsmPrimitive> onlyWays = new java.util.ArrayList<>(originalWays);
                ds.setSelected(onlyWays);
                
                java.util.Collection<org.openstreetmap.josm.command.Command> allWayCommands = new java.util.ArrayList<>();
                
                for (Way way : originalWays) {
                    List<EastNorth> points = new ArrayList<>();
                    for (Node node : way.getNodes()) {
                        points.add(node.getEastNorth());
                    }
                    
                    boolean isClosed = way.isClosed() && points.size() > 2;
                    
                    List<EastNorth> result = LineSmoother.smoothLaplacian(
                        points, iterations, lambda, preserveEndpoints, preserveCorners, isClosed);
                    
                    List<Node> originalNodes = way.getNodes();
                    int newNodeCount = result.size();
                    int oldNodeCount = originalNodes.size();
                    
                    org.openstreetmap.josm.command.Command wayCmd = null;
                    
                    if (newNodeCount == oldNodeCount) {
                        java.util.Collection<org.openstreetmap.josm.command.Command> commands = new java.util.ArrayList<>();
                        for (int i = 0; i < newNodeCount; i++) {
                            Node node = originalNodes.get(i);
                            org.openstreetmap.josm.data.coor.LatLon newCoor = 
                                org.openstreetmap.josm.data.projection.ProjectionRegistry.getProjection().eastNorth2latlon(result.get(i));
                            if (!node.getCoor().equalsEpsilon(newCoor)) {
                                org.openstreetmap.josm.data.osm.Node newNode = new org.openstreetmap.josm.data.osm.Node(node);
                                newNode.setCoor(newCoor);
                                commands.add(new org.openstreetmap.josm.command.ChangeCommand(node, newNode));
                            }
                        }
                        if (!commands.isEmpty()) {
                            wayCmd = new org.openstreetmap.josm.command.SequenceCommand(
                                tr("Smoothing: way {0}", way.getId()), commands);
                        }
                    } else {
                        java.util.Collection<org.openstreetmap.josm.command.Command> commands = new java.util.ArrayList<>();
                        List<Node> newNodes = new ArrayList<>();
                        
                        for (EastNorth en : result) {
                            Node node = new Node(org.openstreetmap.josm.data.projection.ProjectionRegistry.getProjection().eastNorth2latlon(en));
                            commands.add(new org.openstreetmap.josm.command.AddCommand(ds, node));
                            newNodes.add(node);
                        }
                        
                        for (Node oldNode : originalNodes) {
                            if (!newNodes.contains(oldNode)) {
                                commands.add(new org.openstreetmap.josm.command.DeleteCommand(ds, oldNode));
                            }
                        }
                        
                        org.openstreetmap.josm.data.osm.Way newWay = new org.openstreetmap.josm.data.osm.Way(way);
                        newWay.setNodes(newNodes);
                        commands.add(new org.openstreetmap.josm.command.ChangeCommand(way, newWay));
                        
                        if (!commands.isEmpty()) {
                            wayCmd = new org.openstreetmap.josm.command.SequenceCommand(
                                tr("Smoothing: way {0}", way.getId()), commands);
                        }
                    }
                    
                    if (wayCmd != null) {
                        allWayCommands.add(wayCmd);
                    }
                }
                
                ds.beginUpdate();
                try {
                    if (!allWayCommands.isEmpty()) {
                        org.openstreetmap.josm.command.SequenceCommand mainCommand = new org.openstreetmap.josm.command.SequenceCommand(
                            tr("Laplacian smoothing ({0} lines)", originalWays.size()), allWayCommands);
                        org.openstreetmap.josm.data.UndoRedoHandler.getInstance().add(mainCommand);
                    }
                } finally {
                    ds.endUpdate();
                }
                
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(
                        MainApplication.getMainFrame(),
                        String.format(tr("Ready!\nProcessed lines: %d"), originalWays.size()),
                        tr("Laplacian smoothing"),
                        JOptionPane.INFORMATION_MESSAGE
                    );
                    dispose();
                });
                
                return null;
            }
        };
        
        worker.execute();
    }
}
