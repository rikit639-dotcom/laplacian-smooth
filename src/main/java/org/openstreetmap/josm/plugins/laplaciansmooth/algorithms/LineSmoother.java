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
package org.openstreetmap.josm.plugins.laplaciansmooth.algorithms;

import org.openstreetmap.josm.data.coor.EastNorth;
import java.util.*;
import java.util.function.BooleanSupplier;

public class LineSmoother {
    
    
    private static List<EastNorth> smoothLaplacianInternal(
            List<EastNorth> points,
            int iterations,
            double lambda,
            boolean preserveEndpoints,
            boolean preserveCorners,
            boolean useTaubin,
            boolean isClosed,
            BooleanSupplier cancelChecker,
            boolean returnNullOnCancel) {
        
        if (points.size() < 3 || iterations <= 0 || lambda <= 0) {
            return new ArrayList<>(points);
        }
        
        List<EastNorth> current = new ArrayList<>(points);
        double cornerAngleThreshold = preserveCorners ? Math.toRadians(45) : 0;
        double mu = -1.01 * lambda;
        
        for (int iter = 0; iter < iterations; iter++) {
            for (int step = 0; step < (useTaubin ? 2 : 1); step++) {
                if (cancelChecker.getAsBoolean()) {
                    return returnNullOnCancel ? null : points;
                }
                double currentLambda = (step == 0) ? lambda : mu;
                final List<EastNorth> currentRef = current;
                final int sIdx = isClosed ? 0 : (preserveEndpoints ? 1 : 0);
                final int eIdx = isClosed ? currentRef.size() : (preserveEndpoints ? currentRef.size() - 1 : currentRef.size());
                
                double[] newX = new double[currentRef.size()];
                double[] newY = new double[currentRef.size()];
                boolean[] fixed = new boolean[currentRef.size()];
                
                for (int i = 0; i < currentRef.size(); i++) {
                    if (i < sIdx || i >= eIdx || (preserveCorners && isCorner(currentRef, i, cornerAngleThreshold, isClosed))) {
                        fixed[i] = true;
                        newX[i] = currentRef.get(i).east();
                        newY[i] = currentRef.get(i).north();
                    } else {
                        EastNorth prev = getPrev(currentRef, i, isClosed);
                        EastNorth curr = currentRef.get(i);
                        EastNorth nextPoint = getNext(currentRef, i, isClosed);
                        double d1 = Math.hypot(curr.east() - prev.east(), curr.north() - prev.north());
                        double d2 = Math.hypot(curr.east() - nextPoint.east(), curr.north() - nextPoint.north());
                        double avgX, avgY;
                        if (d1 + d2 > 1e-10) {
                            double w1 = 1.0 / (d1 + 1e-9);
                            double w2 = 1.0 / (d2 + 1e-9);
                            avgX = (prev.east() * w1 + nextPoint.east() * w2) / (w1 + w2);
                            avgY = (prev.north() * w1 + nextPoint.north() * w2) / (w1 + w2);
                        } else {
                            avgX = curr.east();
                            avgY = curr.north();
                        }
                        newX[i] = curr.east() + currentLambda * (avgX - curr.east());
                        newY[i] = curr.north() + currentLambda * (avgY - curr.north());
                        fixed[i] = false;
                    }
                }
                
                List<EastNorth> testPoints = new ArrayList<>(currentRef.size());
                for (int i = 0; i < currentRef.size(); i++) {
                    testPoints.add(new EastNorth(newX[i], newY[i]));
                }
                
                if (hasSelfIntersection(testPoints, isClosed, cancelChecker)) {
                    double reducedLambda = currentLambda * 0.5;
                    for (int attempt = 0; attempt < 3; attempt++) {
                        if (cancelChecker.getAsBoolean()) return returnNullOnCancel ? null : points;
                        for (int i = sIdx; i < eIdx; i++) {
                            if (!fixed[i]) {
                                EastNorth prev = getPrev(currentRef, i, isClosed);
                                EastNorth curr = currentRef.get(i);
                                EastNorth nextPoint = getNext(currentRef, i, isClosed);
                                double d1 = Math.hypot(curr.east() - prev.east(), curr.north() - prev.north());
                                double d2 = Math.hypot(curr.east() - nextPoint.east(), curr.north() - nextPoint.north());
                                double avgX, avgY;
                                if (d1 + d2 > 1e-10) {
                                    double w1 = 1.0 / (d1 + 1e-9);
                                    double w2 = 1.0 / (d2 + 1e-9);
                                    avgX = (prev.east() * w1 + nextPoint.east() * w2) / (w1 + w2);
                                    avgY = (prev.north() * w1 + nextPoint.north() * w2) / (w1 + w2);
                                } else {
                                    avgX = curr.east();
                                    avgY = curr.north();
                                }
                                newX[i] = curr.east() + reducedLambda * (avgX - curr.east());
                                newY[i] = curr.north() + reducedLambda * (avgY - curr.north());
                            }
                        }
                        testPoints.clear();
                        for (int i = 0; i < currentRef.size(); i++) testPoints.add(new EastNorth(newX[i], newY[i]));
                        if (!hasSelfIntersection(testPoints, isClosed, cancelChecker)) break;
                        reducedLambda *= 0.5;
                    }
                }
                current = testPoints;
            }
        }
        
        if (cancelChecker.getAsBoolean()) {
            return returnNullOnCancel ? null : points;
        }
        return current;
    }
    
