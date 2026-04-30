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
            Set<Integer> fixedExtra,
            BooleanSupplier cancelChecker,
            boolean returnNullOnCancel) {

        if (points.size() < 3 || iterations <= 0 || lambda <= 0) {
            return new ArrayList<>(points);
        }

        double offE = points.get(0).east();
        double offN = points.get(0).north();
        List<EastNorth> current = new ArrayList<>(points.size());
        for (EastNorth p : points) {
            current.add(new EastNorth(p.east() - offE, p.north() - offN));
        }

        double cornerAngleThreshold = preserveCorners ? Math.toRadians(45) : 0;
        double mu = -1.01 * lambda;
        int n = current.size();

        for (int iter = 0; iter < iterations; iter++) {
            if (cancelChecker.getAsBoolean()) return returnNullOnCancel ? null : points;

            List<EastNorth> iterationStart = current;
            int intersectionsBefore = countSelfIntersections(iterationStart, isClosed, cancelChecker);

            List<EastNorth> step1 = applyLaplacianStep(iterationStart, lambda, preserveEndpoints, preserveCorners, cornerAngleThreshold, isClosed, fixedExtra);
            List<EastNorth> iterationEnd = useTaubin ? applyLaplacianStep(step1, mu, preserveEndpoints, preserveCorners, cornerAngleThreshold, isClosed, fixedExtra) : step1;

            double factor = 1.0;
            boolean success = false;
            List<EastNorth> testPoints = null;

            for (int attempt = 0; attempt < 5; attempt++) {
                testPoints = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    EastNorth startP = iterationStart.get(i);
                    EastNorth endP = iterationEnd.get(i);
                    
                    double dx = (endP.east() - startP.east()) * factor;
                    double dy = (endP.north() - startP.north()) * factor;

                    double distPrev = Math.hypot(startP.east() - getPrev(iterationStart, i, isClosed).east(), 
                                                 startP.north() - getPrev(iterationStart, i, isClosed).north());
                    double distNext = Math.hypot(startP.east() - getNext(iterationStart, i, isClosed).east(), 
                                                 startP.north() - getNext(iterationStart, i, isClosed).north());
                    
                    double safeLimit = Math.max(0.05, Math.min(distPrev, distNext) * 0.45);
                    double moveDist = Math.hypot(dx, dy);
                    
                    if (moveDist > safeLimit && moveDist > 1e-11) {
                        double ratio = safeLimit / moveDist;
                        dx *= ratio;
                        dy *= ratio;
                    }
                    testPoints.add(new EastNorth(startP.east() + dx, startP.north() + dy));
                }

                if (countSelfIntersections(testPoints, isClosed, cancelChecker) <= intersectionsBefore) {
                    success = true;
                    break;
                }
                factor *= 0.5;
            }
            if (success) {
                current = testPoints;
            } else {
                current = step1; 
            }
        }

        List<EastNorth> result = new ArrayList<>(current.size());
        for (EastNorth p : current) {
            result.add(new EastNorth(p.east() + offE, p.north() + offN));
        }
        return result;
    }

    private static List<EastNorth> applyLaplacianStep(List<EastNorth> pts, double l, boolean pEnd, boolean pCor, double angleT, boolean closed, Set<Integer> fixedExtra) {
        int n = pts.size();
        List<EastNorth> next = new ArrayList<>(n);
        int sIdx = closed ? 0 : (pEnd ? 1 : 0);
        int eIdx = closed ? n : (pEnd ? n - 1 : n);

        for (int i = 0; i < n; i++) {
            if (i < sIdx || i >= eIdx || (pCor && isCorner(pts, i, angleT, closed)) || (fixedExtra != null && fixedExtra.contains(i))) {
                next.add(pts.get(i));
            } else {
                EastNorth prev = getPrev(pts, i, closed);
                EastNorth curr = pts.get(i);
                EastNorth nextP = getNext(pts, i, closed);
                double d1 = Math.hypot(curr.east() - prev.east(), curr.north() - prev.north());
                double d2 = Math.hypot(curr.east() - nextP.east(), curr.north() - nextP.north());
                double w1 = 1.0 / (d1 + 1e-9);
                double w2 = 1.0 / (d2 + 1e-9);
                double avgX = (prev.east() * w1 + nextP.east() * w2) / (w1 + w2);
                double avgY = (prev.north() * w1 + nextP.north() * w2) / (w1 + w2);
                next.add(new EastNorth(curr.east() + l * (avgX - curr.east()), curr.north() + l * (avgY - curr.north())));
            }
        }
        return next;
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
        return smoothLaplacianInternal(points, iterations, lambda, preserveEndpoints, preserveCorners, useTaubin, isClosed, null, checkCancelled, false);
    }
    
    
    public static List<EastNorth> smoothLaplacianWithProgress(
            List<EastNorth> points,
            int iterations,
            double lambda,
            boolean preserveEndpoints,
            boolean preserveCorners,
            boolean useTaubin,
            boolean isClosed,
            Set<Integer> fixedExtra,
            BooleanSupplier progressCallback) {
        return smoothLaplacianInternal(points, iterations, lambda, preserveEndpoints, preserveCorners, useTaubin, isClosed, fixedExtra, progressCallback, true);
    }
    

    public static boolean hasSelfIntersection(List<EastNorth> points, boolean isClosed, BooleanSupplier checkCancelled) {
        return countSelfIntersections(points, isClosed, checkCancelled) > 0;
    }

    public static int countSelfIntersections(List<EastNorth> points, boolean isClosed, BooleanSupplier checkCancelled) {
        int n = points.size();
        if (n < 4) return 0;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (EastNorth p : points) {
            minX = Math.min(minX, p.east()); minY = Math.min(minY, p.north());
            maxX = Math.max(maxX, p.east()); maxY = Math.max(maxY, p.north());
        }
        int gridSize = Math.min(500, Math.max(2, (int) Math.sqrt(n)));
        double cellW = (maxX - minX) / gridSize + 1e-9, cellH = (maxY - minY) / gridSize + 1e-9;
        int[] head = new int[gridSize * gridSize], next = new int[n], segIdx = new int[n];
        java.util.Arrays.fill(head, -1);
        int count = 0, endIdx = isClosed ? n : n - 1;
        for (int i = 0; i < endIdx; i++) {
            if ((i & 0x3FF) == 0 && checkCancelled.getAsBoolean()) return count;
            EastNorth p1 = points.get(i), p2 = points.get((i + 1) % n);
            double sMinX = Math.min(p1.east(), p2.east()), sMinY = Math.min(p1.north(), p2.north());
            double sMaxX = Math.max(p1.east(), p2.east()), sMaxY = Math.max(p1.north(), p2.north());
            int x1 = Math.max(0, Math.min(gridSize - 1, (int)((sMinX - minX) / cellW)));
            int y1 = Math.max(0, Math.min(gridSize - 1, (int)((sMinY - minY) / cellH)));
            int x2 = Math.max(0, Math.min(gridSize - 1, (int)((sMaxX - minX) / cellW)));
            int y2 = Math.max(0, Math.min(gridSize - 1, (int)((sMaxY - minY) / cellH)));
            for (int gx = x1; gx <= x2; gx++) {
                for (int gy = y1; gy <= y2; gy++) {
                    int hIdx = gx * gridSize + gy;
                    for (int curr = head[hIdx]; curr != -1; curr = next[curr]) {
                        int o = segIdx[curr];
                        if (Math.abs(i - o) <= 1 || (isClosed && ((i == n - 1 && o == 0) || (i == 0 && o == n - 1)))) continue;
                        if (segmentsIntersect(p1, p2, points.get(o), points.get((o + 1) % n))) count++;
                    }
                    if (count > 100) return count;
                }
            }
            int centerIdx = ((x1 + x2) / 2) * gridSize + ((y1 + y2) / 2);
            segIdx[i] = i; next[i] = head[centerIdx]; head[centerIdx] = i;
        }
        return count;
    }
    
    private static boolean segmentsIntersect(EastNorth a1, EastNorth a2, EastNorth b1, EastNorth b2) {
        double o1 = orientation(a1, a2, b1);
        double o2 = orientation(a1, a2, b2);
        double o3 = orientation(b1, b2, a1);
        double o4 = orientation(b1, b2, a2);

        if (((o1 > 0 && o2 < 0) || (o1 < 0 && o2 > 0)) && 
            ((o3 > 0 && o4 < 0) || (o3 < 0 && o4 > 0))) return true;

        if (o1 == 0 && onSegment(a1, b1, a2)) return true;
        if (o2 == 0 && onSegment(a1, b2, a2)) return true;
        if (o3 == 0 && onSegment(b1, a1, b2)) return true;
        if (o4 == 0 && onSegment(b1, a2, b2)) return true;

        return false;
    }

    private static double orientation(EastNorth p, EastNorth q, EastNorth r) {
        double val = (q.east() - p.east()) * (r.north() - p.north()) - 
                     (q.north() - p.north()) * (r.east() - p.east());
        double eps = 1e-11;
        return (val > eps) ? 1 : (val < -eps ? -1 : 0);
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
