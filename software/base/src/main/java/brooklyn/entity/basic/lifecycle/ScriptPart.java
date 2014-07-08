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
package brooklyn.entity.basic.lifecycle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ScriptPart {
    protected ScriptHelper helper;
    protected List<String> lines = new LinkedList<String>();

    public ScriptPart(ScriptHelper helper) {
        this.helper = helper;
    }

    public ScriptHelper append(CharSequence line) {
        lines.add(line.toString());
        return helper;
    }

    public ScriptHelper append(Collection<? extends CharSequence> lines) {
        for (CharSequence line : lines) {
            append(line);
        }
        return helper;
    }

    public ScriptHelper append(CharSequence... lines) {
        return append(Arrays.asList(lines));
    }

    public ScriptHelper prepend(CharSequence line) {
        lines.add(0, line.toString());
        return helper;
    }

    public ScriptHelper prepend(Collection<? extends CharSequence> lines) {
        List<CharSequence> reversedLines = new ArrayList<CharSequence>(lines);
        Collections.reverse(reversedLines);
        for (CharSequence line : reversedLines) {
            prepend(line);
        }
        return helper;
    }

    public ScriptHelper prepend(CharSequence... lines) {
        return prepend(Arrays.asList(lines));
    }

    public ScriptHelper reset(CharSequence line) {
        return reset(Arrays.asList(line));
    }

    public ScriptHelper reset(List<? extends CharSequence> ll) {
        lines.clear();
        return append(ll);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }
}