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
package org.apache.brooklyn.policy.jclouds.os;

import java.util.List;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.policy.AbstractPolicy;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.core.internal.ssh.SshTool;
import static org.apache.brooklyn.util.ssh.BashCommands.sbinPath;
import org.apache.brooklyn.util.text.Identifiers;
import org.jclouds.compute.config.AdminAccessConfiguration;
import org.jclouds.scriptbuilder.functions.InitAdminAccess;
import org.jclouds.scriptbuilder.statements.login.AdminAccess;
import org.jclouds.scriptbuilder.statements.ssh.SshdConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * When attached to an entity, this will monitor for when an {@link SshMachineLocation} is added to that entity
 * (e.g. when a VM has been provisioned for it).
 * 
 * The policy will then (asynchronously) add a new user to the VM, with a randomly generated password.
 * The ssh details will be set as a sensor on the entity.
 * 
 * If this is used, it is strongly encouraged to tell users to change the password on first login.
 * 
 * A preferred mechanism would be for an external key-management tool to generate ssh key-pairs for
 * the user, and for the public key to be passed to Brooklyn. However, there is not a customer 
 * requirement for that yet, so focusing on the password-approach.
 */
@Beta
public class CreateUserPolicy extends AbstractPolicy implements SensorEventListener<Location> {

    // TODO Should add support for authorizing ssh keys as well
    
    // TODO Should review duplication with:
    //  - JcloudsLocationConfig.GRANT_USER_SUDO
    //    (but config default/description and context of use are different)
    //  - AdminAccess in JcloudsLocation.createUserStatements
    
    // TODO Could make the password explicitly configurable, or auto-generate if not set?
    
    private static final Logger LOG = LoggerFactory.getLogger(CreateUserPolicy.class);

    @SetFromFlag("user")
    public static final ConfigKey<String> VM_USERNAME = ConfigKeys.newStringConfigKey("createuser.vm.user.name");

    @SetFromFlag("grantSudo")
    public static final ConfigKey<Boolean> GRANT_SUDO = ConfigKeys.newBooleanConfigKey(
            "createuser.vm.user.grantSudo",
            "Whether to give the new user sudo rights",
            false);

    public static final AttributeSensor<String> VM_USER_CREDENTIALS = Sensors.newStringSensor(
            "createuser.vm.user.credentials",
            "The \"<user> : <password> @ <hostname>:<port>\"");

    @SetFromFlag("resetLoginUser")
    public static final ConfigKey<Boolean> RESET_LOGIN_USER = ConfigKeys.newBooleanConfigKey(
            "createuser.vm.user.resetLoginUser",
            "Whether to reset the password used for user login",
            false);

    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        subscriptions().subscribe(entity, AbstractEntity.LOCATION_ADDED, this);
    }

    @Override
    public void onEvent(SensorEvent<Location> event) {
        final Entity entity = event.getSource();
        final Location loc = event.getValue();
        if (loc instanceof SshMachineLocation) {
            addUserAsync(entity, (SshMachineLocation)loc);
        }
    }

    protected void addUserAsync(final Entity entity, final SshMachineLocation machine) {
        ((EntityInternal)entity).getExecutionContext().execute(new Runnable() {
            public void run() {
                addUser(entity, machine);
            }});
    }
    
    protected void addUser(Entity entity, SshMachineLocation machine) {
        boolean grantSudo = getRequiredConfig(GRANT_SUDO);
        boolean resetPassword = getRequiredConfig(RESET_LOGIN_USER);
        String user = getRequiredConfig(VM_USERNAME);
        String password = Identifiers.makeRandomId(12);
        String hostname = machine.getAddress().getHostName();
        int port = machine.getPort();
        String creds = user + " : " + password + " @ " +hostname + ":" + port;
        
        LOG.info("Adding auto-generated user "+user+" @ "+hostname+":"+port);
        
        // Build the command to create the user
        // Note AdminAccess requires _all_ fields set, due to http://code.google.com/p/jclouds/issues/detail?id=1095
        // If jclouds grants Sudo rights, it overwrites the /etc/sudoers, which makes integration tests very dangerous! Not using it.
        AdminAccess adminAccess = AdminAccess.builder()
                .adminUsername(user)
                .adminPassword(password)
                .grantSudoToAdminUser(false)
                .resetLoginPassword(resetPassword)
                .loginPassword(password)
                .authorizeAdminPublicKey(false)
                .adminPublicKey("ignored")
                .installAdminPrivateKey(false)
                .adminPrivateKey("ignore")
                .lockSsh(false)
                .build();
        
        org.jclouds.scriptbuilder.domain.OsFamily scriptOsFamily = (machine.getMachineDetails().getOsDetails().isWindows()) 
                ? org.jclouds.scriptbuilder.domain.OsFamily.WINDOWS
                : org.jclouds.scriptbuilder.domain.OsFamily.UNIX;
        
        InitAdminAccess initAdminAccess = new InitAdminAccess(new AdminAccessConfiguration.Default());
        initAdminAccess.visit(adminAccess);
        String cmd = adminAccess.render(scriptOsFamily);

        // Exec command to create the user
        int result = machine.execScript(ImmutableMap.of(SshTool.PROP_RUN_AS_ROOT.getName(), true), "create-user-"+user, ImmutableList.of(cmd), ImmutableMap.of("PATH", sbinPath()));
        if (result != 0) {
            throw new IllegalStateException("Failed to auto-generate user, using command "+cmd);
        }

        // Exec command to grant password-access to sshd (which may have been disabled earlier).
        cmd = new SshdConfig(ImmutableMap.of("PasswordAuthentication", "yes")).render(scriptOsFamily);
        result = machine.execScript(ImmutableMap.of(SshTool.PROP_RUN_AS_ROOT.getName(), true), "create-user-"+user, ImmutableList.of(cmd), ImmutableMap.of("PATH", sbinPath()));
        if (result != 0) {
            throw new IllegalStateException("Failed to enable ssh-login-with-password, using command "+cmd);
        }

        // Exec command to grant sudo rights.
        if (grantSudo) {
            List<String> cmds = ImmutableList.of(
                    "cat >> /etc/sudoers <<-'END_OF_JCLOUDS_FILE'\n"+
                            user+" ALL = (ALL) NOPASSWD:ALL\n"+
                            "END_OF_JCLOUDS_FILE\n",
                    "chmod 0440 /etc/sudoers");
            result = machine.execScript(ImmutableMap.of(SshTool.PROP_RUN_AS_ROOT.getName(), true), "add-user-to-sudoers-"+user, cmds, ImmutableMap.of("PATH", sbinPath()));
            if (result != 0) {
                throw new IllegalStateException("Failed to auto-generate user, using command "+cmds);
            }
        }
        
        ((EntityLocal)entity).sensors().set(VM_USER_CREDENTIALS, creds);
    }
}
