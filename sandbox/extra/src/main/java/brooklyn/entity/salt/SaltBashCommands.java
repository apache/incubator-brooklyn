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
package brooklyn.entity.salt;

import static brooklyn.util.ssh.BashCommands.downloadToStdout;

import javax.annotation.Nullable;

import org.apache.commons.io.FilenameUtils;

import brooklyn.entity.chef.ChefBashCommands;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;

import com.google.common.annotations.Beta;
import com.google.common.io.Files;

/**
 * BASH commands useful for setting up SaltStack.
 */
@Beta
public class SaltBashCommands {

    /**
     * SaltStack formulas can be found at {@code https://github.com/saltstack-formulas} as repositories.
     * <p>
     * This assumes the download is an archive containing a single directory on the root which will
     * be renamed to {@code formulaName}. if that directory already has the correct name {@code formulaName}
     * can be null, but if taking from a GitHub tarball it will typically be of the form {@code formulaName-master/}
     * hence the renaming.
     */
    // TODO support installing from classpath, and using the repository (tie in with those methods)
    public static final String downloadAndExpandFormula(String source, @Nullable String formulaName, boolean force) {
        String dl = downloadAndExpandFormula(source);
        if (formulaName==null) return dl;
        String tmpName = "tmp-"+Strings.makeValidFilename(formulaName)+"-"+Identifiers.makeRandomId(4);
        String installCmd = BashCommands.chain("mkdir "+tmpName, "cd "+tmpName, dl,
                BashCommands.requireTest("`ls | wc -w` -eq 1", "The archive must contain exactly one directory"),
                "FORMULA_EXPANDED_DIR=`ls`",
                "mv $FORMULA_EXPANDED_DIR '../"+formulaName+"'",
                "cd ..",
                "rm -rf "+tmpName);
        if (!force) return BashCommands.alternatives("ls "+formulaName, installCmd);
        else return BashCommands.alternatives("rm -rf "+formulaName, installCmd);
    }

    /**
     * Same as {@link #downloadAndExpandFormula(String, String)} with no formula name.
     * <p>
     * Equivalent to the following command, but substituting the given {@code sourceUrl}.
     * <pre>{@code
     * curl -f -L  https://github.com/saltstack-formulas/nginx-formula/archive/master.tar.gz | tar xvz
     * }</pre>
     */
    public static final String downloadAndExpandFormula(String sourceUrl) {
        String ext = Files.getFileExtension(sourceUrl);
        if ("tar".equalsIgnoreCase(ext))
            return downloadToStdout(sourceUrl) + " | tar xv";
        if ("tgz".equalsIgnoreCase(ext) || sourceUrl.toLowerCase().endsWith(".tar.gz"))
            return downloadToStdout(sourceUrl) + " | tar xvz";

        String target = FilenameUtils.getName(sourceUrl);
        if (target==null) target = ""; else target = target.trim();
        target += "_"+Strings.makeRandomId(4);

        if ("zip".equalsIgnoreCase(ext) || "tar.gz".equalsIgnoreCase(ext))
            return BashCommands.chain(
                BashCommands.commandToDownloadUrlAs(sourceUrl, target),
                "unzip "+target,
                "rm "+target);

        throw new UnsupportedOperationException("No way to expand "+sourceUrl+" (yet)");
    }

}
