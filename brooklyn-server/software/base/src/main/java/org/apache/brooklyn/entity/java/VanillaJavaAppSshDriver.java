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
package org.apache.brooklyn.entity.java;

import static java.lang.String.format;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.brooklyn.entity.software.base.lifecycle.ScriptHelper;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.file.ArchiveBuilder;
import org.apache.brooklyn.util.core.file.ArchiveUtils;
import org.apache.brooklyn.util.core.internal.ssh.SshTool;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.text.StringEscapes.BashStringEscapes;

import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * The SSH implementation of the {@link VanillaJavaAppDriver}.
 */
public class VanillaJavaAppSshDriver extends JavaSoftwareProcessSshDriver implements VanillaJavaAppDriver {

    // FIXME this should be a config, either on the entity or -- probably better -- 
    // an alternative / override timeout on the SshTool for file copy commands
    final static int NUM_RETRIES_FOR_COPYING = 4;
    
    public VanillaJavaAppSshDriver(VanillaJavaAppImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public VanillaJavaAppImpl getEntity() {
        return (VanillaJavaAppImpl) super.getEntity();
    }

    @Override
    protected String getLogFileLocation() {
        return Os.mergePathsUnix(getRunDir(), "console");
    }

    @Override
    public void install() {
        newScript(INSTALLING).execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING)
                .body.append(format("mkdir -p %s/lib", getRunDir()))
                .failOnNonZeroResultCode()
                .execute();

        SshMachineLocation machine = getMachine();
        VanillaJavaApp entity = getEntity();
        for (String entry : entity.getClasspath()) {
            // If a local folder, then create archive from contents first
            if (Urls.isDirectory(entry)) {
                File jarFile = ArchiveBuilder.jar().addDirContentsAt(new File(entry), "").create();
                entry = jarFile.getAbsolutePath();
            }

            // Determine filename
            String destFile = entry.contains("?") ? entry.substring(0, entry.indexOf('?')) : entry;
            destFile = destFile.substring(destFile.lastIndexOf('/') + 1);

            ArchiveUtils.deploy(MutableMap.<String, Object>of(), entry, machine, getRunDir(), Os.mergePaths(getRunDir(), "lib"), destFile);
        }

        ScriptHelper helper = newScript(CUSTOMIZING+"-classpath")
                .body.append(String.format("ls -1 \"%s\"", Os.mergePaths(getRunDir(), "lib")))
                .gatherOutput();
        helper.setFlag(SshTool.PROP_NO_EXTRA_OUTPUT, true);
        int result = helper.execute();
        if (result != 0) {
            throw new IllegalStateException("Error listing classpath files: " + helper.getResultStderr());
        }
        String stdout = helper.getResultStdout();

        // Transform stdout into list of files in classpath
        if (Strings.isBlank(stdout)) {
            getEntity().sensors().set(VanillaJavaApp.CLASSPATH_FILES, ImmutableList.of(Os.mergePaths(getRunDir(), "lib")));
        } else {
            // FIXME Cannot handle spaces in paths properly
            Iterable<String> lines = Splitter.on(CharMatcher.BREAKING_WHITESPACE).omitEmptyStrings().trimResults().split(stdout);
            Iterable<String> files = Iterables.transform(lines, new Function<String, String>() {
                        @Override
                        public String apply(@Nullable String input) {
                            return Os.mergePathsUnix(getRunDir(), "lib", input);
                        }
                    });
            getEntity().sensors().set(VanillaJavaApp.CLASSPATH_FILES, ImmutableList.copyOf(files));
        }
    }

    public String getClasspath() {
        @SuppressWarnings("unchecked")
        List<String> files = getEntity().getAttribute(VanillaJavaApp.CLASSPATH_FILES);
        if (files == null || files.isEmpty()) {
            return null;
        } else {
            return Joiner.on(":").join(files);
        }
    }

    @Override
    public void launch() {
        String clazz = getEntity().getMainClass();
        String args = getArgs();

        newScript(MutableMap.of(USE_PID_FILE, true), LAUNCHING)
            .body.append(
                    format("echo \"launching: java $JAVA_OPTS %s %s\"", clazz, args),
                    format("java $JAVA_OPTS -cp \"%s\" %s %s >> %s/console 2>&1 </dev/null &", getClasspath(), clazz, args, getRunDir())
                )
            .execute();
    }

    public String getArgs(){
        List<Object> args = getEntity().getConfig(VanillaJavaApp.ARGS);
        StringBuilder sb = new StringBuilder();
        Iterator<Object> it = args.iterator();
        while (it.hasNext()) {
            Object argO = it.next();
            try {
                String arg = Tasks.resolveValue(argO, String.class, getEntity().getExecutionContext());
                BashStringEscapes.assertValidForDoubleQuotingInBash(arg);
                sb.append(format("\"%s\"",arg));
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }
            if (it.hasNext()) {
                sb.append(" ");
            }
        }

        return sb.toString();
    }

    @Override
    public boolean isRunning() {
        int result = newScript(MutableMap.of(USE_PID_FILE, true), CHECK_RUNNING).execute();
        return result == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of(USE_PID_FILE, true), STOPPING).execute();
    }

    @Override
    public void kill() {
        newScript(MutableMap.of(USE_PID_FILE, true), KILLING).execute();
    }

    @Override
    protected Map getCustomJavaSystemProperties() {
        return MutableMap.builder()
                .putAll(super.getCustomJavaSystemProperties())
                .putAll(getEntity().getJvmDefines())
                .build();
    }

    @Override
    protected List<String> getCustomJavaConfigOptions() {
        return MutableList.<String>builder()
                .addAll(super.getCustomJavaConfigOptions())
                .addAll(getEntity().getJvmXArgs())
                .build();
    }

    @Override
    public Map<String,String> getShellEnvironment() {
        return MutableMap.<String,String>builder()
                .putAll(super.getShellEnvironment())
                .putIfNotNull("CLASSPATH", getClasspath())
                .build();
    }
}
