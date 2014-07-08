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
package brooklyn.policy.followthesun;

import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.Location;

public class FollowTheSunParameters {

    private static final Logger LOG = LoggerFactory.getLogger(FollowTheSunParameters.class);

    private FollowTheSunParameters() {}

    /** trigger for moving segment X from geo A to geo B:
     * where x is total number of requests submitted in X across the CDM network,
     * and x_A is number of reqs from geo A, with A the most prolific geography
     * (arbitrarily chosen in case of ties so recommended to choose at least a small percent_majority or delta_above_percent_majority, in addition to this field);
     * this parameter T defines a number such that x_A > T*x in order for X to be migrated to A
     * (but see also DELTA_ABOVE_PERCENT_TOTAL, below) */
    public double triggerPercentTotal = 0.3;
    /** fields as above, and T as above,
     * this parameter T' defines a number such that x_A > T*x + T' in order for X to be migrated to A */
    public double triggerDeltaAbovePercentTotal = 0;
    /** fields as above,
     * this parameter T defines a number such that x_A > T in order for X to be migrated to A */
    public double triggerAbsoluteTotal = 2;

    /** fields as above, with X_B the number from a different geography B,
     * where A and B are the two most prolific requesters of X, and X_A >= X_B;
     * this parameter T defines a number such that x_A-x_B > T*x in order for X to be migrated to A */
    public double triggerPercentMajority = 0.2;
    /** as corresponding majority and total fields, with x_A-x_B on the LHS of inequality */
    public double triggerDeltaAbovePercentMajority = 1;
    /** as corresponding majority and total fields, with x_A-x_B on the LHS of inequality */
    public double triggerAbsoluteMajority = 4;
    
    /** a list of excluded locations */
    public Set<Location> excludedLocations = new LinkedHashSet<Location>();

    public static FollowTheSunParameters newDefault() {
        return new FollowTheSunParameters();
    }

    private static double parseDouble(String text, double defaultValue) {
        try {
            double d = Double.parseDouble(text);
            if (!Double.isNaN(d)) return d;
        } catch (Exception e) {
            LOG.warn("Illegal double value '"+text+"', using default "+defaultValue+": "+e, e);
        }
        return defaultValue;
    }

    private static String[] parseCommaSeparatedList(String csv) {
        if (csv==null || csv.trim().length()==0) return new String[0];
        return csv.split(",");
    }

    public boolean isTriggered(double highest, double total, double nextHighest, double current) {
        if (highest <= current) return false;
        if (highest < total*triggerPercentTotal + triggerDeltaAbovePercentTotal) return false;
        if (highest < triggerAbsoluteTotal) return false;
        //TODO more params about nextHighest vs current
        if (highest-current < total*triggerPercentMajority + triggerDeltaAbovePercentMajority) return false;
        if (highest-current < triggerAbsoluteMajority) return false;
        return true;
    }
    
    public String toString() {
        return "Inter-geography policy params: percentTotal="+triggerPercentTotal+"; deltaAbovePercentTotal="+triggerDeltaAbovePercentTotal+
                "; absoluteTotal="+triggerAbsoluteTotal+"; percentMajority="+triggerPercentMajority+
                "; deltaAbovePercentMajority="+triggerDeltaAbovePercentMajority+"; absoluteMajority="+triggerAbsoluteMajority;

    }
}
