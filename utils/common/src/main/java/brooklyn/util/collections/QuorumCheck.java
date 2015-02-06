/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.util.collections;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;

import brooklyn.util.yaml.Yamls;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

/**
 * For checking if a group/cluster is quorate. That is, whether the group has sufficient
 * healthy members.
 */
public interface QuorumCheck {

    /**
     * @param sizeHealthy Number of healthy members
     * @param totalSize   Total number of members one would expect to be healthy (i.e. ignoring stopped members)
     * @return            Whether this group is healthy
     */
    public boolean isQuorate(int sizeHealthy, int totalSize);

    public static class QuorumChecks {
        /**
         * Checks that all members that should be up are up (i.e. ignores stopped nodes).
         */
        public static QuorumCheck all() {
            return new NumericQuorumCheck(0, 1.0, false, "all");
        }
        /**
         * Checks all members that should be up are up, and that there is at least one such member.
         */
        public static QuorumCheck allAndAtLeastOne() {
            return new NumericQuorumCheck(1, 1.0, false, "allAndAtLeastOne");
        }
        /**
         * Requires at least one member that should be up is up.
         */
        public static QuorumCheck atLeastOne() {
            return new NumericQuorumCheck(1, 0.0, false, "atLeastOne");
        }
        /**
         * Requires at least one member to be up if the total size is non-zero.
         * i.e. okay if empty, or if non-empty and something is healthy, but not okay if not-empty and nothing is healthy.
         * "Empty" means that no members are supposed to be up  (e.g. there may be stopped members).
         */
        public static QuorumCheck atLeastOneUnlessEmpty() {
            return new NumericQuorumCheck(1, 0.0, true, "atLeastOneUnlessEmpty");
        }
        /**
         * Always "healthy"
         */
        public static QuorumCheck alwaysTrue() {
            return new NumericQuorumCheck(0, 0.0, true, "alwaysHealthy");
        }
        
        public static QuorumCheck newInstance(int minRequiredSize, double minRequiredRatio, boolean allowEmpty) {
            return new NumericQuorumCheck(minRequiredSize, minRequiredRatio, allowEmpty);
        }
        
        /** See {@link QuorumChecks#newLinearRange(String,String)} */
        public static QuorumCheck newLinearRange(String range) {
            return newLinearRange(range, null);
        }
        
        /** Given a JSON representation of a list of points (where a point is a list of 2 numbers),
         * with the points in increasing x-coordinate value,
         * this constructs a quorum check which does linear interpolation on those coordinates,
         * with extensions to either side.
         * The x-coordinate is taken as the total size, and the y-coordinate as the minimum required size.
         * <p>
         * It sounds complicated but it gives a very easy and powerful way to define quorum checks.
         * For instance:
         * <p>
         * <code>[[0,0],[1,1]]</code> says that if 0 items are expected, at least 0 is required; 
         *   if 1 is expected, 1 is required; and by extension if 10 are expected, 10 are required.
         *   In other words, this is the same as {@link #all()}.
         * <p>
         * <code>[[0,1],[1,1],[2,2]]</code> is the same as the previous for x (number expected) greater-than or equal to 1;
         * but if 0 is expected, 1 is required, and so it fails when 0 are present.
         * In other words, {@link #allAndAtLeastOne()}.
         * <p>
         * <code>[[5,5],[10,10],[100,70],[200,140]]</code> has {@link #all()} behavior up to 10 expected 
         * (line extended to the left, for less than 5); but then gently tapers off to requiring only 70% at 100
         * (with 30 of 40 = 75% required at that intermediate point along the line [[10,10],[100,70]]);
         * and then from 100 onwards it is a straight 70%.
         * <p>
         * The type of linear regression described in the last example is quite useful in practise, 
         * to be stricter for smaller clusters (or possibly more lax for small values in some cases,
         * such as when tolerating dangling references during rebind). 
         */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public static QuorumCheck newLinearRange(String range, String name) {
            return LinearRangeQuorumCheck.of(name, (Iterable)Iterables.getOnlyElement( Yamls.parseAll(range) ));
        }
        
        private static final List<QuorumCheck> NAMED_CHECKS = MutableList
                .of(all(), allAndAtLeastOne(), atLeastOne(), atLeastOneUnlessEmpty(), alwaysTrue());
        
