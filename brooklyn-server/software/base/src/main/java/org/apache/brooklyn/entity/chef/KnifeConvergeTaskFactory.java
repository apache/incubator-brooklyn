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
package org.apache.brooklyn.entity.chef;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.brooklyn.util.text.StringEscapes.BashStringEscapes.wrapBash;

import java.io.File;
import java.util.List;
import java.util.Map;

import com.google.common.base.Strings;
import com.google.common.net.HostAndPort;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.core.effector.EffectorTasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.Jsonya;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.task.system.ProcessTaskWrapper;
import org.apache.brooklyn.util.ssh.BashCommands;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.gson.GsonBuilder;

public class KnifeConvergeTaskFactory<RET> extends KnifeTaskFactory<RET> {

    private static final Logger log = LoggerFactory.getLogger(KnifeConvergeTaskFactory.class);
    
    protected Function<? super Entity,String> runList;
    protected Map<Object, Object> knifeAttributes = new MutableMap<Object, Object>();
    protected List<String> extraBootstrapParameters = MutableList.of();
    protected Boolean sudo;
    protected Boolean runTwice;
    protected String nodeName;
    protected Integer port;
    /** null means nothing specified, use user supplied or machine default;
     * false means use machine default (disallow user supplied);
     * true means use knife default (omit the argument and disallow user supplied)
     */
    protected Boolean portOmittedToUseKnifeDefault;

    public KnifeConvergeTaskFactory(String taskName) {
        super(taskName);
    }
    
    @Override
    protected KnifeConvergeTaskFactory<RET> self() {
        return this;
    }
    
    /** construct the knife command, based on the settings on other methods
     * (called when instantiating the script, after all parameters sent)
     */
    protected List<String> initialKnifeParameters() {
        // runs inside the task so can detect entity/machine at runtime
        MutableList<String> result = new MutableList<String>();
        SshMachineLocation machine = EffectorTasks.findSshMachine();
        
        result.add("bootstrap");
        result.addAll(extraBootstrapParameters);

        HostAndPort hostAndPort = machine.getSshHostAndPort();
        result.add(wrapBash(hostAndPort.getHostText()));
        Integer whichPort = knifeWhichPort(hostAndPort);
        if (whichPort!=null)
            result.add("-p "+whichPort);

        result.add("-x "+wrapBash(checkNotNull(machine.getUser(), "user")));
        
        File keyfile = ChefServerTasks.extractKeyFile(machine);
        if (keyfile!=null) result.add("-i "+keyfile.getPath());
        else result.add("-P "+checkNotNull(machine.findPassword(), "No password or private key data for "+machine));
        
        result.add("--no-host-key-verify");
        
        if (sudo != Boolean.FALSE) result.add("--sudo");

        if (!Strings.isNullOrEmpty(nodeName)) {
            result.add("--node-name");
            result.add(nodeName);
        }

        result.add("-r "+wrapBash(runList.apply(entity())));
        
        if (!knifeAttributes.isEmpty())
            result.add("-j "+wrapBash(new GsonBuilder().create()
                    .toJson(knifeAttributes)));

        return result;
    }
    
    /** whether knife should attempt to run twice;
     * see {@link ChefConfig#CHEF_RUN_CONVERGE_TWICE} */
    public KnifeConvergeTaskFactory<RET> knifeRunTwice(boolean runTwice) {
        this.runTwice = runTwice;
        return self();
    }
    
    /** whether to pass --sudo to knife; default true */
    public KnifeConvergeTaskFactory<RET> knifeSudo(boolean sudo) {
        this.sudo = sudo;
        return self();
    }

    /** what node name to pass to knife; default = null, meaning chef-client will pick the node name */
    public KnifeConvergeTaskFactory<RET> knifeNodeName(String nodeName) {
        this.nodeName = nodeName;
        return self();
    }

    /** tell knife to use an explicit port */
    public KnifeConvergeTaskFactory<RET> knifePort(int port) {
        if (portOmittedToUseKnifeDefault!=null) {
            log.warn("Port "+port+" specified to "+this+" for when already explicitly told to use a default (overriding previous); see subsequent warning for more details");
        }
        this.port = port;
        return self();
    }

