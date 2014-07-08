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
package brooklyn.util.net;

import java.io.Serializable;

import com.google.common.base.Objects;
import com.google.common.net.HostAndPort;

public class UserAndHostAndPort implements Serializable {

    private static final long serialVersionUID = 1525306419968853853L;

    public static UserAndHostAndPort fromParts(String user, String host, int port) {
        return new UserAndHostAndPort(user, HostAndPort.fromParts(host, port));
    }

    /**
     * Split a string of the form myuser@myhost:1234 into a user, host and port.
     *  
     * @param str The input string to parse.
     * @return    If parsing was successful, a populated UserAndHostAndPort object.
     * @throws IllegalArgumentException
     *             if nothing meaningful could be parsed.
     */
    public static UserAndHostAndPort fromString(String str) {
        int userEnd = str.indexOf("@");
        if (userEnd < 0) throw new IllegalArgumentException("User missing (no '@' in "+str);
        return new UserAndHostAndPort(str.substring(0, userEnd).trim(), HostAndPort.fromString(str.substring(userEnd+1).trim()));
    }

    private final String user;
    private final HostAndPort hostAndPort;
    
    protected UserAndHostAndPort(String user, HostAndPort hostAndPort) {
        this.user = user;
        this.hostAndPort = hostAndPort;
    }
    
    public String getUser() {
        return user;
    }
    
    public HostAndPort getHostAndPort() {
        return hostAndPort;
    }
    
    @Override
    public String toString() {
        return user + "@" + hostAndPort.getHostText() + (hostAndPort.hasPort() ? ":" + hostAndPort.getPort() : "");
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof UserAndHostAndPort)) return false;
        UserAndHostAndPort o = (UserAndHostAndPort) obj;
        return Objects.equal(user, o.user) && Objects.equal(hostAndPort, o.hostAndPort);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(user, hostAndPort);
    }
}
