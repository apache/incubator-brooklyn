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
package brooklyn.util;

import java.util.List;

// FIXME move to brooklyn.util.cli.CommandLineArgs, and change get to "remove"
public class CommandLineUtil {

    public static String getCommandLineOption (List<String> args, String param){
        return getCommandLineOption(args, param, null);
    }

    /** given a list of args, e.g. --name Foo --parent Bob
     * will return "Foo" as param name, and remove those entries from the args list
     */
    public static String getCommandLineOption(List<String> args, String param, String defaultValue) {
        int i = args.indexOf(param);
        if (i >= 0) {
            String result = args.get(i + 1);
            args.remove(i + 1);
            args.remove(i);
            return result;
        } else {
            return defaultValue;
        }
    }

    public static int getCommandLineOptionInt(List<String> args, String param, int defaultValue) {
        String s = getCommandLineOption(args, param,null);
        if (s == null) return defaultValue;
        return Integer.parseInt(s);
    }

    //we don't want instances.
    private CommandLineUtil(){}
}