    /** omit the port parameter altogether (let knife use its default) */
    public KnifeConvergeTaskFactory<RET> knifePortUseKnifeDefault() {
        if (port!=null) {
            log.warn("knifePortUseKnifeDefault specified to "+this+" when already told to use "+port+" explicitly (overriding previous); see subsequent warning for more details");
            port = -1;
        }
        portOmittedToUseKnifeDefault = true;
        return self();
    }
    
    /** use the default port known to brooklyn for the target machine for ssh */
    public KnifeConvergeTaskFactory<RET> knifePortUseMachineSshPort() {
        if (port!=null) {
            log.warn("knifePortUseMachineSshPort specified to "+this+" when already told to use "+port+" explicitly (overriding previous); see subsequent warning for more details");
            port = -1;
        }
        portOmittedToUseKnifeDefault = false;
        return self();
    }
    
    protected Integer knifeWhichPort(HostAndPort hostAndPort) {
        if (port==null) {
            if (Boolean.TRUE.equals(portOmittedToUseKnifeDefault))
                // user has explicitly said to use knife default, omitting port here
                return null;
            // default is to use the machine port
            return hostAndPort.getPort();
        }
        if (port==-1) {
            // port was supplied by user, then portDefault (true or false)
            port = null;
            Integer whichPort = knifeWhichPort(hostAndPort);
            log.warn("knife port conflicting instructions for "+this+" at entity "+entity()+" on "+hostAndPort+"; using default ("+whichPort+")");
            return whichPort;
        }
        if (portOmittedToUseKnifeDefault!=null) {
            // portDefault was specified (true or false), then overridden with a port
            log.warn("knife port conflicting instructions for "+this+" at entity "+entity()+" on "+hostAndPort+"; using supplied port "+port);
        }
        // port was supplied by user, use that
        return port;
    }
    
    /** parameters to pass to knife after the bootstrap command */
    public KnifeConvergeTaskFactory<RET> knifeAddExtraBootstrapParameters(String extraBootstrapParameter1, String ...extraBootstrapParameters) {
        this.extraBootstrapParameters.add(extraBootstrapParameter1);
        for (String p: extraBootstrapParameters)
            this.extraBootstrapParameters.add(p);
        return self();
    }
    
    /** function supplying the run list to be passed to knife, evaluated at the last moment */
    public KnifeConvergeTaskFactory<RET> knifeRunList(Function<? super Entity, String> runList) {
        this.runList = runList;
        return self();
    }
    public KnifeConvergeTaskFactory<RET> knifeRunList(String runList) {
        this.runList = Functions.constant(runList);
        return self();
    }
    
    /** includes the given attributes in the attributes to be passed to chef; 
     * when combining with other attributes, this uses {@link Jsonya} semantics to add 
     * (a deep add, combining lists and maps) */
    public KnifeConvergeTaskFactory<RET> knifeAddAttributes(Map<? extends Object, ? extends Object> attributes) {
        if (attributes!=null && !attributes.isEmpty()) {
            Jsonya.of(knifeAttributes).add(attributes);
        }
        return self();
    }
    
    protected String buildKnifeCommand(int knifeCommandIndex) {
        String result = super.buildKnifeCommand(knifeCommandIndex);
        if (Boolean.TRUE.equals(runTwice))
            result = BashCommands.alternatives(result, result);
        return result;
    }

    @Override
    public <T2> KnifeConvergeTaskFactory<T2> returning(ScriptReturnType type) {
        return (KnifeConvergeTaskFactory<T2>) super.<T2>returning(type);
    }

    @Override
    public <RET2> KnifeConvergeTaskFactory<RET2> returning(Function<ProcessTaskWrapper<?>, RET2> resultTransformation) {
        return (KnifeConvergeTaskFactory<RET2>) super.returning(resultTransformation);
    }
    
    @Override
    public KnifeConvergeTaskFactory<Boolean> returningIsExitCodeZero() {
        return (KnifeConvergeTaskFactory<Boolean>) super.returningIsExitCodeZero();
    }
    
    @Override
    public KnifeConvergeTaskFactory<String> requiringZeroAndReturningStdout() {
        return (KnifeConvergeTaskFactory<String>) super.requiringZeroAndReturningStdout();
    }

    public KnifeConvergeTaskFactory<RET> knifeAddParameters(String word1, String ...words) {
        super.knifeAddParameters(word1, words);
        return self();
    }

    // TODO other methods from KnifeTaskFactory will return KTF class not KCTF;
    // should make it generic so it returns the right type...
}