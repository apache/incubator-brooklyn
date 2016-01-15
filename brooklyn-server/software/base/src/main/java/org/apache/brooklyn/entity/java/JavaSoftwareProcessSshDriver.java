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

import static org.apache.brooklyn.util.JavaGroovyEquivalents.groovyTruth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.core.effector.EffectorTasks;
import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessSshDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.internal.Primitives;

import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.core.internal.ssh.ShellTool;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.core.task.ssh.SshTasks;
import org.apache.brooklyn.util.core.task.system.ProcessTaskFactory;
import org.apache.brooklyn.util.core.task.system.ProcessTaskWrapper;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.text.StringEscapes.BashStringEscapes;

/**
 * The SSH implementation of the {@link org.apache.brooklyn.entity.java.JavaSoftwareProcessDriver}.
 */
public abstract class JavaSoftwareProcessSshDriver extends AbstractSoftwareProcessSshDriver implements JavaSoftwareProcessDriver {

    public static final Logger log = LoggerFactory.getLogger(JavaSoftwareProcessSshDriver.class);

    public static final List<List<String>> MUTUALLY_EXCLUSIVE_OPTS = ImmutableList.<List<String>> of(ImmutableList.of("-client",
            "-server"));

    public static final List<String> KEY_VAL_OPT_PREFIXES = ImmutableList.of("-Xmx", "-Xms", "-Xss");

