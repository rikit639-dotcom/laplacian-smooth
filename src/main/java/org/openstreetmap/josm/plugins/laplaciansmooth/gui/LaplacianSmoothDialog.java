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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
import java.util.concurrent.atomic.AtomicLong;

import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.trc;

public class LaplacianSmoothDialog extends JDialog {
    
    private final List<Way> originalWays;
    
    private JSlider iterationsSlider;
    private JLabel iterationsValueLabel;
    private JSlider lambdaSlider;
    private JLabel lambdaValueLabel;
    private JCheckBox preserveEndpointsCheckBox;
    private JCheckBox preserveCornersCheckBox;
    private JLabel cornerAngleLabel;
    private JSlider cornerAngleSlider;
    private AnglePreviewComponent cornerAnglePreview;
    private JCheckBox taubinCheckBox;

    private static class AnglePreviewComponent extends JComponent {
        private int angleDeg = 45;

        public AnglePreviewComponent() {
            setPreferredSize(new Dimension(24, 24));
            setMinimumSize(new Dimension(24, 24));
            setMaximumSize(new Dimension(24, 24));
        }

        public void setAngle(int deg) {
            this.angleDeg = deg;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            double cx = w * 0.45;
            double cy = h * 0.75;
            double r = Math.min(w, h) * 0.42;

            Color col = isEnabled() ? new Color(30, 136, 229) : UIManager.getColor("Label.disabledForeground");
            if (col == null) col = Color.GRAY;
            g2.setColor(col);
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            double x1 = cx - r;
            double y1 = cy;
            double rad = Math.toRadians(angleDeg);
            double x2 = cx + r * Math.cos(Math.PI - rad);
            double y2 = cy - r * Math.sin(Math.PI - rad);

            java.awt.geom.Path2D path = new java.awt.geom.Path2D.Double();
            path.moveTo(x1, y1);
            path.lineTo(cx, cy);
            path.lineTo(x2, y2);
            g2.draw(path);

            g2.setColor(isEnabled() ? new Color(229, 57, 53) : col);
            g2.fill(new java.awt.geom.Ellipse2D.Double(cx - 2, cy - 2, 4, 4));
            g2.dispose();
        }
    }
    private JCheckBox protectSpecialPointsCheckBox;
    private JCheckBox increaseDensityCheckBox;
    private JSpinner stepSpinner;
    private JCheckBox autoStepCheckBox;
    
    public LaplacianSmoothDialog(List<Way> ways) {
        super(MainApplication.getMainFrame(), tr("Laplacian smoothing"), true);
        this.originalWays = ways;
        
        setLayout(new BorderLayout());
        setResizable(false);
        setMinimumSize(new Dimension(480, 400));
        
        initComponents();
        pack();
        setLocationRelativeTo(MainApplication.getMainFrame());
        loadSettings();
    }
    
    private void initComponents() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        
        JPanel presetPanel = new JPanel(new GridLayout(1, 3, 6, 0));
        presetPanel.setBorder(BorderFactory.createTitledBorder(tr("Presets")));
        presetPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JButton weakPresetButton = new JButton(trc("preset", "Weak"));
        JButton mediumPresetButton = new JButton(trc("preset", "Medium"));
        JButton strongPresetButton = new JButton(trc("preset", "Strong"));
        
        weakPresetButton.addActionListener(e -> setPreset(2, 0.3));
        mediumPresetButton.addActionListener(e -> setPreset(5, 0.5));
        strongPresetButton.addActionListener(e -> setPreset(10, 0.7));
        
        presetPanel.add(weakPresetButton);
        presetPanel.add(mediumPresetButton);
        presetPanel.add(strongPresetButton);
        
        JPanel iterationsPanel = new JPanel(new BorderLayout(10, 5));
        iterationsPanel.setBorder(BorderFactory.createTitledBorder(tr("Iterations")));
        iterationsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
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
        lambdaPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
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
        optionsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        preserveEndpointsCheckBox = new JCheckBox(tr("Preserve endpoints"));
        
