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
package brooklyn.entity.java;

public class ExampleVanillaMainCpuHungry {
    private static final int MAX_TIME_MILLIS = 100*1000;
    private static final int CALCULATIONS_PER_CYCLE = 100000;
    private static final int SLEEP_PER_CYCLE_MILLIS = 1;
    
    public static void main(String[] args) throws Exception {
        System.out.println("In ExampleVanillaMainCpuHungry.main");
        long startTime = System.currentTimeMillis();
        long count = 0;
        double total = 0;
        do {
            for (int i = 0; i < CALCULATIONS_PER_CYCLE; i++) {
                total += Math.sqrt(Math.random());
                count++;
            }
            Thread.sleep(SLEEP_PER_CYCLE_MILLIS);
        } while ((System.currentTimeMillis() - startTime) < MAX_TIME_MILLIS);
        
        System.out.println("Did "+count+" random square roots, took "+(System.currentTimeMillis()-startTime)+"ms; total = "+total);
    }
}
