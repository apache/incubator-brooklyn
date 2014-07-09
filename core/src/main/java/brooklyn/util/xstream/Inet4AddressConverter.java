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
package brooklyn.util.xstream;

import java.net.Inet4Address;
import java.net.UnknownHostException;

import brooklyn.util.exceptions.Exceptions;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

public class Inet4AddressConverter implements Converter {

    @Override
    public boolean canConvert(@SuppressWarnings("rawtypes") Class type) {
        return type.equals(Inet4Address.class);
    }

    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        Inet4Address addr = (Inet4Address) source;
        writer.setValue(addr.getHostName()+"/"+addr.getHostAddress());
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        String hostSlashAddress = reader.getValue();
        int i = hostSlashAddress.indexOf('/');
        try {
            if (i==-1) {
                return Inet4Address.getByName(hostSlashAddress);
            } else {
                String host = hostSlashAddress.substring(0, i);
                String addrS = hostSlashAddress.substring(i+1);
                byte[] addr = new byte[4];
                String[] addrSI = addrS.split("\\.");
                for (int k=0; k<4; k++) addr[k] = (byte)(int)Integer.valueOf(addrSI[k]);
                return Inet4Address.getByAddress(host, addr);
            }
        } catch (UnknownHostException e) {
            throw Exceptions.propagate(e);
        }
    }

}
