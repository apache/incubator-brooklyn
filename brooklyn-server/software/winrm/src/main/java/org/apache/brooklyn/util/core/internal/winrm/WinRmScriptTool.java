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
package org.apache.brooklyn.util.core.internal.winrm;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.location.winrm.NaiveWindowsScriptRunner;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.internal.ssh.ShellTool;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Time;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.apache.brooklyn.core.config.ConfigKeys.newConfigKeyWithDefault;
import static org.apache.brooklyn.util.core.internal.ssh.ShellAbstractTool.getOptionalVal;

/**
 * Implemented like SshCliTool
 */
// TODO Use a general interface for SshTool and WinRmScriptTool with
public class WinRmScriptTool {
    public static final ConfigKey<String> PROP_SCRIPT_DIR = newConfigKeyWithDefault(ShellTool.PROP_SCRIPT_DIR, "The directory where the script should be uploaded. It is an env variable.", "temp");
    public static final ConfigKey<String> PROP_SUMMARY = ShellTool.PROP_SUMMARY;

    private static final String SCRIPT_TEMP_DIR_VARIABLE = "BROOKLYN_TEMP_SCRIPT_DIR";

    private String scriptNameWithoutExtension;
    private String scriptDir;
    private String summary;
    private NaiveWindowsScriptRunner scriptRunner;

    public WinRmScriptTool(Map<String, ?> props, NaiveWindowsScriptRunner scriptRunner) {
        this.scriptDir = getOptionalVal(props, PROP_SCRIPT_DIR);

        String summary = getOptionalVal(props, PROP_SUMMARY);
        if (summary != null) {
            summary = Strings.makeValidFilename(summary);
            if (summary.length() > 30)
                summary = summary.substring(0, 30);
        }

        this.summary = summary;
        this.scriptNameWithoutExtension = "brooklyn-" +
                Time.makeDateStampString() + "-" + Identifiers.makeRandomId(4) +
                (Strings.isBlank(summary) ? "" : "-" + summary);

        this.scriptRunner = scriptRunner;
    }

    public static String psStringExpressionToBatchVariable(String psString, String batchVariable) {
        return "for /f \"delims=\" %%i in ('powershell -noprofile \\'Write-Host \""+ psString +"\"\\') do @set "+batchVariable+ "=%i";
    }

    private String psExtension(String filename) {
        return filename + ".ps1";
    }

    private String batchExtension(String filename) {
        return filename + ".bat";
    }

    public int execPsScript(List<String> psCommands) {
        return execPsScript(ImmutableMap.<String, Object>of(), psCommands);
    }

    public int execPsScript(Map<String, Object> flags, List<String> commands) {
        byte[] scriptBytes = toScript(commands).getBytes();
        String scriptFilename = psExtension(scriptNameWithoutExtension);
        if (flags.containsKey("scriptFilename"))
            flags.put("scriptFilename", scriptFilename);
        String scriptPath = Os.mergePathsWin("$env:"+scriptDir, scriptFilename);
        scriptRunner.copyTo(new ByteArrayInputStream(scriptBytes), scriptPath);
        return scriptRunner.executeNativeOrPsCommand(MutableMap.of(), null, "& " + scriptPath, summary, false);
    }

    public int execNativeScript(List<String> commands) {
        return execNativeScript(ImmutableMap.<String, Object>of(), commands);
    }

    public int execNativeScript(Map<String, Object> flags, List<String> commands) {
        byte[] scriptBytes = toScript(commands).getBytes();
        String scriptFilename = batchExtension(scriptNameWithoutExtension);
        if (flags.containsKey("scriptFilename"))
            flags.put("scriptFilename", scriptFilename);
        scriptRunner.copyTo(new ByteArrayInputStream(scriptBytes), Os.mergePathsWin("$env:"+scriptDir, scriptFilename));
        String scriptPath = Os.mergePathsWin("%"+scriptDir+"%", scriptFilename);
        return scriptRunner.executeNativeOrPsCommand(MutableMap.of(), scriptPath, null, summary, false);
    }

    private String toScript(List<String> commands) {
        return Joiner.on("\r\n").join(commands);
    }
}
