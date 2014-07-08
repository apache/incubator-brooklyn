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
package brooklyn.util.text;


/** takes a version string, and compares to other versions, using {@link NaturalOrderComparator} */
public class ComparableVersion implements Comparable<String> {

    public final String version;
    
    public ComparableVersion(String version) {
        this.version = version;
    }

    public int compareTo(String target) {
        return new NaturalOrderComparator().compare(version, target);
    }
    
    public boolean isGreaterThanOrEqualTo(String target) {
        return compareTo(target) >= 0;
    }
    public boolean isGreaterThanAndNotEqualTo(String target) {
        return compareTo(target) > 0;
    }
    public boolean isLessThanOrEqualTo(String target) {
        return compareTo(target) <= 0;
    }
    public boolean isLessThanAndNotEqualTo(String target) {
        return compareTo(target) < 0;
    }

    /** inclusive at endpoints */
    public boolean isInRange(String lowerBound, String upperBound) {
        return isGreaterThanAndNotEqualTo(lowerBound) && isLessThanAndNotEqualTo(upperBound);
    }

    /** parses a string expressed with common mathematical sematics,
     * as either square brackets (inclusive), round brackets (exclusive), or one of each,
     * surrounding a pair of version strings separated by a comma, where a version string 
     * consists of any non-whitespace non-bracket characters 
     * (ie numbers, letters, dots, hyphens, underscores) or is empty (to indicate no bound); 
     * e.g. "[10.6,10.7)" to mean >= 10.6 and < 10.7;
     * "[10.6,)" to mean >= 10.6.
     */
    public boolean isInRange(String range) {
        String r = range.trim();
        boolean strictLeft, strictRight;
        
        if (r.startsWith("(")) strictLeft = true;
        else if (r.startsWith("[")) strictLeft = false;
        else throw new IllegalArgumentException("Range must start with ( or [");
        if (r.endsWith(")")) strictRight = true;
        else if (r.endsWith("]")) strictRight = false;
        else throw new IllegalArgumentException("Range must end with ) or ]");
        
        int i = r.indexOf(",");
        if (i==-1) throw new IllegalArgumentException("Range must contain , following the open bracket and version");
        String left = r.substring(1, i).trim();
        String right = r.substring(i+1, r.length()-1).trim();
        
        if (left.length()>0) {
            if (strictLeft && compareTo(left)<=0) return false; 
            if (!strictLeft && compareTo(left)<0) return false; 
        }
        if (right.length()>0) {
            if (strictRight && compareTo(right)>=0) return false; 
            if (!strictRight && compareTo(right)>0) return false; 
        }

        return true;
    }

}
