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
package brooklyn.location.jclouds.networking;

import java.util.List;
import java.util.concurrent.Callable;

import org.jclouds.aws.ec2.AWSEC2Api;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.net.domain.IpPermission;
import org.jclouds.net.domain.IpProtocol;
import org.jclouds.net.util.IpPermissions;

import brooklyn.util.collections.MutableList;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.net.Cidr;
import brooklyn.util.text.Identifiers;

import com.google.common.annotations.Beta;

/** WIP to define a security group in an up-front way, where subsequently it can be applied to a jclouds location */
@Beta
public class SecurityGroupDefinition {

    private Callable<String> groupNameFactory = new Callable<String>() { public String call() { return "br-sg-"+Identifiers.makeRandomId(8); } };
    private List<IpPermission> ipPerms = MutableList.of();
    
    public void createGroupInAwsRegion(ComputeServiceContext computeServiceContext, String region) {
        AWSEC2Api ec2Client = computeServiceContext.unwrapApi(AWSEC2Api.class);
        String sgId = ec2Client.getSecurityGroupApi().get().createSecurityGroupInRegionAndReturnId(region, getName(), "Brooklyn-managed security group "+getName());
        ec2Client.getSecurityGroupApi().get().authorizeSecurityGroupIngressInRegion(region, sgId, ipPerms);
    }

    /** allows access to the given port on TCP from within the subnet */
    public SecurityGroupDefinition allowingInternalPort(int port) {
        return allowing(IpPermissions.permit(IpProtocol.TCP).port(port));
    }
    public SecurityGroupDefinition allowingInternalPorts(int port1, int port2, int ...ports) {
        allowing(IpPermissions.permit(IpProtocol.TCP).port(port1));
        allowing(IpPermissions.permit(IpProtocol.TCP).port(port2));
        for (int port: ports)
            allowing(IpPermissions.permit(IpProtocol.TCP).port(port));
        return this;
    }
    public SecurityGroupDefinition allowingInternalPortRange(int portRangeStart, int portRangeEnd) {
        return allowing(IpPermissions.permit(IpProtocol.TCP).fromPort(portRangeStart).to(portRangeEnd));
    }
    public SecurityGroupDefinition allowingInternalPing() {
        return allowing(IpPermissions.permit(IpProtocol.ICMP));
    }
    
    public SecurityGroupDefinition allowingPublicPort(int port) {
        return allowing(IpPermissions.permit(IpProtocol.TCP).port(port).originatingFromCidrBlock(Cidr.UNIVERSAL.toString()));
    }
    public SecurityGroupDefinition allowingPublicPorts(int port1, int port2, int ...ports) {
        allowing(IpPermissions.permit(IpProtocol.TCP).port(port1).originatingFromCidrBlock(Cidr.UNIVERSAL.toString()));
        allowing(IpPermissions.permit(IpProtocol.TCP).port(port2).originatingFromCidrBlock(Cidr.UNIVERSAL.toString()));
        for (int port: ports)
            allowing(IpPermissions.permit(IpProtocol.TCP).port(port).originatingFromCidrBlock(Cidr.UNIVERSAL.toString()));
        return this;
    }
    public SecurityGroupDefinition allowingPublicPortRange(int portRangeStart, int portRangeEnd) {
        return allowing(IpPermissions.permit(IpProtocol.TCP).fromPort(portRangeStart).to(portRangeEnd).originatingFromCidrBlock(Cidr.UNIVERSAL.toString()));
    }
    public SecurityGroupDefinition allowingPublicPing() {
        return allowing(IpPermissions.permit(IpProtocol.ICMP).originatingFromCidrBlock(Cidr.UNIVERSAL.toString()));
    }
    
    public SecurityGroupDefinition allowing(IpPermission permission) {
        ipPerms.add(permission);
        return this;
    }
    
    // TODO use cloud machine namer
    public SecurityGroupDefinition named(final String name) {
        groupNameFactory = new Callable<String>() { public String call() { return name; } };
        return this;
    }
    public String getName() { 
        try { return groupNameFactory.call(); } 
        catch (Exception e) { throw Exceptions.propagate(e); } 
    }

    public Iterable<IpPermission> getPermissions() {
        return ipPerms;
    }
}