    public static List<EastNorth> smoothLaplacian(
            List<EastNorth> points,
            int iterations,
            double lambda,
            boolean preserveEndpoints,
            boolean preserveCorners,
            boolean useTaubin,
            boolean isClosed,
            BooleanSupplier checkCancelled) {
        return smoothLaplacianInternal(points, iterations, lambda, preserveEndpoints, preserveCorners, useTaubin, isClosed, checkCancelled, false);
    }
    
    
    public static List<EastNorth> smoothLaplacianWithProgress(
            List<EastNorth> points,
            int iterations,
            double lambda,
            boolean preserveEndpoints,
            boolean preserveCorners,
            boolean useTaubin,
            boolean isClosed,
            BooleanSupplier progressCallback) {
        return smoothLaplacianInternal(points, iterations, lambda, preserveEndpoints, preserveCorners, useTaubin, isClosed, progressCallback, true);
    }
    
    
    public static boolean hasSelfIntersection(List<EastNorth> points, boolean isClosed, BooleanSupplier checkCancelled) {
        int n = points.size();
        if (n < 4) return false;

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (EastNorth p : points) {
            minX = Math.min(minX, p.east()); minY = Math.min(minY, p.north());
            maxX = Math.max(maxX, p.east()); maxY = Math.max(maxY, p.north());
        }

        int gridSize = (int) Math.sqrt(n);
        double cellW = (maxX - minX) / gridSize + 1e-9;
        double cellH = (maxY - minY) / gridSize + 1e-9;
        List<Integer>[][] grid = new List[gridSize + 1][gridSize + 1];

        int endIdx = isClosed ? n : n - 1;
        for (int i = 0; i < endIdx; i++) {
            if (i % 1000 == 0 && checkCancelled.getAsBoolean()) return false;
            EastNorth p1 = points.get(i);
            EastNorth p2 = points.get((i + 1) % n);
            int x1 = (int)((Math.min(p1.east(), p2.east()) - minX) / cellW);
            int y1 = (int)((Math.min(p1.north(), p2.north()) - minY) / cellH);
            int x2 = (int)((Math.max(p1.east(), p2.east()) - minX) / cellW);
            int y2 = (int)((Math.max(p1.north(), p2.north()) - minY) / cellH);

            for (int gx = x1; gx <= x2; gx++) {
                for (int gy = y1; gy <= y2; gy++) {
                    if (grid[gx][gy] == null) grid[gx][gy] = new ArrayList<>();
                    for (int otherIdx : grid[gx][gy]) {
                        if (Math.abs(i - otherIdx) <= 1) continue;
                        if (isClosed && ((i == n - 1 && otherIdx == 0) || (i == 0 && otherIdx == n - 1))) continue;
                        if (segmentsIntersect(p1, p2, points.get(otherIdx), points.get((otherIdx + 1) % n))) return true;
                    }
                    grid[gx][gy].add(i);
                }
            }
        }
        return false;
    }
    
    private static boolean segmentsIntersect(EastNorth a1, EastNorth a2, EastNorth b1, EastNorth b2) {
        double o1 = orientation(a1, a2, b1);
        double o2 = orientation(a1, a2, b2);
        double o3 = orientation(b1, b2, a1);
        double o4 = orientation(b1, b2, a2);
        
        if (o1 != o2 && o3 != o4) return true;
        
        if (o1 == 0 && onSegment(a1, b1, a2)) return true;
        if (o2 == 0 && onSegment(a1, b2, a2)) return true;
        if (o3 == 0 && onSegment(b1, a1, b2)) return true;
        if (o4 == 0 && onSegment(b1, a2, b2)) return true;
        
        return false;
    }
    
