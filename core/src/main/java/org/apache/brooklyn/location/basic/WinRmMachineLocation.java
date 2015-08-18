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
package org.apache.brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.location.MachineDetails;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.OsDetails;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;
import com.google.common.reflect.TypeToken;

import brooklyn.entity.basic.ConfigKeys;

import org.apache.brooklyn.location.access.PortForwardManager;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;

import io.cloudsoft.winrm4j.winrm.WinRmTool;
import io.cloudsoft.winrm4j.winrm.WinRmToolResponse;

public class WinRmMachineLocation extends AbstractLocation implements MachineLocation {

    private static final Logger LOG = LoggerFactory.getLogger(WinRmMachineLocation.class);

    // FIXME Respect `port` config when using {@link WinRmTool}
    public static final ConfigKey<Integer> WINRM_PORT = ConfigKeys.newIntegerConfigKey(
            "port",
            "WinRM port to use when connecting to the remote machine",
            5985);
    
    // TODO merge with {link SshTool#PROP_USER} and {@link SshMachineLocation#user}
    public static final ConfigKey<String> USER = ConfigKeys.newStringConfigKey("user",
            "Username to use when connecting to the remote machine");

    // TODO merge with {link SshTool#PROP_PASSWORD}
    public static final ConfigKey<String> PASSWORD = ConfigKeys.newStringConfigKey("password",
            "Password to use when connecting to the remote machine");

    public static final ConfigKey<Integer> COPY_FILE_CHUNK_SIZE_BYTES = ConfigKeys.newIntegerConfigKey("windows.copy.file.size.bytes",
            "Size of file chunks (in bytes) to be used when copying a file to the remote server", 1024);

     public static final ConfigKey<InetAddress> ADDRESS = ConfigKeys.newConfigKey(
            InetAddress.class,
            "address",
            "Address of the remote machine");

    public static final ConfigKey<Integer> EXECUTION_ATTEMPTS = ConfigKeys.newIntegerConfigKey(
            "windows.exec.attempts",
            "Number of attempts to execute a remote command",
            1);
    
    // TODO See SshTool#PROP_SSH_TRIES, where it was called "sshTries"; remove duplication? Merge into one well-named thing?
    public static final ConfigKey<Integer> EXEC_TRIES = ConfigKeys.newIntegerConfigKey(
            "execTries", 
            "Max number of times to attempt WinRM operations", 
            10);

    public static final ConfigKey<Iterable<String>> PRIVATE_ADDRESSES = ConfigKeys.newConfigKey(
            new TypeToken<Iterable<String>>() {},
            "privateAddresses",
            "Private addresses of this machine, e.g. those within the private network", 
            null);

    public static final ConfigKey<Map<Integer, String>> TCP_PORT_MAPPINGS = ConfigKeys.newConfigKey(
            new TypeToken<Map<Integer, String>>() {},
            "tcpPortMappings",
            "NAT'ed ports, giving the mapping from private TCP port to a public host:port", 
            null);

    @Override
    public InetAddress getAddress() {
        return getConfig(ADDRESS);
    }

    @Override
    public OsDetails getOsDetails() {
        return null;
    }

    @Override
    public MachineDetails getMachineDetails() {
        return null;
    }

    @Nullable
    @Override
    public String getHostname() {
        InetAddress address = getAddress();
        return (address != null) ? address.getHostAddress() : null;
    }

    @Nullable
    protected String getHostAndPort() {
        String host = getHostname();
        return (host == null) ? null : host + ":" + config().get(WINRM_PORT);
    }

    @Override
    public Set<String> getPublicAddresses() {
        InetAddress address = getAddress();
        return (address == null) ? ImmutableSet.<String>of() : ImmutableSet.of(address.getHostAddress());
    }
    
    @Override
    public Set<String> getPrivateAddresses() {
        Iterable<String> result = getConfig(PRIVATE_ADDRESSES);
        return (result == null) ? ImmutableSet.<String>of() : ImmutableSet.copyOf(result);
    }