        preserveCornersCheckBox = new JCheckBox(tr("Keep corners <"));
        cornerAngleLabel = new JLabel("45°");
        cornerAngleLabel.setPreferredSize(new Dimension(34, 24));
        cornerAngleSlider = new JSlider(15, 120, 45);
        cornerAngleSlider.setPreferredSize(new Dimension(80, 24));
        cornerAngleSlider.setMaximumSize(new Dimension(80, 24));
        cornerAnglePreview = new AnglePreviewComponent();
        cornerAnglePreview.setAngle(45);
        
        cornerAngleSlider.addChangeListener(e -> {
            int val = (cornerAngleSlider.getValue() / 5) * 5;
            cornerAngleLabel.setText(val + "°");
            cornerAnglePreview.setAngle(val);
        });
        
        JPanel cornerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        cornerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        cornerRow.add(preserveCornersCheckBox);
        cornerRow.add(Box.createHorizontalStrut(6));
        cornerRow.add(cornerAngleLabel);
        cornerRow.add(Box.createHorizontalStrut(4));
        cornerRow.add(cornerAngleSlider);
        cornerRow.add(Box.createHorizontalStrut(8));
        cornerRow.add(cornerAnglePreview);

        preserveCornersCheckBox.addActionListener(e -> {
            boolean enabled = preserveCornersCheckBox.isSelected();
            cornerAngleLabel.setEnabled(enabled);
            cornerAngleSlider.setEnabled(enabled);
            cornerAnglePreview.setEnabled(enabled);
        });

        taubinCheckBox = new JCheckBox(tr("Preserve shape volume (Taubin method)"));
        protectSpecialPointsCheckBox = new JCheckBox(tr("Protect special points (junctions, tagged nodes)"));
        