    private static double orientation(EastNorth p, EastNorth q, EastNorth r) {
        double val = (q.east() - p.east()) * (r.north() - p.north()) - 
                     (q.north() - p.north()) * (r.east() - p.east());
        if (Math.abs(val) < 1e-10) return 0;
        return val > 0 ? 1 : -1;
    }
    
    private static boolean onSegment(EastNorth p, EastNorth q, EastNorth r) {
        return q.east() <= Math.max(p.east(), r.east()) && q.east() >= Math.min(p.east(), r.east()) &&
               q.north() <= Math.max(p.north(), r.north()) && q.north() >= Math.min(p.north(), r.north());
    }
    
    
    public static boolean hasIntersectionWithOtherLines(
            List<EastNorth> points,
            List<List<EastNorth>> otherLines,
            Set<Integer> fixedNodeIndices,
            boolean isClosed) {
        
        for (List<EastNorth> otherLine : otherLines) {
            if (hasIntersectionBetweenLines(points, otherLine, fixedNodeIndices, isClosed, false)) {
                return true;
            }
        }
        return false;
    }
    
    public static boolean hasIntersectionBetweenLines(
            List<EastNorth> line1,
            List<EastNorth> line2,
            Set<Integer> fixedNodesInLine1,
            boolean line1Closed,
            boolean line2Closed) {
        
        int n1 = line1.size();
        int n2 = line2.size();
        int end1 = line1Closed ? n1 : n1 - 1;
        int end2 = line2Closed ? n2 : n2 - 1;
        
        for (int i = 0; i < end1; i++) {
            EastNorth a1 = line1.get(i);
            EastNorth a2 = line1.get((i + 1) % n1);
            
            for (int j = 0; j < end2; j++) {
                EastNorth b1 = line2.get(j);
                EastNorth b2 = line2.get((j + 1) % n2);
                
                boolean isSharedNode = false;
                if (fixedNodesInLine1 != null) {
                    for (int idx : fixedNodesInLine1) {
                        if (idx < line1.size()) {
                            EastNorth node = line1.get(idx);
                            if (pointsEqual(node, b1) || pointsEqual(node, b2)) {
                                isSharedNode = true;
                                break;
                            }
                        }
                    }
                }
                
                if (!isSharedNode && segmentsIntersect(a1, a2, b1, b2)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private static boolean pointsEqual(EastNorth p1, EastNorth p2) {
        return Math.hypot(p1.east() - p2.east(), p1.north() - p2.north()) < 1e-8;
    }
    
    
    private static EastNorth getPrev(List<EastNorth> points, int idx, boolean isClosed) {
        if (idx > 0) {
            return points.get(idx - 1);
        } else if (isClosed && points.size() > 0) {
            return points.get(points.size() - 1);
        }
        return points.get(idx);
    }
    
    private static EastNorth getNext(List<EastNorth> points, int idx, boolean isClosed) {
        if (idx < points.size() - 1) {
            return points.get(idx + 1);
        } else if (isClosed && points.size() > 0) {
            return points.get(0);
        }
        return points.get(idx);
    }
    
    private static boolean isCorner(List<EastNorth> points, int idx, double angleThreshold, boolean isClosed) {
        if (angleThreshold <= 0) return false;
        
        EastNorth prev = getPrev(points, idx, isClosed);
        EastNorth curr = points.get(idx);
        EastNorth next = getNext(points, idx, isClosed);
        
        double angle = calculateAngle(prev, curr, next);
        return Math.abs(angle - Math.PI) > angleThreshold;
    }
    
    private static double calculateAngle(EastNorth a, EastNorth b, EastNorth c) {
        double abX = a.east() - b.east();
        double abY = a.north() - b.north();
        double cbX = c.east() - b.east();
        double cbY = c.north() - b.north();
        
        double dot = abX * cbX + abY * cbY;
        double magAB = Math.hypot(abX, abY);
        double magCB = Math.hypot(cbX, cbY);
        
        if (magAB == 0 || magCB == 0) return Math.PI;
        
        double cos = dot / (magAB * magCB);
        cos = Math.max(-1, Math.min(1, cos));
        
        return Math.acos(cos);
    }
}
