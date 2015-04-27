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
package brooklyn.location.basic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.commons.codec.binary.Base64;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.location.MachineDetails;
import brooklyn.location.MachineLocation;
import brooklyn.location.OsDetails;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;
import io.cloudsoft.winrm4j.winrm.WinRmTool;
import io.cloudsoft.winrm4j.winrm.WinRmToolResponse;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;

public class WinRmMachineLocation extends AbstractLocation implements MachineLocation {

    public static final ConfigKey<String> WINDOWS_USERNAME = ConfigKeys.newStringConfigKey("windows.username",
            "Username to use when connecting to the remote machine");

    public static final ConfigKey<String> WINDOWS_PASSWORD = ConfigKeys.newStringConfigKey("windows.password",
            "Password to use when connecting to the remote machine");

    public static final ConfigKey<Integer> COPY_FILE_CHUNK_SIZE_BYTES = ConfigKeys.newIntegerConfigKey("windows.copy.file.size.bytes",
            "Size of file chunks (in bytes) to be used when copying a file to the remote server", 1024);

    @SetFromFlag
    protected String user;

    @SetFromFlag(nullable = false)
    protected InetAddress address;

    @Override
    public InetAddress getAddress() {
        return address;
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
        return address.getHostAddress();
    }

    @Override
    public Set<String> getPublicAddresses() {
        return null;
    }

    @Override
    public Set<String> getPrivateAddresses() {
        return null;
    }

    public int executeScript(String script) {
        return executeScript(ImmutableList.of(script));
    }

    public int executeScript(List<String> script) {
        WinRmTool winRmTool = WinRmTool.connect(getHostname(), getUsername(), getPassword());
        WinRmToolResponse response = winRmTool.executeScript(script);
        return response.getStatusCode();
    }

    public int executePsScript(String psScript) {
        return executePsScript(ImmutableList.of(psScript));
    }

    public int executePsScript(List<String> psScript) {
        WinRmTool winRmTool = WinRmTool.connect(getHostname(), getUsername(), getPassword());
        WinRmToolResponse response = winRmTool.executePs(psScript);
        return response.getStatusCode();
    }

    public int copyTo(File source, File destination) {
        try {
            return copyTo(new FileInputStream(source), destination);
        } catch (FileNotFoundException e) {
            throw Exceptions.propagate(e);
        }
    }

    public int copyTo(InputStream source, File destination) {
        executePsScript(ImmutableList.of("rm -ErrorAction SilentlyContinue " + destination.getPath()));
        try {
            int chunkSize = getConfig(COPY_FILE_CHUNK_SIZE_BYTES);
            byte[] inputData = new byte[chunkSize];
            int bytesRead;
            while ((bytesRead = source.read(inputData)) > 0) {
                byte[] chunk;
                if (bytesRead == chunkSize) {
                    chunk = inputData;
                } else {
                    chunk = Arrays.copyOf(inputData, bytesRead);
                }
                executePsScript(ImmutableList.of("Add-Content -Encoding Byte -path " + destination.getPath() +
                        " -value ([System.Convert]::FromBase64String(\"" + new String(Base64.encodeBase64(chunk)) + "\"))"));
            }

            return 0;
        } catch (java.io.IOException e) {
            throw Exceptions.propagate(e);
        }
    }

    public String getUsername() {
        return config().get(WINDOWS_USERNAME);
    }

    private String getPassword() {
        return config().get(WINDOWS_PASSWORD);
    }

    public static String getDefaultUserMetadataString() {
        // Using an encoded command obviates the need to escape
        String unencodePowershell =
                "$RDP = Get-WmiObject -Class Win32_TerminalServiceSetting -ComputerName $env:computername -Namespace root\\CIMV2\\TerminalServices -Authentication PacketPrivacy\r\n" +
                "$RDP.SetAllowTSConnections(1,1)\r\n" +
                "Set-ExecutionPolicy Unrestricted\r\n" +
                "Set-Item WSMan:\\localhost\\Shell\\MaxConcurrentUsers 100\r\n" +
                "Set-Item WSMan:\\localhost\\Shell\\MaxMemoryPerShellMB 0\r\n" +
                "Set-Item WSMan:\\localhost\\Shell\\MaxProcessesPerShell 0\r\n" +
                "Set-Item WSMan:\\localhost\\Shell\\MaxShellsPerUser 0\r\n" +
                "New-ItemProperty \"HKLM:\\System\\CurrentControlSet\\Control\\LSA\" -Name \"SuppressExtendedProtection\" -Value 1 -PropertyType \"DWord\"";
//                "New-ItemProperty \"HKLM:\\System\\CurrentControlSet\\Control\\LSA\" -Name \"LmCompatibilityLevel\" -Value 3 -PropertyType \"DWord\" \r\n";

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
