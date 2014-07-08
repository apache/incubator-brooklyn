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
package brooklyn.util.net;

/**
 * Encodes URLs, escaping as appropriate.
 * 
 * Copied from fmucar's answer in http://stackoverflow.com/questions/724043/http-url-address-encoding-in-java
 * 
 * TODO Want to use a library utility, but couldn't find this in guava and don't want to introduce
 * dependency on commons-httpclient-3.1 to use URIUtil.
 * 
 * @author aled
 */
public class URLParamEncoder {

    public static String encode(String input) {
        StringBuilder resultStr = new StringBuilder();
        for (char ch : input.toCharArray()) {
            if (isUnsafe(ch)) {
                resultStr.append('%');
                resultStr.append(toHex(ch / 16));
                resultStr.append(toHex(ch % 16));
            } else {
                resultStr.append(ch);
            }
        }
        return resultStr.toString();
    }

    private static char toHex(int ch) {
        return (char) (ch < 10 ? '0' + ch : 'A' + ch - 10);
    }

    private static boolean isUnsafe(char ch) {
        if (ch > 128 || ch < 0)
            return true;
        return (" %$&+,/:;=?@<>#%"
            // these are included in httpclient URI as "unwise", and have been found to be problematic
            // * backslash in a query param breaks URI.create 
            + "\\"
            ).indexOf(ch) >= 0;
    }

}