    public WinRmToolResponse executeScript(String script) {
        return executeScript(ImmutableList.of(script));
    }

    public WinRmToolResponse executeScript(List<String> script) {
        int execTries = getRequiredConfig(EXEC_TRIES);
        Collection<Throwable> exceptions = Lists.newArrayList();
        for (int i = 0; i < execTries; i++) {
            try {
                return executeScriptNoRetry(script);
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                if (i == (execTries+1)) {
                    LOG.info("Propagating WinRM exception (attempt "+(i+1)+" of "+execTries+")", e);
                } else if (i == 0) {
                    LOG.warn("Ignoring WinRM exception and retrying (attempt "+(i+1)+" of "+execTries+")", e);
                } else {
                    LOG.debug("Ignoring WinRM exception and retrying (attempt "+(i+1)+" of "+execTries+")", e);
                }
                exceptions.add(e);
            }
        }
        throw Exceptions.propagate("failed to execute shell script", exceptions);
    }

    protected WinRmToolResponse executeScriptNoRetry(List<String> script) {
        WinRmTool winRmTool = WinRmTool.connect(getHostAndPort(), getUser(), getPassword());
        WinRmToolResponse response = winRmTool.executeScript(script);
        return response;
    }

    public WinRmToolResponse executePsScript(String psScript) {
        return executePsScript(ImmutableList.of(psScript));
    }

    public WinRmToolResponse executePsScript(List<String> psScript) {
        int execTries = getRequiredConfig(EXEC_TRIES);
        Collection<Throwable> exceptions = Lists.newArrayList();
        for (int i = 0; i < execTries; i++) {
            try {
                return executePsScriptNoRetry(psScript);
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                if (i == (execTries+1)) {
                    LOG.info("Propagating WinRM exception (attempt "+(i+1)+" of "+execTries+")", e);
                } else if (i == 0) {
                    LOG.warn("Ignoring WinRM exception and retrying after 5 seconds (attempt "+(i+1)+" of "+execTries+")", e);
                    Time.sleep(Duration.FIVE_SECONDS);
                } else {
                    LOG.debug("Ignoring WinRM exception and retrying after 5 seconds (attempt "+(i+1)+" of "+execTries+")", e);
                    Time.sleep(Duration.FIVE_SECONDS);
                }
                exceptions.add(e);
            }
        }
        throw Exceptions.propagate("failed to execute powershell script", exceptions);
    }

    public WinRmToolResponse executePsScriptNoRetry(List<String> psScript) {
        WinRmTool winRmTool = WinRmTool.connect(getHostAndPort(), getUser(), getPassword());
        WinRmToolResponse response = winRmTool.executePs(psScript);
        return response;
    }

    public int copyTo(File source, String destination) {
        FileInputStream sourceStream = null;
        try {
            sourceStream = new FileInputStream(source);
            return copyTo(sourceStream, destination);
        } catch (FileNotFoundException e) {
            throw Exceptions.propagate(e);
        } finally {
            if (sourceStream != null) {
                Streams.closeQuietly(sourceStream);
            }
        }
    }