        public static QuorumCheck of(String nameOrRange) {
            if (nameOrRange==null) return null;
            for (QuorumCheck qc: NAMED_CHECKS) {
                if (qc instanceof NumericQuorumCheck) {
                    if (Objects.equal(nameOrRange, ((NumericQuorumCheck)qc).getName()))
                        return qc;
                }
            }
            return newLinearRange(nameOrRange);
        }
    }
    
    public static class NumericQuorumCheck implements QuorumCheck, Serializable {
        private static final long serialVersionUID = -5090669237460159621L;
        
        protected final int minRequiredSize;
        protected final double minRequiredRatio;
        protected final boolean allowEmpty;
        protected final String name;

        public NumericQuorumCheck(int minRequiredSize, double minRequiredRatio, boolean allowEmpty) {
            this(minRequiredSize, minRequiredRatio, allowEmpty, null);
        }
        public NumericQuorumCheck(int minRequiredSize, double minRequiredRatio, boolean allowEmpty, String name) {
            this.minRequiredSize = minRequiredSize;
            this.minRequiredRatio = minRequiredRatio;
            this.allowEmpty = allowEmpty;
            this.name = name;
        }
        
        @Override
        public boolean isQuorate(int sizeHealthy, int totalSize) {
            if (allowEmpty && totalSize==0) return true;
            if (sizeHealthy < minRequiredSize) return false;
            if (sizeHealthy < totalSize*minRequiredRatio-0.000000001) return false;
            return true;
        }

        public String getName() {
            return name;
        }
        
        @Override
        public String toString() {
            return "QuorumCheck["+(name!=null?name+";":"")+"require="+minRequiredSize+","+((int)100*minRequiredRatio)+"%"+(allowEmpty ? "|0" : "")+"]";
        }
    }

    /** See {@link QuorumChecks#newLinearRange(String,String)} */
    public static class LinearRangeQuorumCheck implements QuorumCheck, Serializable {

        private static final long serialVersionUID = -6425548115925898645L;

        private static class Point {
            final double size, minRequiredAtSize;
            public Point(double size, double minRequiredAtSize) { this.size = size; this.minRequiredAtSize = minRequiredAtSize; }
            public static Point ofIntegerCoords(Iterable<Integer> coords) {
                Preconditions.checkNotNull(coords==null, "coords");
                Preconditions.checkArgument(Iterables.size(coords)==2, "A point must consist of two coordinates; invalid data: "+coords);
                Iterator<Integer> ci = coords.iterator();
                return new Point(ci.next(), ci.next());
            }
            public static List<Point> listOfIntegerCoords(Iterable<? extends Iterable<Integer>> points) {
                MutableList<Point> result = MutableList.of();
                for (Iterable<Integer> point: points) result.add(ofIntegerCoords(point));
                return result.asUnmodifiable();
            }
            @Override
            public String toString() {
                return "("+size+","+minRequiredAtSize+")";
            }
        }
        
        protected final String name;
        protected final List<Point> points;

        public static LinearRangeQuorumCheck of(String name, Iterable<? extends Iterable<Integer>> points) {
            return new LinearRangeQuorumCheck(name, Point.listOfIntegerCoords(points));
        }
        public static LinearRangeQuorumCheck of(Iterable<? extends Iterable<Integer>> points) {
            return new LinearRangeQuorumCheck(null, Point.listOfIntegerCoords(points));
        }
        
        protected LinearRangeQuorumCheck(String name, Iterable<Point> points) {
            Preconditions.checkArgument(Iterables.size(points)>=2, "At least two points must be supplied for "+name+": "+points);
            this.name = name;
            this.points = MutableList.copyOf(points).asUnmodifiable();
            // check valid
            Point last = null;
            for (Point p: points) {
                if (last!=null) {
                    if (p.size <= last.size) throw new IllegalStateException("Points must be supplied in order of increasing totalSize (x coordinate); instead have "+last+" and "+p);
                }
            }
        }

        @Override
        public boolean isQuorate(int sizeHealthy, int totalSize) {
            Point next = points.get(0);
            Point prev = null;
            for (int i=1; i<points.size(); i++) {
                prev = next;
                next = points.get(i);
                if (next.size>totalSize) break;
            }
            double minRequiredAtSize = (totalSize-prev.size)/(next.size-prev.size) * (next.minRequiredAtSize-prev.minRequiredAtSize) + prev.minRequiredAtSize;
            return (sizeHealthy > minRequiredAtSize-0.000000001);
        }
        
        @Override
        public String toString() {
            return "LinearRangeQuorumCheck["+(name!=null ? name+":" : "")+points+"]";
        }
    }

}
