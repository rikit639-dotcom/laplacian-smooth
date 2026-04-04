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

public class LineSmoother {
    
    
    public static List<EastNorth> smoothLaplacian(
            List<EastNorth> points,
            int iterations,
            double lambda,
            boolean preserveEndpoints,
            boolean preserveCorners,
            boolean isClosed) {
        
        if (points.size() < 3 || iterations <= 0 || lambda <= 0) {
            return new ArrayList<>(points);
        }
        
        List<EastNorth> current = new ArrayList<>(points);
        double cornerAngleThreshold = preserveCorners ? Math.toRadians(45) : 0;
        
        for (int iter = 0; iter < iterations; iter++) {
            List<EastNorth> next = new ArrayList<>(current);
            
            int startIdx;
            int endIdx;
            if (isClosed) {
                startIdx = 0;
                endIdx = current.size();
            } else {
                startIdx = preserveEndpoints ? 1 : 0;
                endIdx = preserveEndpoints ? current.size() - 1 : current.size();
            }
            
            double[] newX = new double[current.size()];
            double[] newY = new double[current.size()];
            boolean[] fixed = new boolean[current.size()];
            
            for (int i = startIdx; i < endIdx; i++) {
                if (preserveCorners && isCorner(current, i, cornerAngleThreshold, isClosed)) {
                    fixed[i] = true;
                    newX[i] = current.get(i).east();
                    newY[i] = current.get(i).north();
                    continue;
                }
                
                EastNorth prev = getPrev(current, i, isClosed);
                EastNorth curr = current.get(i);
                EastNorth nextPoint = getNext(current, i, isClosed);
                
                double laplacianX = (prev.east() + nextPoint.east()) / 2.0 - curr.east();
                double laplacianY = (prev.north() + nextPoint.north()) / 2.0 - curr.north();
                
                newX[i] = curr.east() + lambda * laplacianX;
                newY[i] = curr.north() + lambda * laplacianY;
                fixed[i] = false;
            }
            
            if (!isClosed && preserveEndpoints && current.size() > 0) {
                fixed[0] = true;
                newX[0] = current.get(0).east();
                newY[0] = current.get(0).north();
                if (current.size() > 1) {
                    fixed[current.size() - 1] = true;
                    newX[current.size() - 1] = current.get(current.size() - 1).east();
                    newY[current.size() - 1] = current.get(current.size() - 1).north();
                }
            }
            
            boolean collision = false;
            List<EastNorth> testPoints = new ArrayList<>(current.size());
            for (int i = 0; i < current.size(); i++) {
                if (fixed[i]) {
                    testPoints.add(current.get(i));
                } else {
                    testPoints.add(new EastNorth(newX[i], newY[i]));
                }
            }
            
            if (hasSelfIntersection(testPoints, isClosed)) {
                collision = true;
                double reducedLambda = lambda * 0.5;
                for (int attempt = 0; attempt < 3 && collision; attempt++) {
                    for (int i = startIdx; i < endIdx; i++) {
                        if (!fixed[i] && !(preserveCorners && isCorner(current, i, cornerAngleThreshold, isClosed))) {
                            EastNorth prev = getPrev(current, i, isClosed);
                            EastNorth curr = current.get(i);
                            EastNorth nextPoint = getNext(current, i, isClosed);
                            
                            double laplacianX = (prev.east() + nextPoint.east()) / 2.0 - curr.east();
                            double laplacianY = (prev.north() + nextPoint.north()) / 2.0 - curr.north();
                            
                            newX[i] = curr.east() + reducedLambda * laplacianX;
                            newY[i] = curr.north() + reducedLambda * laplacianY;
                        }
                    }
                    testPoints.clear();
                    for (int i = 0; i < current.size(); i++) {
                        if (fixed[i]) {
                            testPoints.add(current.get(i));
                        } else {
                            testPoints.add(new EastNorth(newX[i], newY[i]));
                        }
                    }
                    collision = hasSelfIntersection(testPoints, isClosed);
                    reducedLambda *= 0.5;
                }
            }
            
            for (int i = 0; i < current.size(); i++) {
                if (!fixed[i]) {
                    next.set(i, new EastNorth(newX[i], newY[i]));
                }
            }
            
            current = next;
        }
        
        return current;
    }
    
    
    public static boolean hasSelfIntersection(List<EastNorth> points, boolean isClosed) {
        if (points.size() < 4) return false;
        
        int n = points.size();
        int endIdx = isClosed ? n : n - 1;
        
        for (int i = 0; i < endIdx; i++) {
            EastNorth a1 = points.get(i);
            EastNorth a2 = points.get((i + 1) % n);
            
            for (int j = i + 2; j < endIdx; j++) {
                if (isClosed && j == i + 1) continue;
                if (!isClosed && j == i + 1) continue;
                
                EastNorth b1 = points.get(j);
                EastNorth b2 = points.get((j + 1) % n);
                
                if (segmentsIntersect(a1, a2, b1, b2)) {
                    return true;
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
