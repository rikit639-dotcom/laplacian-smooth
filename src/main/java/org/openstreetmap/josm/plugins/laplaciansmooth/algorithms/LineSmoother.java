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
package org.openstreetmap.josm.plugins.laplaciansmooth.algorithms;

import org.openstreetmap.josm.data.coor.EastNorth;
import java.util.*;
import java.util.function.BooleanSupplier;

public class LineSmoother {
    

    private static Set<Integer> findIntersectingPointIndices(List<EastNorth> points, boolean isClosed, BooleanSupplier checkCancelled) {
        int n = points.size();
        Set<Integer> offending = new HashSet<>();
        if (n < 4) return offending;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (EastNorth p : points) {
            minX = Math.min(minX, p.east()); minY = Math.min(minY, p.north());
            maxX = Math.max(maxX, p.east()); maxY = Math.max(maxY, p.north());
        }
        int gridSize = Math.min(500, Math.max(2, (int) Math.sqrt(n)));
        double cellW = (maxX - minX) / gridSize + 1e-9, cellH = (maxY - minY) / gridSize + 1e-9;
        int[] head = new int[gridSize * gridSize], next = new int[n], segIdx = new int[n];
        java.util.Arrays.fill(head, -1);
        int intersectionCap = Math.max(100, n);
        int endIdx = n - 1;
        int found = 0;
        for (int i = 0; i < endIdx; i++) {
            if ((i & 0x3FF) == 0 && checkCancelled.getAsBoolean()) return offending;
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
                        if (segmentsIntersect(p1, p2, points.get(o), points.get((o + 1) % n))) {
                            offending.add(i);
                            offending.add((i + 1) % n);
                            offending.add(o);
                            offending.add((o + 1) % n);
                            found++;
                        }
                    }
                    if (found > intersectionCap) return offending;
                }
            }
            int centerIdx = ((x1 + x2) / 2) * gridSize + ((y1 + y2) / 2);
            segIdx[i] = i; next[i] = head[centerIdx]; head[centerIdx] = i;
        }
        return offending;
    }

    private static List<EastNorth> buildTestPoints(List<EastNorth> iterationStart, List<EastNorth> iterationEnd, double[] pointFactor, boolean[] frozen, boolean isClosed) {
        int n = iterationStart.size();
        List<EastNorth> testPoints = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            EastNorth startP = iterationStart.get(i);
            EastNorth endP = iterationEnd.get(i);

            double localFactor = frozen[i] ? 0.0 : pointFactor[i];
            double dx = (endP.east() - startP.east()) * localFactor;
            double dy = (endP.north() - startP.north()) * localFactor;

            double distPrev = Math.hypot(startP.east() - getPrev(iterationStart, i, isClosed).east(), 
                                         startP.north() - getPrev(iterationStart, i, isClosed).north());
            double distNext = Math.hypot(startP.east() - getNext(iterationStart, i, isClosed).east(), 
                                         startP.north() - getNext(iterationStart, i, isClosed).north());

            double refDist;
            if (distPrev > 1e-9 && distNext > 1e-9) {
                refDist = Math.min(distPrev, distNext);
            } else if (distPrev > 1e-9) {
                refDist = distPrev;
            } else if (distNext > 1e-9) {
                refDist = distNext;
            } else {
                refDist = 0.0;
            }

            double safeLimit = Math.max(0.05, refDist * 0.45);
            double moveDist = Math.hypot(dx, dy);

            if (moveDist > safeLimit && moveDist > 1e-11) {
                double ratio = safeLimit / moveDist;
                dx *= ratio;
                dy *= ratio;
            }
            testPoints.add(new EastNorth(startP.east() + dx, startP.north() + dy));
        }
        return testPoints;
    }
    
    private static List<EastNorth> smoothLaplacianInternal(
            List<EastNorth> points,
            int iterations,
            double lambda,
            boolean preserveEndpoints,
            boolean preserveCorners,
            double cornerAngleThreshold,
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

        double effectiveCornerThreshold = preserveCorners ? cornerAngleThreshold : 0.0;
        double mu = -1.01 * lambda;
        int n = current.size();

        for (int iter = 0; iter < iterations; iter++) {
            if (cancelChecker.getAsBoolean()) return returnNullOnCancel ? null : points;

            List<EastNorth> iterationStart = current;
            Set<Integer> baselineOffenders = findIntersectingPointIndices(iterationStart, isClosed, cancelChecker);

            List<EastNorth> step1 = applyLaplacianStep(iterationStart, lambda, preserveEndpoints, preserveCorners, effectiveCornerThreshold, isClosed, fixedExtra);
            List<EastNorth> iterationEnd = useTaubin ? applyLaplacianStep(step1, mu, preserveEndpoints, preserveCorners, effectiveCornerThreshold, isClosed, fixedExtra) : step1;

            double[] pointFactor = new double[n];
            java.util.Arrays.fill(pointFactor, 1.0);
            boolean[] frozen = new boolean[n];
            List<EastNorth> testPoints = null;

            for (int attempt = 0; attempt < 6; attempt++) {
                testPoints = buildTestPoints(iterationStart, iterationEnd, pointFactor, frozen, isClosed);

                Set<Integer> currentOffenders = findIntersectingPointIndices(testPoints, isClosed, cancelChecker);
                Set<Integer> newOffenders = new HashSet<>(currentOffenders);
                newOffenders.removeAll(baselineOffenders);

                if (newOffenders.isEmpty()) {
                    break;
                }
                if (attempt < 4) {
                    for (int idx : newOffenders) {
                        pointFactor[idx] *= 0.5;
                    }
                } else if (attempt == 4) {
                    for (int idx : newOffenders) {
                        frozen[idx] = true;
                    }
                }
            }
            current = testPoints;
        }

        List<EastNorth> result = new ArrayList<>(current.size());
        for (EastNorth p : current) {
            result.add(new EastNorth(p.east() + offE, p.north() + offN));
        }
        return result;
    }

    private static List<EastNorth> applyLaplacianStep(List<EastNorth> pts, double l, boolean pEnd, boolean pCor, double angleT, boolean closed, Set<Integer> fixedExtra) {
        int n = pts.size();
        if (n < 2) return new ArrayList<>(pts);
        List<EastNorth> next = new ArrayList<>(n);
        
        int limit = closed ? n - 1 : n;
        int sIdx = (!closed && pEnd) ? 1 : 0;
        int eIdx = (!closed && pEnd) ? n - 1 : limit;

        for (int i = 0; i < limit; i++) {
            if (i < sIdx || i >= eIdx || (pCor && isCorner(pts, i, angleT, closed)) || (fixedExtra != null && fixedExtra.contains(i))) {
                next.add(pts.get(i));
            } else {
                EastNorth curr = pts.get(i);
                boolean noPrev = !closed && i == 0;
                boolean noNext = !closed && i == n - 1;
                EastNorth prev = noPrev ? extrapolateTangent(pts, i, true) : getPrev(pts, i, closed);
                EastNorth nextP = noNext ? extrapolateTangent(pts, i, false) : getNext(pts, i, closed);
                double d1 = Math.hypot(curr.east() - prev.east(), curr.north() - prev.north());
                double d2 = Math.hypot(curr.east() - nextP.east(), curr.north() - nextP.north());
                double w1 = d1 > 1e-9 ? 1.0 / d1 : 0.0;
                double w2 = d2 > 1e-9 ? 1.0 / d2 : 0.0;
                if (w1 + w2 > 0) {
                    double avgX = (prev.east() * w1 + nextP.east() * w2) / (w1 + w2);
                    double avgY = (prev.north() * w1 + nextP.north() * w2) / (w1 + w2);
                    next.add(new EastNorth(curr.east() + l * (avgX - curr.east()), curr.north() + l * (avgY - curr.north())));
                } else {
                    next.add(curr);
                }
            }
        }
        
        if (closed) {
            next.add(next.get(0));
        }
        return next;
    }
    
    private static EastNorth extrapolateTangent(List<EastNorth> pts, int idx, boolean prevSide) {
        int n = pts.size();
        if (prevSide) {
            if (n < 3) return pts.get(idx);
            EastNorth p1 = pts.get(idx + 1);
            EastNorth p2 = pts.get(idx + 2);
            return new EastNorth(2 * p1.east() - p2.east(), 2 * p1.north() - p2.north());
        } else {
            if (n < 3) return pts.get(idx);
            EastNorth p1 = pts.get(idx - 1);
            EastNorth p2 = pts.get(idx - 2);
            return new EastNorth(2 * p1.east() - p2.east(), 2 * p1.north() - p2.north());
        }
    }
    
    public static List<EastNorth> smoothLaplacian(
            List<EastNorth> points,
            int iterations,
            double lambda,
            boolean preserveEndpoints,
            boolean preserveCorners,
            double cornerAngleThreshold,
            boolean useTaubin,
            boolean isClosed,
            BooleanSupplier checkCancelled) {
        return smoothLaplacianInternal(points, iterations, lambda, preserveEndpoints, preserveCorners, cornerAngleThreshold, useTaubin, isClosed, null, checkCancelled, false);
    }
    
    
    public static List<EastNorth> smoothLaplacianWithProgress(
            List<EastNorth> points,
            int iterations,
            double lambda,
            boolean preserveEndpoints,
            boolean preserveCorners,
            double cornerAngleThreshold,
            boolean useTaubin,
            boolean isClosed,
            Set<Integer> fixedExtra,
            BooleanSupplier progressCallback) {
        return smoothLaplacianInternal(points, iterations, lambda, preserveEndpoints, preserveCorners, cornerAngleThreshold, useTaubin, isClosed, fixedExtra, progressCallback, true);
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
        int intersectionCap = Math.max(100, n);
        int count = 0, endIdx = n - 1;
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
                    if (count > intersectionCap) return count;
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
        int n = points.size();
        if (isClosed && n > 2) {
            if (idx == 0) return points.get(n - 2);
            return points.get(idx - 1);
        }
        if (idx > 0) return points.get(idx - 1);
        return points.get(idx);
    }
    
    private static EastNorth getNext(List<EastNorth> points, int idx, boolean isClosed) {
        int n = points.size();
        if (isClosed && n > 2) {
            if (idx == n - 2) return points.get(0);
            if (idx == n - 1) return points.get(1);
            return points.get(idx + 1);
        }
        if (idx < n - 1) return points.get(idx + 1);
        return points.get(idx);
    }
    
    private static boolean isCorner(List<EastNorth> points, int idx, double angleThreshold, boolean isClosed) {
        if (angleThreshold <= 0) return false;
        
        EastNorth prev = getPrev(points, idx, isClosed);
        EastNorth curr = points.get(idx);
        EastNorth next = getNext(points, idx, isClosed);
        
        double angle = calculateAngle(prev, curr, next);
        return angle < angleThreshold;
    }
    
    public static double calculateAngle(EastNorth a, EastNorth b, EastNorth c) {
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

    public static class ChaikinResult {
        public final List<EastNorth> points;
        public final int[] nodeIndexMapping;

        public ChaikinResult(List<EastNorth> points, int[] nodeIndexMapping) {
            this.points = points;
            this.nodeIndexMapping = nodeIndexMapping;
        }
    }

    private static final class ChaikinPassResult {
        final List<EastNorth> points;
        final List<Boolean> fixed;
        final List<Integer> orig;
        final List<Boolean> suppressible;
        final List<EastNorth> fallback;

        ChaikinPassResult(List<EastNorth> points, List<Boolean> fixed, List<Integer> orig, List<Boolean> suppressible, List<EastNorth> fallback) {
            this.points = points;
            this.fixed = fixed;
            this.orig = orig;
            this.suppressible = suppressible;
            this.fallback = fallback;
        }
    }

    private static ChaikinPassResult generateChaikinPassPoints(
            List<EastNorth> curPts, List<Boolean> curFixed, List<Integer> curOrig,
            boolean isClosed, boolean preserveCorners, double angleThreshold,
            Set<Integer> suppressSlots) {

        int m = curPts.size();
        List<EastNorth> nextPts = new ArrayList<>();
        List<Boolean> nextFixed = new ArrayList<>();
        List<Integer> nextOrig = new ArrayList<>();
        List<Boolean> nextSuppressible = new ArrayList<>();
        List<EastNorth> nextFallback = new ArrayList<>();

        int edgeCount = isClosed ? m : m - 1;
        for (int i = 0; i < edgeCount; i++) {
            int nextIdx = (i + 1) % m;
            int prevIdx = (i - 1 + m) % m;

            EastNorth pCurr = curPts.get(i);
            EastNorth pNext = curPts.get(nextIdx);
            EastNorth pPrev = curPts.get(prevIdx);

            boolean fCurr = curFixed.get(i);
            boolean fNext = curFixed.get(nextIdx);
            int origCurr = curOrig.get(i);

            if (fCurr && fNext) {
                nextPts.add(pCurr);
                nextFixed.add(true);
                nextOrig.add(origCurr);
                nextSuppressible.add(false);
                nextFallback.add(pCurr);
            } else if (fCurr && !fNext) {
                nextPts.add(pCurr);
                nextFixed.add(true);
                nextOrig.add(origCurr);
                nextSuppressible.add(false);
                nextFallback.add(pCurr);

                int genSlot = nextPts.size();
                EastNorth generated = null;

                boolean isPreservedSharp = preserveCorners && isCorner(curPts, i, angleThreshold, isClosed);
                if (!isPreservedSharp && (isClosed || i > 0)) {
                    double dIn = Math.hypot(pCurr.east() - pPrev.east(), pCurr.north() - pPrev.north());
                    double dOut = Math.hypot(pNext.east() - pCurr.east(), pNext.north() - pCurr.north());
                    if (dIn > 1e-9 && dOut > 1e-9) {
                        double u1x = (pCurr.east() - pPrev.east()) / dIn;
                        double u1y = (pCurr.north() - pPrev.north()) / dIn;
                        double u2x = (pNext.east() - pCurr.east()) / dOut;
                        double u2y = (pNext.north() - pCurr.north()) / dOut;
                        double tx = u1x + u2x;
                        double ty = u1y + u2y;
                        double tlen = Math.hypot(tx, ty);
                        if (tlen > 1e-6) {
                            tx /= tlen;
                            ty /= tlen;
                            double span = Math.min(dOut * 0.35, dIn * 0.35);
                            generated = new EastNorth(pCurr.east() + tx * span, pCurr.north() + ty * span);
                        }
                    }
                }
                if (generated == null) {
                    generated = new EastNorth(0.25 * pCurr.east() + 0.75 * pNext.east(),
                                              0.25 * pCurr.north() + 0.75 * pNext.north());
                }
                nextPts.add(suppressSlots.contains(genSlot) ? pCurr : generated);
                nextFixed.add(false);
                nextOrig.add(-1);
                nextSuppressible.add(true);
                nextFallback.add(pCurr);
            } else if (!fCurr && fNext) {
                int nextNextIdx = (nextIdx + 1) % m;
                EastNorth pNextNext = curPts.get(nextNextIdx);
                boolean isPreservedSharp = preserveCorners && isCorner(curPts, nextIdx, angleThreshold, isClosed);

                EastNorth generated = null;
                if (!isPreservedSharp && (isClosed || nextIdx < m - 1)) {
                    double dIn = Math.hypot(pNext.east() - pCurr.east(), pNext.north() - pCurr.north());
                    double dOut = Math.hypot(pNextNext.east() - pNext.east(), pNextNext.north() - pNext.north());
                    if (dIn > 1e-9 && dOut > 1e-9) {
                        double u1x = (pNext.east() - pCurr.east()) / dIn;
                        double u1y = (pNext.north() - pCurr.north()) / dIn;
                        double u2x = (pNextNext.east() - pNext.east()) / dOut;
                        double u2y = (pNextNext.north() - pNext.north()) / dOut;
                        double tx = u1x + u2x;
                        double ty = u1y + u2y;
                        double tlen = Math.hypot(tx, ty);
                        if (tlen > 1e-6) {
                            tx /= tlen;
                            ty /= tlen;
                            double span = Math.min(dIn * 0.35, dOut * 0.35);
                            generated = new EastNorth(pNext.east() - tx * span, pNext.north() - ty * span);
                        }
                    }
                }
                if (generated == null) {
                    generated = new EastNorth(0.75 * pCurr.east() + 0.25 * pNext.east(),
                                              0.75 * pCurr.north() + 0.25 * pNext.north());
                }
                int genSlot = nextPts.size();
                nextPts.add(suppressSlots.contains(genSlot) ? pNext : generated);
                nextFixed.add(false);
                nextOrig.add(origCurr);
                nextSuppressible.add(true);
                nextFallback.add(pNext);
            } else {
                int genSlot1 = nextPts.size();
                EastNorth g1 = new EastNorth(0.75 * pCurr.east() + 0.25 * pNext.east(),
                                             0.75 * pCurr.north() + 0.25 * pNext.north());
                nextPts.add(suppressSlots.contains(genSlot1) ? pCurr : g1);
                nextFixed.add(false);
                nextOrig.add(origCurr);
                nextSuppressible.add(true);
                nextFallback.add(pCurr);

                int genSlot2 = nextPts.size();
                EastNorth g2 = new EastNorth(0.25 * pCurr.east() + 0.75 * pNext.east(),
                                             0.25 * pCurr.north() + 0.75 * pNext.north());
                nextPts.add(suppressSlots.contains(genSlot2) ? pNext : g2);
                nextFixed.add(false);
                nextOrig.add(-1);
                nextSuppressible.add(true);
                nextFallback.add(pNext);
            }
        }

        if (!isClosed) {
            int lastIdx = m - 1;
            nextPts.add(curPts.get(lastIdx));
            nextFixed.add(curFixed.get(lastIdx));
            nextOrig.add(curOrig.get(lastIdx));
            nextSuppressible.add(false);
            nextFallback.add(curPts.get(lastIdx));
        }

        return new ChaikinPassResult(nextPts, nextFixed, nextOrig, nextSuppressible, nextFallback);
    }

    public static ChaikinResult subdivideChaikinTracked(
            List<EastNorth> points,
            boolean isClosed,
            int passes,
            boolean preserveEndpoints,
            boolean preserveCorners,
            double cornerAngleThreshold,
            boolean useTaubin,
            Set<Integer> fixedIndices) {

        int n = points.size();
        if (n < 2) {
            int[] map = new int[n];
            for (int i = 0; i < n; i++) map[i] = i;
            return new ChaikinResult(new ArrayList<>(points), map);
        }

        int distinctCount = isClosed ? n - 1 : n;
        double angleThreshold = preserveCorners ? cornerAngleThreshold : 0.0;

        List<EastNorth> curPts = new ArrayList<>(distinctCount);
        List<Boolean> curFixed = new ArrayList<>(distinctCount);
        List<Integer> curOrig = new ArrayList<>(distinctCount);

        boolean hasAnyFixed = false;
        for (int i = 0; i < distinctCount; i++) {
            curPts.add(points.get(i));
            boolean fixed = false;
            if (fixedIndices != null && fixedIndices.contains(i)) {
                fixed = true;
            } else if (!isClosed && preserveEndpoints && (i == 0 || i == distinctCount - 1)) {
                fixed = true;
            } else if (preserveCorners && isCorner(points, i, angleThreshold, isClosed)) {
                fixed = true;
            }
            curFixed.add(fixed);
            if (fixed) hasAnyFixed = true;
            curOrig.add(i);
        }

        double origArea = isClosed ? calculateSignedArea(curPts) : 0.0;
        EastNorth centroid = isClosed ? calculateCentroid(curPts) : null;

        for (int pass = 0; pass < passes; pass++) {
            int m = curPts.size();
            if (m < 2) break;

            int baselineIntersections = countSelfIntersections(curPts, isClosed, () -> false);

            Set<Integer> suppressSlots = new HashSet<>();
            ChaikinPassResult passResult = generateChaikinPassPoints(curPts, curFixed, curOrig, isClosed, preserveCorners, angleThreshold, suppressSlots);

            for (int attempt = 0; attempt < 5; attempt++) {
                int currentIntersections = countSelfIntersections(passResult.points, isClosed, () -> false);
                if (currentIntersections <= baselineIntersections) {
                    break;
                }
                Set<Integer> offenders = findIntersectingPointIndices(passResult.points, isClosed, () -> false);
                boolean addedAny = false;
                for (int idx : offenders) {
                    if (idx < passResult.suppressible.size() && passResult.suppressible.get(idx) && !suppressSlots.contains(idx)) {
                        suppressSlots.add(idx);
                        addedAny = true;
                    }
                }
                if (!addedAny) {
                    break;
                }
                passResult = generateChaikinPassPoints(curPts, curFixed, curOrig, isClosed, preserveCorners, angleThreshold, suppressSlots);
            }

            curPts = passResult.points;
            curFixed = passResult.fixed;
            curOrig = passResult.orig;
        }

        if (isClosed && useTaubin && !hasAnyFixed && Math.abs(origArea) > 1e-6 && centroid != null) {
            double newArea = calculateSignedArea(curPts);
            if (Math.abs(newArea) > 1e-6) {
                double scale = Math.sqrt(Math.abs(origArea / newArea));
                List<EastNorth> scaled = new ArrayList<>(curPts.size());
                for (EastNorth p : curPts) {
                    double e = centroid.east() + (p.east() - centroid.east()) * scale;
                    double nCoord = centroid.north() + (p.north() - centroid.north()) * scale;
                    scaled.add(new EastNorth(e, nCoord));
                }
                curPts = scaled;
            }
        }

        if (isClosed && !curPts.isEmpty()) {
            curPts.add(curPts.get(0));
            curOrig.add(curOrig.get(0));
        }

        int[] map = new int[curOrig.size()];
        for (int i = 0; i < curOrig.size(); i++) {
            map[i] = curOrig.get(i);
        }

        return new ChaikinResult(curPts, map);
    }

    private static double calculateSignedArea(List<EastNorth> pts) {
        int m = pts.size();
        if (m < 3) return 0.0;
        double area = 0.0;
        for (int i = 0; i < m; i++) {
            EastNorth p1 = pts.get(i);
            EastNorth p2 = pts.get((i + 1) % m);
            area += (p1.east() * p2.north() - p2.east() * p1.north());
        }
        return area * 0.5;
    }

    private static EastNorth calculateCentroid(List<EastNorth> pts) {
        int m = pts.size();
        if (m == 0) return new EastNorth(0, 0);
        double sumE = 0.0, sumN = 0.0;
        for (EastNorth p : pts) {
            sumE += p.east();
            sumN += p.north();
        }
        return new EastNorth(sumE / m, sumN / m);
    }
}