    public int copyTo(InputStream source, String destination) {
        executePsScript(ImmutableList.of("rm -ErrorAction SilentlyContinue " + destination));
        try {
            int chunkSize = getConfig(COPY_FILE_CHUNK_SIZE_BYTES);
            byte[] inputData = new byte[chunkSize];
            int bytesRead;
            int expectedFileSize = 0;
            while ((bytesRead = source.read(inputData)) > 0) {
                byte[] chunk;
                if (bytesRead == chunkSize) {
                    chunk = inputData;
                } else {
                    chunk = Arrays.copyOf(inputData, bytesRead);
                }
                executePsScript(ImmutableList.of("If ((!(Test-Path " + destination + ")) -or ((Get-Item '" + destination + "').length -eq " +
                        expectedFileSize + ")) {Add-Content -Encoding Byte -path " + destination +
                        " -value ([System.Convert]::FromBase64String(\"" + new String(Base64.encodeBase64(chunk)) + "\"))}"));
                expectedFileSize += bytesRead;
            }

            return 0;
        } catch (java.io.IOException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public void init() {
        super.init();

        // Register any pre-existing port-mappings with the PortForwardManager
        Map<Integer, String> tcpPortMappings = getConfig(TCP_PORT_MAPPINGS);
        if (tcpPortMappings != null) {
            PortForwardManager pfm = (PortForwardManager) getManagementContext().getLocationRegistry().resolve("portForwardManager(scope=global)");
            for (Map.Entry<Integer, String> entry : tcpPortMappings.entrySet()) {
                int targetPort = entry.getKey();
                HostAndPort publicEndpoint = HostAndPort.fromString(entry.getValue());
                if (!publicEndpoint.hasPort()) {
                    throw new IllegalArgumentException("Invalid portMapping ('"+entry.getValue()+"') for port "+targetPort+" in machine "+this);
                }
                pfm.associate(publicEndpoint.getHostText(), publicEndpoint, this, targetPort);
            }
        }
    }
    public String getUser() {
        return config().get(USER);
    }

    private String getPassword() {
        return config().get(PASSWORD);
    }

    private <T> T getRequiredConfig(ConfigKey<T> key) {
        return checkNotNull(getConfig(key), "key %s must be set", key);
    }
    
    public static String getDefaultUserMetadataString() {
        // Using an encoded command obviates the need to escape
        String unencodePowershell = Joiner.on("\r\n").join(ImmutableList.of(
                // Allow TS connections
                "$RDP = Get-WmiObject -Class Win32_TerminalServiceSetting -ComputerName $env:computername -Namespace root\\CIMV2\\TerminalServices -Authentication PacketPrivacy",
                "$RDP.SetAllowTSConnections(1,1)",
                "Set-ExecutionPolicy Unrestricted -Force",
                // Set unlimited values for remote execution limits
                "Set-Item WSMan:\\localhost\\Shell\\MaxConcurrentUsers 100",
                "Set-Item WSMan:\\localhost\\Shell\\MaxMemoryPerShellMB 0",
                "Set-Item WSMan:\\localhost\\Shell\\MaxProcessesPerShell 0",
                "Set-Item WSMan:\\localhost\\Shell\\MaxShellsPerUser 0",
                "New-ItemProperty \"HKLM:\\System\\CurrentControlSet\\Control\\LSA\" -Name \"SuppressExtendedProtection\" -Value 1 -PropertyType \"DWord\"",
                // The following allows scripts to re-authenticate with local credential - this is required
                // as certain operations cannot be performed with remote credentials
                "$allowed = @('WSMAN/*')",
                "$key = 'hklm:\\SOFTWARE\\Policies\\Microsoft\\Windows\\CredentialsDelegation'",
                "if (!(Test-Path $key)) {",
                "    md $key",
                "}",
                "New-ItemProperty -Path $key -Name AllowFreshCredentials -Value 1 -PropertyType Dword -Force",
                "New-ItemProperty -Path $key -Name AllowFreshCredentialsWhenNTLMOnly -Value 1 -PropertyType Dword -Force",
                "$credKey = Join-Path $key 'AllowFreshCredentials'",
                "if (!(Test-Path $credKey)) {",
                "    md $credkey",
                "}",
                "$ntlmKey = Join-Path $key 'AllowFreshCredentialsWhenNTLMOnly'",
                "if (!(Test-Path $ntlmKey)) {",
                "    md $ntlmKey",
                "}",
                "$i = 1",
                "$allowed |% {",
                "    # Script does not take into account existing entries in this key",
                "    New-ItemProperty -Path $credKey -Name $i -Value $_ -PropertyType String -Force",
                "    New-ItemProperty -Path $ntlmKey -Name $i -Value $_ -PropertyType String -Force",
                "    $i++",
                "}"
        ));

        String encoded = new String(Base64.encodeBase64(unencodePowershell.getBytes(Charsets.UTF_16LE)));
        return "winrm quickconfig -q & " +
                "winrm set winrm/config/service/auth @{Basic=\"true\"} & " +
                "winrm set winrm/config/service/auth @{CredSSP=\"true\"} & " +
                "winrm set winrm/config/client/auth @{CredSSP=\"true\"} & " +
                "winrm set winrm/config/client @{AllowUnencrypted=\"true\"} & " +
                "winrm set winrm/config/service @{AllowUnencrypted=\"true\"} & " +
                "winrm set winrm/config/winrs @{MaxConcurrentUsers=\"100\"} & " +
                "winrm set winrm/config/winrs @{MaxMemoryPerShellMB=\"0\"} & " +
                "winrm set winrm/config/winrs @{MaxProcessesPerShell=\"0\"} & " +
                "winrm set winrm/config/winrs @{MaxShellsPerUser=\"0\"} & " +
                "netsh advfirewall firewall add rule name=RDP dir=in protocol=tcp localport=3389 action=allow profile=any & " +
                "netsh advfirewall firewall add rule name=WinRM dir=in protocol=tcp localport=5985 action=allow profile=any & " +
                "powershell -EncodedCommand " + encoded;
        /* TODO: Find out why scripts with new line characters aren't working on AWS. The following appears as if it *should*
           work but doesn't - the script simply isn't run. By connecting to the machine via RDP, you can get the script
           from 'http://169.254.169.254/latest/user-data', and running it at the command prompt works, but for some
           reason the script isn't run when the VM is provisioned
        */
//        return Joiner.on("\r\n").join(ImmutableList.of(
//                "winrm quickconfig -q",
//                "winrm set winrm/config/service/auth @{Basic=\"true\"}",
//                "winrm set winrm/config/client @{AllowUnencrypted=\"true\"}",
//                "winrm set winrm/config/service @{AllowUnencrypted=\"true\"}",
//                "netsh advfirewall firewall add rule name=RDP dir=in protocol=tcp localport=3389 action=allow profile=any",
//                "netsh advfirewall firewall add rule name=WinRM dir=in protocol=tcp localport=5985 action=allow profile=any",
//                // Using an encoded command necessitates the need to escape. The unencoded command is as follows:
//                // $RDP = Get-WmiObject -Class Win32_TerminalServiceSetting -ComputerName $env:computername -Namespace root\CIMV2\TerminalServices -Authentication PacketPrivacy
//                // $Result = $RDP.SetAllowTSConnections(1,1)
//                "powershell -EncodedCommand JABSAEQAUAAgAD0AIABHAGUAdAAtAFcAbQBpAE8AYgBqAGUAYwB0ACAALQBDAGwAYQBzAHMAI" +
//                        "ABXAGkAbgAzADIAXwBUAGUAcgBtAGkAbgBhAGwAUwBlAHIAdgBpAGMAZQBTAGUAdAB0AGkAbgBnACAALQBDAG8AbQBwA" +
//                        "HUAdABlAHIATgBhAG0AZQAgACQAZQBuAHYAOgBjAG8AbQBwAHUAdABlAHIAbgBhAG0AZQAgAC0ATgBhAG0AZQBzAHAAY" +
//                        "QBjAGUAIAByAG8AbwB0AFwAQwBJAE0AVgAyAFwAVABlAHIAbQBpAG4AYQBsAFMAZQByAHYAaQBjAGUAcwAgAC0AQQB1A" +
//                        "HQAaABlAG4AdABpAGMAYQB0AGkAbwBuACAAUABhAGMAawBlAHQAUAByAGkAdgBhAGMAeQANAAoAJABSAGUAcwB1AGwAd" +
//                        "AAgAD0AIAAkAFIARABQAC4AUwBlAHQAQQBsAGwAbwB3AFQAUwBDAG8AbgBuAGUAYwB0AGkAbwBuAHMAKAAxACwAMQApAA=="
//        ));
    }
}