        increaseDensityCheckBox = new JCheckBox(tr("Increase node density"));
        JLabel stepLabel = new JLabel(tr("Step (m)"));
        stepSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 1000.0, 0.1));
        setupAutoClamp(stepSpinner, 0.1, 1000.0);
        stepSpinner.setMaximumSize(new Dimension(80, 24));
        autoStepCheckBox = new JCheckBox(tr("Auto"));
        
        JPanel densityRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        densityRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        densityRow.add(increaseDensityCheckBox);
        densityRow.add(Box.createHorizontalStrut(10));
        densityRow.add(stepLabel);
        densityRow.add(Box.createHorizontalStrut(5));
        densityRow.add(stepSpinner);
        densityRow.add(Box.createHorizontalStrut(8));
        densityRow.add(autoStepCheckBox);
        
        for (JComponent c : new JComponent[]{preserveEndpointsCheckBox, cornerRow, taubinCheckBox, protectSpecialPointsCheckBox}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
            optionsPanel.add(c);
        }
        densityRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, densityRow.getPreferredSize().height));
        optionsPanel.add(densityRow);

        preserveEndpointsCheckBox.setSelected(true);
        preserveCornersCheckBox.setSelected(false);
        cornerAngleLabel.setEnabled(false);
        cornerAngleSlider.setEnabled(false);
        cornerAnglePreview.setEnabled(false);
        taubinCheckBox.setSelected(false);
        protectSpecialPointsCheckBox.setSelected(true);
        increaseDensityCheckBox.setSelected(false);
        autoStepCheckBox.setSelected(false);
        autoStepCheckBox.setEnabled(false);
        stepSpinner.setEnabled(false);
        
        increaseDensityCheckBox.addActionListener(e -> {
            boolean enabled = increaseDensityCheckBox.isSelected();
            autoStepCheckBox.setEnabled(enabled);
            stepSpinner.setEnabled(enabled && !autoStepCheckBox.isSelected());
        });
        autoStepCheckBox.addActionListener(e -> {
            stepSpinner.setEnabled(increaseDensityCheckBox.isSelected() && !autoStepCheckBox.isSelected());
        });
        
        JPanel buttonPanel = new JPanel(new GridLayout(1, 4, 8, 0));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));
        JButton resetButton = new JButton(tr("Reset"));
        JButton applyButton = new JButton(tr("Apply"));
        JButton saveButton = new JButton(tr("Save"));
        JButton cancelButton = new JButton(tr("Cancel"));
        
        resetButton.addActionListener(this::resetToDefaults);
        applyButton.addActionListener(this::applyChanges);
        saveButton.addActionListener(this::saveSettings);
        cancelButton.addActionListener(e -> dispose());
        
        buttonPanel.add(resetButton);
        buttonPanel.add(applyButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        
        presetPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, presetPanel.getPreferredSize().height));
        iterationsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, iterationsPanel.getPreferredSize().height));
        lambdaPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, lambdaPanel.getPreferredSize().height));
        optionsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, optionsPanel.getPreferredSize().height));
        
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
        int cornerAngle = Integer.parseInt(prefs.get("laplacian_smooth.corner_angle", "45"));
        boolean useTaubin = Boolean.parseBoolean(prefs.get("laplacian_smooth.use_taubin", "false"));
        boolean protectSpecial = Boolean.parseBoolean(prefs.get("laplacian_smooth.protect_special", "true"));
        boolean increaseDensity = Boolean.parseBoolean(prefs.get("laplacian_smooth.increase_density", "false"));
        boolean autoStep = Boolean.parseBoolean(prefs.get("laplacian_smooth.auto_step", "false"));
        double step = Double.parseDouble(prefs.get("laplacian_smooth.step", "1.0"));
        
        iterationsSlider.setValue(iterations);
        lambdaSlider.setValue((int)(lambda * 100));
        preserveEndpointsCheckBox.setSelected(preserveEndpoints);
        preserveCornersCheckBox.setSelected(preserveCorners);
        cornerAngleSlider.setValue(cornerAngle);
        cornerAngleLabel.setText(cornerAngle + "°");
        cornerAnglePreview.setAngle(cornerAngle);
        cornerAngleLabel.setEnabled(preserveCorners);
        cornerAngleSlider.setEnabled(preserveCorners);
        cornerAnglePreview.setEnabled(preserveCorners);
        taubinCheckBox.setSelected(useTaubin);
        protectSpecialPointsCheckBox.setSelected(protectSpecial);
        increaseDensityCheckBox.setSelected(increaseDensity);
        autoStepCheckBox.setSelected(autoStep);
        autoStepCheckBox.setEnabled(increaseDensity);
        stepSpinner.setValue(step);
        stepSpinner.setEnabled(increaseDensity && !autoStep);
    }
    
    private void resetToDefaults(ActionEvent e) {
        iterationsSlider.setValue(5);
        lambdaSlider.setValue(50);
        preserveEndpointsCheckBox.setSelected(true);
        preserveCornersCheckBox.setSelected(false);
        cornerAngleSlider.setValue(45);
        cornerAngleLabel.setText("45°");
        cornerAnglePreview.setAngle(45);
        cornerAngleLabel.setEnabled(false);
        cornerAngleSlider.setEnabled(false);
        cornerAnglePreview.setEnabled(false);
        taubinCheckBox.setSelected(false);
        protectSpecialPointsCheckBox.setSelected(true);
        increaseDensityCheckBox.setSelected(false);
        autoStepCheckBox.setSelected(false);
        autoStepCheckBox.setEnabled(false);
        stepSpinner.setValue(1.0);
        stepSpinner.setEnabled(false);
    }
    
    private void saveSettings(ActionEvent e) {
        try {
            stepSpinner.commitEdit();
        } catch (Exception ignored) {}
        org.openstreetmap.josm.spi.preferences.IPreferences prefs = Config.getPref();
        
        prefs.put("laplacian_smooth.iterations", String.valueOf(iterationsSlider.getValue()));
        prefs.put("laplacian_smooth.lambda", String.valueOf(lambdaSlider.getValue() / 100.0));
        prefs.put("laplacian_smooth.preserve_endpoints", String.valueOf(preserveEndpointsCheckBox.isSelected()));
        prefs.put("laplacian_smooth.preserve_corners", String.valueOf(preserveCornersCheckBox.isSelected()));
        prefs.put("laplacian_smooth.corner_angle", String.valueOf((cornerAngleSlider.getValue() / 5) * 5));
        prefs.put("laplacian_smooth.use_taubin", String.valueOf(taubinCheckBox.isSelected()));
        prefs.put("laplacian_smooth.protect_special", String.valueOf(protectSpecialPointsCheckBox.isSelected()));
        prefs.put("laplacian_smooth.increase_density", String.valueOf(increaseDensityCheckBox.isSelected()));
        prefs.put("laplacian_smooth.auto_step", String.valueOf(autoStepCheckBox.isSelected()));
        prefs.put("laplacian_smooth.step", String.valueOf(stepSpinner.getValue()));
        
        JOptionPane.showMessageDialog(this, 
            tr("Settings saved by default"), 
            tr("Laplacian smoothing"), 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void applyChanges(ActionEvent e) {
        try {
            stepSpinner.commitEdit();
        } catch (Exception ignored) {}
        int iterations = iterationsSlider.getValue();
        double lambda = lambdaSlider.getValue() / 100.0;
        boolean preserveEndpoints = preserveEndpointsCheckBox.isSelected();
        boolean preserveCorners = preserveCornersCheckBox.isSelected();
        double cornerAngleThreshold = Math.toRadians((cornerAngleSlider.getValue() / 5) * 5);
        boolean useTaubin = taubinCheckBox.isSelected();
        boolean protectSpecial = protectSpecialPointsCheckBox.isSelected();
        boolean increaseDensity = increaseDensityCheckBox.isSelected();
        boolean autoStep = autoStepCheckBox.isSelected();
        double step = (Double) stepSpinner.getValue();
        
        setVisible(false);

        JDialog pd = new JDialog(MainApplication.getMainFrame(), tr("Laplacian smoothing"), false);
        pd.setResizable(false);
        pd.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
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
        
        final AtomicLong processedPoints = new AtomicLong(0);
        final AtomicLong totalUnits = new AtomicLong(0);
        
        SwingWorker<BulkMoveCommand, Integer> worker = new SwingWorker<BulkMoveCommand, Integer>() {
            @Override
            protected BulkMoveCommand doInBackground() {
                DataSet ds = MainApplication.getLayerManager().getEditDataSet();
                if (ds != null) {
                    ds.setSelected(new java.util.ArrayList<>(originalWays));
                }
                java.util.Map<Node, org.openstreetmap.josm.data.coor.LatLon> nodeMoves = new java.util.concurrent.ConcurrentHashMap<>();
                java.util.Map<Way, List<Node>> newWayNodesMap = new java.util.concurrent.ConcurrentHashMap<>();
                java.util.Set<Node> createdNodes = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

                if (increaseDensity && autoStep) {
                    for (Way way : originalWays) {
                        totalUnits.addAndGet((long) way.getNodesCount() * iterations);
                    }
                    if (totalUnits.get() == 0) totalUnits.set(1);

                    for (int wayIdx = 0; wayIdx < originalWays.size(); wayIdx++) {
                        if (isCancelled()) return null;
                        Way way = originalWays.get(wayIdx);
                        List<Node> origNodes = way.getNodes();
                        List<EastNorth> basePoints = new ArrayList<>(origNodes.size());
                        for (Node node : origNodes) basePoints.add(node.getEastNorth());

                        java.util.Set<Integer> fixedExtra = new java.util.HashSet<>();
                        for (int i = 0; i < origNodes.size(); i++) {
                            Node n = origNodes.get(i);
                            if (protectSpecial && (n.isTagged() || n.getParentWays().size() > 1)) {
                                fixedExtra.add(i);
                            }
                        }

                        int numBase = way.isClosed() ? origNodes.size() - 1 : origNodes.size();
                        int passes = Math.min(4, Math.max(2, (int) Math.round(iterations / 2.0)));
                        LineSmoother.ChaikinResult chaikin = LineSmoother.subdivideChaikinTracked(
                            basePoints, way.isClosed(), passes, preserveEndpoints, preserveCorners, cornerAngleThreshold, useTaubin, fixedExtra
                        );

                        List<EastNorth> smoothPoints = chaikin.points;
                        int[] nodeMap = chaikin.nodeIndexMapping;

                        List<Node> finalWayNodes = new ArrayList<>();
                        int distinctSmooth = way.isClosed() ? smoothPoints.size() - 1 : smoothPoints.size();
                        java.util.Set<Integer> assignedOrig = new java.util.HashSet<>();

                        for (int i = 0; i < distinctSmooth; i++) {
                            EastNorth pt = smoothPoints.get(i);
                            org.openstreetmap.josm.data.coor.LatLon ll = org.openstreetmap.josm.data.projection.ProjectionRegistry.getProjection().eastNorth2latlon(pt);
                            int origIdx = nodeMap[i];

                            if (origIdx >= 0 && origIdx < origNodes.size() && !assignedOrig.contains(origIdx)) {
                                Node origNode = origNodes.get(origIdx);
                                nodeMoves.put(origNode, ll);
                                finalWayNodes.add(origNode);
                                assignedOrig.add(origIdx);
                            } else {
                                Node newNode = new Node(ll);
                                createdNodes.add(newNode);
                                finalWayNodes.add(newNode);
                            }
                        }

                        int distinctOrig = way.isClosed() ? origNodes.size() - 1 : origNodes.size();
                        for (int i = 0; i < distinctOrig; i++) {
                            if (!assignedOrig.contains(i)) {
                                Node origNode = origNodes.get(i);
                                nodeMoves.put(origNode, origNode.getCoor());
                            }
                        }

                        if (way.isClosed() && !finalWayNodes.isEmpty()) {
                            finalWayNodes.add(finalWayNodes.get(0));
                        }
                        newWayNodesMap.put(way, finalWayNodes);
                        processedPoints.addAndGet((long) origNodes.size() * iterations);
                        publish((int) (processedPoints.get() * 100 / totalUnits.get()));
                    }
                } else {
                    List<List<Node>> allDensified = new ArrayList<>();
                    for (Way way : originalWays) {
                        List<Node> currentNodes = way.getNodes();
                        List<Node> densified = new ArrayList<>();
                        if (increaseDensity) {
                            for (int i = 0; i < currentNodes.size() - 1; i++) {
                                Node n1 = currentNodes.get(i); Node n2 = currentNodes.get(i + 1);
                                densified.add(n1);
                                double dist = n1.getEastNorth().distance(n2.getEastNorth());
                                if (dist > step * 0.5) {
                                    int parts = (int) Math.max(1, Math.round(dist / step));
                                    for (int k = 1; k < parts; k++) {
                                        double ratio = (double) k / parts;
                                        EastNorth en = new EastNorth(
                                            n1.getEastNorth().east() + (n2.getEastNorth().east() - n1.getEastNorth().east()) * ratio,
                                            n1.getEastNorth().north() + (n2.getEastNorth().north() - n1.getEastNorth().north()) * ratio
                                        );
                                        Node newNode = new Node(org.openstreetmap.josm.data.projection.ProjectionRegistry.getProjection().eastNorth2latlon(en));
                                        densified.add(newNode); createdNodes.add(newNode);
                                    }
                                }
                            }
                            densified.add(currentNodes.get(currentNodes.size() - 1));
                        } else {
                            densified.addAll(currentNodes);
                        }
                        allDensified.add(densified);
                        totalUnits.addAndGet((long) densified.size() * iterations);
                    }

                    if (totalUnits.get() == 0) totalUnits.set(1);

                    for (int wayIdx = 0; wayIdx < originalWays.size(); wayIdx++) {
                        if (isCancelled()) return null;
                        Way way = originalWays.get(wayIdx);
                        List<Node> densifiedNodes = allDensified.get(wayIdx);
                        List<EastNorth> points = new ArrayList<>(densifiedNodes.size());
                        for (Node node : densifiedNodes) points.add(node.getEastNorth());

                        java.util.Set<Integer> fixedExtra = new java.util.HashSet<>();
                        for (int i = 0; i < densifiedNodes.size(); i++) {
                            Node n = densifiedNodes.get(i);
                            boolean isOriginal = !createdNodes.contains(n);
                            if (protectSpecial && isOriginal && (n.isTagged() || n.getParentWays().size() > 1)) {
                                fixedExtra.add(i);
                            }
                        }

                        List<EastNorth> result = LineSmoother.smoothLaplacianWithProgress(
                            points, iterations, lambda, preserveEndpoints, preserveCorners, cornerAngleThreshold, useTaubin, way.isClosed(),
                            fixedExtra,
                            () -> {
                                long current = processedPoints.incrementAndGet();
                                publish((int) (current * 100 / totalUnits.get()));
                                return isCancelled();
                            }
                        );

                        if (result != null) {
                            for (int i = 0; i < densifiedNodes.size(); i++) {
                                nodeMoves.put(densifiedNodes.get(i), 
                                    org.openstreetmap.josm.data.projection.ProjectionRegistry.getProjection().eastNorth2latlon(result.get(i)));
                            }
                            newWayNodesMap.put(way, densifiedNodes);
                        }
                    }
                }

                Node[] nodes = nodeMoves.keySet().toArray(new Node[0]);
                org.openstreetmap.josm.data.coor.LatLon[] target = new org.openstreetmap.josm.data.coor.LatLon[nodes.length];
                for (int i = 0; i < nodes.length; i++) target[i] = nodeMoves.get(nodes[i]);

                Way[] ways = originalWays.toArray(new Way[0]);
                Node[][] densified = new Node[ways.length][];
                for (int i = 0; i < ways.length; i++) densified[i] = newWayNodesMap.get(ways[i]).toArray(new Node[0]);

                return isCancelled() ? null : new BulkMoveCommand(ds, nodes, target, ways, densified, createdNodes.toArray(new Node[0]));
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
        pd.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                worker.cancel(true);
            }
        });
        pd.setVisible(true);
        worker.execute();
    }

    private static class BulkMoveCommand extends org.openstreetmap.josm.command.Command {
        private final Node[] nodes;
        private final org.openstreetmap.josm.data.coor.LatLon[] newCoords, oldCoords;
        private final boolean[] oldNodesModified;
        private final Way[] ways;
        private final Node[][] newWaysNodes, oldWaysNodes;
        private final boolean[] oldWaysModified;
        private final Node[] createdNodes;
        private final boolean hasTopologyChanges;
        private final java.util.List<org.openstreetmap.josm.data.osm.OsmPrimitive> participatingPrimitives;
        private final java.util.List<org.openstreetmap.josm.data.osm.OsmPrimitive> precalcModified;
        private final java.util.List<org.openstreetmap.josm.data.osm.OsmPrimitive> precalcAdded;

        public BulkMoveCommand(DataSet ds, Node[] nodes, org.openstreetmap.josm.data.coor.LatLon[] target, 
                               Way[] ways, Node[][] densified, Node[] created) {
            super(ds);
            this.nodes = nodes;
            this.newCoords = target;
            this.ways = ways;
            this.newWaysNodes = densified;
            this.createdNodes = created;
            this.hasTopologyChanges = created.length > 0;
            this.oldCoords = new org.openstreetmap.josm.data.coor.LatLon[nodes.length];
            this.oldNodesModified = new boolean[nodes.length];
            for (int i = 0; i < nodes.length; i++) {
                oldCoords[i] = nodes[i].getCoor();
                oldNodesModified[i] = nodes[i].isModified();
            }
            this.oldWaysNodes = new Node[ways.length][];
            this.oldWaysModified = new boolean[ways.length];
            for (int i = 0; i < ways.length; i++) {
                oldWaysNodes[i] = ways[i].getNodes().toArray(new Node[0]);
                oldWaysModified[i] = ways[i].isModified();
            }

            java.util.List<org.openstreetmap.josm.data.osm.OsmPrimitive> allPrimitives = new java.util.ArrayList<>(ways.length + nodes.length);
            allPrimitives.addAll(java.util.Arrays.asList(ways));
            allPrimitives.addAll(java.util.Arrays.asList(nodes));
            this.participatingPrimitives = java.util.Collections.unmodifiableList(allPrimitives);

            java.util.List<org.openstreetmap.josm.data.osm.OsmPrimitive> mod = new java.util.ArrayList<>();
            if (hasTopologyChanges) {
                mod.addAll(java.util.Arrays.asList(ways));
                java.util.Set<Node> createdSet = new java.util.HashSet<>(java.util.Arrays.asList(createdNodes));
                for (Node n : nodes) {
                    if (!createdSet.contains(n)) mod.add(n);
                }
            } else {
                mod.addAll(java.util.Arrays.asList(nodes));
            }
            this.precalcModified = java.util.Collections.unmodifiableList(mod);
            this.precalcAdded = java.util.Collections.unmodifiableList(java.util.Arrays.asList(createdNodes));
        }

        @Override
        public boolean executeCommand() {
            return move(newCoords, newWaysNodes, true);
        }

        @Override
        public void undoCommand() {
            move(oldCoords, oldWaysNodes, false);
        }

        private boolean move(org.openstreetmap.josm.data.coor.LatLon[] coords, Node[][] wayNodesLists, boolean isExec) {
            DataSet ds = getAffectedDataSet();
            if (ds == null) return false;
            ds.beginUpdate();
            try {
                for (int i = 0; i < ways.length; i++) {
                    ways[i].setNodes(java.util.Collections.emptyList());
                }
                if (hasTopologyChanges) {
                    if (isExec) {
                        for (Node n : createdNodes) {
                            if (n.getDataSet() == null) ds.addPrimitive(n);
                            n.setDeleted(false);
                        }
                    }
                }
                for (int i = 0; i < nodes.length; i++) {
                    nodes[i].setCoor(coords[i]);
                    nodes[i].setModified(isExec || oldNodesModified[i]);
                }
                for (int i = 0; i < ways.length; i++) {
                    ways[i].setNodes(java.util.Arrays.asList(wayNodesLists[i]));
                    ways[i].setModified(hasTopologyChanges ? (isExec || oldWaysModified[i]) : oldWaysModified[i]);
                }
                if (hasTopologyChanges && !isExec) {
                    for (Node n : createdNodes) {
                        if (n.getDataSet() != null) n.setDeleted(true);
                    }
                }
            } finally {
                ds.endUpdate();
            }
            return true;
        }

        @Override
        public void fillModifiedData(java.util.Collection<org.openstreetmap.josm.data.osm.OsmPrimitive> m,
                                     java.util.Collection<org.openstreetmap.josm.data.osm.OsmPrimitive> d,
                                     java.util.Collection<org.openstreetmap.josm.data.osm.OsmPrimitive> a) {
            if (m != null) m.addAll(precalcModified);
            if (a != null) a.addAll(precalcAdded);
        }

        @Override
        public String getDescriptionText() {
            return tr("Laplacian smoothing ({0} lines)", ways.length);
        }

        @Override
        public java.util.Collection<org.openstreetmap.josm.data.osm.OsmPrimitive> getParticipatingPrimitives() {
            return participatingPrimitives;
        }
    }

    private void setupAutoClamp(JSpinner spinner, double min, double max) {
        JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) spinner.getEditor();
        JFormattedTextField field = editor.getTextField();
        javax.swing.Timer timer = new javax.swing.Timer(1000, e -> {
            try {
                String text = field.getText().trim().replace(',', '.');
                if (!text.isEmpty()) {
                    double dVal = Double.parseDouble(text);
                    if (dVal < min) {
                        spinner.setValue(min);
                    } else if (dVal > max) {
                        spinner.setValue(max);
                    } else {
                        spinner.setValue(dVal);
                    }
                }
            } catch (Exception ex) {
                try { spinner.commitEdit(); } catch (Exception ignored) {}
            }
        });
        timer.setRepeats(false);
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void trigger() { timer.restart(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { trigger(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { trigger(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { trigger(); }
        });
    }
}
