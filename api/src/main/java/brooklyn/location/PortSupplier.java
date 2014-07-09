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
package brooklyn.location;

/** Mixin interface for location which allows it to supply ports from a given range */
public interface PortSupplier {

    /**
     * Reserve a specific port for an application. If your application requires a specific port - for example, port 80 for a web
     * server - you should reserve this port before starting your application. Using this method, you will be able to detect if
     * another application has already claimed this port number.
     *
     * @param portNumber the required port number.
     * @return {@code true} if the port was successfully reserved; {@code false} if it has been previously reserved.
     */
    boolean obtainSpecificPort(int portNumber);

    /**
     * Reserve a port for your application, with a port number in a specific range. If your application requires a port, but it does
     * not mind exactly which port number - for example, a port for internal JMX monitoring - call this method.
     *
     * @param range the range of acceptable port numbers.
     * @return the port number that has been reserved, or -1 if there was no available port in the acceptable range.
     */
    int obtainPort(PortRange range);

    /**
     * Release a previously reserved port.
     *
     * @param portNumber the port number from a call to {@link #obtainPort(PortRange)} or {@link #obtainSpecificPort(int)}
     */
    void releasePort(int portNumber);

}