    public JavaSoftwareProcessSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);

        entity.sensors().set(Attributes.LOG_FILE_LOCATION, getLogFileLocation());
    }

    protected abstract String getLogFileLocation();

    public boolean isJmxEnabled() {
        return (entity instanceof UsesJmx) && (entity.getConfig(UsesJmx.USE_JMX));
    }

    public boolean isJmxSslEnabled() {
        return isJmxEnabled() && groovyTruth(entity.getConfig(UsesJmx.JMX_SSL_ENABLED));
    }

    /**
     * Sets all JVM options (-X.. -D..) in an environment var JAVA_OPTS.
     * <p>
     * That variable is constructed from {@link #getJavaOpts()}, then wrapped _unescaped_ in double quotes. An
     * error is thrown if there is an unescaped double quote in the string. All other unescaped
     * characters are permitted, but unless $var expansion or `command` execution is desired (although
     * this is not confirmed as supported) the generally caller should escape any such characters, for
     * example using {@link BashStringEscapes#escapeLiteralForDoubleQuotedBash(String)}.
     */
    @Override
    public Map<String, String> getShellEnvironment() {
        List<String> javaOpts = getJavaOpts();

        for (String it : javaOpts) {
            BashStringEscapes.assertValidForDoubleQuotingInBash(it);
        }
        // do not double quote here; the env var is double quoted subsequently;
        // spaces should be preceded by double-quote
        // (if dbl quotes are needed we could pass on the command-line instead of in an env var)
        String sJavaOpts = Joiner.on(' ').join(javaOpts);
        return MutableMap.<String, String>builder().putAll(super.getShellEnvironment()).put("JAVA_OPTS", sJavaOpts).build();
    }

    /**
     * arguments to pass to the JVM; this is the config options (e.g. -Xmx1024; only the contents of
     * {@link #getCustomJavaConfigOptions()} by default) and java system properties (-Dk=v; add custom
     * properties in {@link #getCustomJavaSystemProperties()})
     * <p>
     * See {@link #getShellEnvironment()} for discussion of quoting/escaping strategy.
     **/
    public List<String> getJavaOpts() {
        Iterable<String> sysprops = Iterables.transform(getJavaSystemProperties().entrySet(),
                new Function<Map.Entry<String, ?>, String>() {
                    public String apply(Map.Entry<String, ?> entry) {
                        String k = entry.getKey();
                        Object v = entry.getValue();
                        try {
                            if (v != null && Primitives.isWrapperType(v.getClass())) {
                                v = "" + v;
                            } else {
                                v = Tasks.resolveValue(v, Object.class, ((EntityInternal)entity).getExecutionContext());
                                if (v == null) {
                                } else if (v instanceof CharSequence) {
                                } else if (TypeCoercions.isPrimitiveOrBoxer(v.getClass())) {
                                    v = "" + v;
                                } else {
                                    // could do toString, but that's likely not what is desired;
                                    // probably a type mismatch,
                                    // post-processing should be specified (common types are accepted
                                    // above)
                                    throw new IllegalArgumentException("cannot convert value " + v + " of type " + v.getClass()
                                            + " to string to pass as JVM property; use a post-processor");
                                }
                            }
                            return "-D" + k + (v != null ? "=" + v : "");
                        } catch (Exception e) {
                            log.warn("Error resolving java option key {}, propagating: {}", k, e);
                            throw Throwables.propagate(e);
                        }
                    }
                });

        Set<String> result = MutableSet.<String> builder().
                addAll(getJmxJavaConfigOptions()).
                addAll(getCustomJavaConfigOptions()).
                addAll(sysprops).
            build();

        for (String customOpt : entity.getConfig(UsesJava.JAVA_OPTS)) {
            for (List<String> mutuallyExclusiveOpt : MUTUALLY_EXCLUSIVE_OPTS) {
                if (mutuallyExclusiveOpt.contains(customOpt)) {
                    result.removeAll(mutuallyExclusiveOpt);
                }
            }
            for (String keyValOptPrefix : KEY_VAL_OPT_PREFIXES) {
                if (customOpt.startsWith(keyValOptPrefix)) {
                    for (Iterator<String> iter = result.iterator(); iter.hasNext();) {
                        String existingOpt = iter.next();
                        if (existingOpt.startsWith(keyValOptPrefix)) {
                            iter.remove();
                        }
                    }
                }
            }
            if (customOpt.contains("=")) {
                String customOptPrefix = customOpt.substring(0, customOpt.indexOf("="));

                for (Iterator<String> iter = result.iterator(); iter.hasNext();) {
                    String existingOpt = iter.next();
                    if (existingOpt.startsWith(customOptPrefix)) {
                        iter.remove();
                    }
                }
            }
            result.add(customOpt);
        }

        return ImmutableList.copyOf(result);
    }

    /**
     * Returns the complete set of Java system properties (-D defines) to set for the application.
     * <p>
     * This is exposed to the JVM as the contents of the {@code JAVA_OPTS} environment variable. Default
     * set contains config key, custom system properties, and JMX defines.
     * <p>
     * Null value means to set -Dkey otherwise it is -Dkey=value.
     * <p>
     * See {@link #getShellEnvironment()} for discussion of quoting/escaping strategy.
     */
    protected Map<String,?> getJavaSystemProperties() {
        return MutableMap.<String,Object>builder()
                .putAll(getCustomJavaSystemProperties())
                .putAll(isJmxEnabled() ? getJmxJavaSystemProperties() : Collections.<String,Object>emptyMap())
                .putAll(entity.getConfig(UsesJava.JAVA_SYSPROPS))
                .build();
    }

    /**
     * Return extra Java system properties (-D defines) used by the application.
     *
     * Override as needed; default is an empty map.
     */
    protected Map getCustomJavaSystemProperties() {
        return Maps.newLinkedHashMap();
    }

    /**
     * Return extra Java config options, ie arguments starting with - which are passed to the JVM prior
     * to the class name.
     * <p>
     * Note defines are handled separately, in {@link #getCustomJavaSystemProperties()}.
     * <p>
     * Override as needed; default is an empty list.
     */
    protected List<String> getCustomJavaConfigOptions() {
        return Lists.newArrayList();
    }

    /** @deprecated since 0.6.0, the config key is always used instead of this */ @Deprecated
    public Integer getJmxPort() {
        return !isJmxEnabled() ? Integer.valueOf(-1) : entity.getAttribute(UsesJmx.JMX_PORT);
    }

    /** @deprecated since 0.6.0, the config key is always used instead of this */ @Deprecated
    public Integer getRmiRegistryPort() {
        return !isJmxEnabled() ? -1 : entity.getAttribute(UsesJmx.RMI_REGISTRY_PORT);
    }

    /** @deprecated since 0.6.0, the config key is always used instead of this */ @Deprecated
    public String getJmxContext() {
        return !isJmxEnabled() ? null : entity.getAttribute(UsesJmx.JMX_CONTEXT);
    }

    /**
     * Return the configuration properties required to enable JMX for a Java application.
     *
     * These should be set as properties in the {@code JAVA_OPTS} environment variable when calling the
     * run script for the application.
     */
    protected Map<String, ?> getJmxJavaSystemProperties() {
        MutableMap.Builder<String, Object> result = MutableMap.<String, Object> builder();

        if (isJmxEnabled()) {
            new JmxSupport(getEntity(), getRunDir()).applyJmxJavaSystemProperties(result);
        }

        return result.build();
    }

    /**
     * Return any JVM arguments required, other than the -D defines returned by {@link #getJmxJavaSystemProperties()}
     */
    protected List<String> getJmxJavaConfigOptions() {
        List<String> result = new ArrayList<String>();
        if (isJmxEnabled()) {
            result.addAll(new JmxSupport(getEntity(), getRunDir()).getJmxJavaConfigOptions());
        }
        return result;
    }

    /**
     * Checks for the presence of Java on the entity's location, installing if necessary.
     * @return true if the required version of Java was found on the machine or if it was installed correctly,
     * otherwise false.
     */
    protected boolean checkForAndInstallJava(String requiredVersion) {
        int requiredJavaMinor;
        if (requiredVersion.contains(".")) {
            List<String> requiredVersionParts = Splitter.on(".").splitToList(requiredVersion);
            requiredJavaMinor = Integer.valueOf(requiredVersionParts.get(1));
        } else if (requiredVersion.length() == 1) {
            requiredJavaMinor = Integer.valueOf(requiredVersion);
        } else {
            log.error("java version required {} is not supported", requiredVersion);
            throw new IllegalArgumentException("Required java version " + requiredVersion + " not supported");
        }
        Optional<String> installedJavaVersion = getInstalledJavaVersion();
        if (installedJavaVersion.isPresent()) {
            List<String> installedVersionParts = Splitter.on(".").splitToList(installedJavaVersion.get());
            int javaMajor = Integer.valueOf(installedVersionParts.get(0));
            int javaMinor = Integer.valueOf(installedVersionParts.get(1));
            if (javaMajor == 1 && javaMinor >= requiredJavaMinor) {
                log.debug("Java {} already installed at {}@{}", new Object[]{installedJavaVersion.get(), getEntity(), getLocation()});
                return true;
            }
        }
        return tryJavaInstall(requiredVersion, BashCommands.installJava(requiredJavaMinor)) == 0;
    }

    protected int tryJavaInstall(String version, String command) {
        getLocation().acquireMutex("installing", "installing Java at " + getLocation());
        try {
            log.debug("Installing Java {} at {}@{}", new Object[]{version, getEntity(), getLocation()});
            ProcessTaskFactory<Integer> taskFactory = SshTasks.newSshExecTaskFactory(getLocation(), command)
                    .summary("install java ("+version+")")
                    .configure(ShellTool.PROP_EXEC_ASYNC, true);
            ProcessTaskWrapper<Integer> installCommand = Entities.submit(getEntity(), taskFactory);
            int result = installCommand.get();
            if (result != 0) {
                log.warn("Installation of Java {} failed at {}@{}: {}",
                        new Object[]{version, getEntity(), getLocation(), installCommand.getStderr()});
            }
            return result;
        } finally {
            getLocation().releaseMutex("installing");
        }
    }

    /**
    * @deprecated since 0.7.0; instead use {@link #getInstalledJavaVersion()}
    */
    @Deprecated
    protected Optional<String> getCurrentJavaVersion() {
        return getInstalledJavaVersion();
    }

    /**
     * Checks for the version of Java installed on the entity's location over SSH.
     * @return An Optional containing the version portion of `java -version`, or absent if no Java found.
     */
    protected Optional<String> getInstalledJavaVersion() {
        log.debug("Checking Java version at {}@{}", getEntity(), getLocation());
        // sed gets stdin like 'java version "1.7.0_45"'
        ProcessTaskWrapper<Integer> versionCommand = Entities.submit(getEntity(), SshTasks.newSshExecTaskFactory(
                getLocation(), "java -version 2>&1 | grep \" version\" | sed 's/.*\"\\(.*\\).*\"/\\1/'"));
        versionCommand.get();
        String stdOut = versionCommand.getStdout().trim();
        if (!Strings.isBlank(stdOut)) {
            log.debug("Found Java version at {}@{}: {}", new Object[] {getEntity(), getLocation(), stdOut});
            return Optional.of(stdOut);
        } else {
            log.debug("Found no Java installed at {}@{}", getEntity(), getLocation());
            return Optional.absent();
        }
    }

    /**
     * Answers one of "OpenJDK", "Oracle", or other vendor info.
     */
    protected Optional<String> getCurrentJavaVendor() {
        // TODO Also handle IBM jvm
        log.debug("Checking Java vendor at {}@{}", getEntity(), getLocation());
        ProcessTaskWrapper<Integer> versionCommand = Entities.submit(getEntity(), SshTasks.newSshExecTaskFactory(
                getLocation(), "java -version 2>&1 | awk 'NR==2 {print $1}'"));
        versionCommand.get();
        String stdOut = versionCommand.getStdout().trim();
        if (Strings.isBlank(stdOut)) {
            log.debug("Found no Java installed at {}@{}", getEntity(), getLocation());
            return Optional.absent();
        } else if ("Java(TM)".equals(stdOut)) {
            log.debug("Found Java version at {}@{}: {}", new Object[] {getEntity(), getLocation(), stdOut});
            return Optional.of("Oracle");
        } else {
            return Optional.of(stdOut);
        }
    }

    /**
     * Checks for Java 6 or 7, installing Java 7 if neither are found. Override this method to
     * check for and install specific versions of Java.
     *
     * @see #checkForAndInstallJava(String)
     */
    public boolean installJava() {
        if (entity instanceof UsesJava) {
            String version = entity.getConfig(UsesJava.JAVA_VERSION_REQUIRED);
            return checkForAndInstallJava(version);
        }
        // by default it installs jdk7
        return checkForAndInstallJava("1.7");
    }

    public void installJmxSupport() {
        if (isJmxEnabled()) {
            newScript("JMX_SETUP_PREINSTALL").body.append("mkdir -p "+getRunDir()).execute();
            new JmxSupport(getEntity(), getRunDir()).install();
        }
    }

    public void checkJavaHostnameBug() {
        checkNoHostnameBug();

        try {
            ProcessTaskWrapper<Integer> hostnameTask = DynamicTasks.queue(SshEffectorTasks.ssh("echo FOREMARKER; hostname -f; echo AFTMARKER")).block();
            String stdout = Strings.getFragmentBetween(hostnameTask.getStdout(), "FOREMARKER", "AFTMARKER");
            if (hostnameTask.getExitCode() == 0 && Strings.isNonBlank(stdout)) {
                String hostname = stdout.trim();
                Integer len = hostname.length();
                if (len > 63) {
                    // likely to cause a java crash due to java bug 7089443 -- set a new short hostname
                    // http://mail.openjdk.java.net/pipermail/net-dev/2012-July/004603.html
                    String newHostname = "br-"+getEntity().getId().toLowerCase();
                    log.info("Detected likelihood of Java hostname bug with hostname length "+len+" for "+getEntity()+"; renaming "+getMachine()+"  to hostname "+newHostname);
                    DynamicTasks.queue(SshEffectorTasks.ssh(BashCommands.setHostname(newHostname, null))).block();
                }
            } else {
                log.debug("Hostname length could not be determined for location "+EffectorTasks.findSshMachine()+"; not doing Java hostname bug check");
            }
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            log.warn("Error checking/fixing Java hostname bug (continuing): "+e, e);
        }
    }

    @Override
    public void setup() {
        DynamicTasks.queue("install java", new Runnable() { public void run() {
            installJava();
        }});

        // TODO check java version

        if (getEntity().getConfig(UsesJava.CHECK_JAVA_HOSTNAME_BUG)) {
            DynamicTasks.queue("check java hostname bug", new Runnable() { public void run() {
                checkJavaHostnameBug(); }});
        }
    }

    @Override
    public void copyRuntimeResources() {
        super.copyRuntimeResources();

        if (isJmxEnabled()) {
            DynamicTasks.queue("install jmx", new Runnable() { public void run() {
                installJmxSupport(); }});
        }
    }

}
