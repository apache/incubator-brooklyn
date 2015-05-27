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
package brooklyn.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IConfigurationListener;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

public class NoPortsAvailableFailureListener implements ITestListener, IConfigurationListener {
    private static final Logger log = LoggerFactory.getLogger(NoPortsAvailableFailureListener.class);

    @Override
    public void onTestStart(ITestResult result) {

    }

    @Override
    public void onTestSuccess(ITestResult result) {

    }

    @Override
    public void onTestFailure(ITestResult result) {
        onFailure(result.getThrowable());
    }

    @Override
    public void onTestSkipped(ITestResult result) {
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
    }

    @Override
    public void onStart(ITestContext context) {
    }

    @Override
    public void onFinish(ITestContext context) {
    }

    @Override
    public void onConfigurationSuccess(ITestResult itr) {
    }

    @Override
    public void onConfigurationFailure(ITestResult itr) {
        onFailure(itr.getThrowable());
    }

    @Override
    public void onConfigurationSkip(ITestResult itr) {
    }

    private void onFailure(Throwable failure) {
        if (failure == null) return;
        String msg = failure.getMessage();
        if (msg == null) return;

        if (msg.contains("unable to find a free port") ||
                msg.contains("Port already in use")) {
            //let's dump some info to help troubleshoot this
            log("Network interfaces", "ifconfig");
            log("Network interfaces", "ip link list");
            log("IPv6 interfaces", "cat /proc/net/if_inet6");
            log("Multicast membership", "netstat -g");
            log("Open ports", "netstat -nap");
            log("Running processes", "ps aux");
            log("User limits - soft", "bash -c 'ulimit -Sa'");
            log("User limits - hard", "bash -c 'ulimit -Ha'");
            log("Open handles count", "bash -c 'lsof | wc -l'");
            log("Open handles count - alternative", "sysctl fs.file-nr");
            log("Open handles count - alternative 2", "cat /proc/sys/fs/file-nr");
            log("Allowed port range", "sysctl net.ipv4.ip_local_port_range");
            log("Fetch a page", "curl https://raw.githubusercontent.com/apache/incubator-brooklyn/master/.gitattributes");
        }
    }

    private String exec(String cmd) throws IOException, InterruptedException {
        String[] args = translateCommandline(cmd);
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream();
        Process p = pb.start();
        Reader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        char[] buff = new char[1024];
        int read;
        StringBuilder result = new StringBuilder();
        while ((read = reader.read(buff)) != -1) {
            result.append(buff, 0, read);
        }
        p.waitFor();
        return result.toString();
    }

    private void log(String msg, String cmd) {
        try {
            log.info(msg + "\n$ " + cmd + "\n" + exec(cmd) + "\n\n");
        } catch (Exception e) {
            log.info("Command failed: " + cmd, e);
        }
    }

    //From org.apache.tools.ant.types.Commandline
    public static String[] translateCommandline(String toProcess) {
        if (toProcess == null || toProcess.length() == 0) {
            //no command? no string
            return new String[0];
        }
        // parse with a simple finite state machine

        final int normal = 0;
        final int inQuote = 1;
        final int inDoubleQuote = 2;
        int state = normal;
        StringTokenizer tok = new StringTokenizer(toProcess, "\"\' ", true);
        Collection<String> v = new ArrayList<String>();
        StringBuffer current = new StringBuffer();
        boolean lastTokenHasBeenQuoted = false;

        while (tok.hasMoreTokens()) {
            String nextTok = tok.nextToken();
            switch (state) {
            case inQuote:
                if ("\'".equals(nextTok)) {
                    lastTokenHasBeenQuoted = true;
                    state = normal;
                } else {
                    current.append(nextTok);
                }
                break;
            case inDoubleQuote:
                if ("\"".equals(nextTok)) {
                    lastTokenHasBeenQuoted = true;
                    state = normal;
                } else {
                    current.append(nextTok);
                }
                break;
            default:
                if ("\'".equals(nextTok)) {
                    state = inQuote;
                } else if ("\"".equals(nextTok)) {
                    state = inDoubleQuote;
                } else if (" ".equals(nextTok)) {
                    if (lastTokenHasBeenQuoted || current.length() != 0) {
                        v.add(current.toString());
                        current = new StringBuffer();
                    }
                } else {
                    current.append(nextTok);
                }
                lastTokenHasBeenQuoted = false;
                break;
            }
        }
        if (lastTokenHasBeenQuoted || current.length() != 0) {
            v.add(current.toString());
        }
        if (state == inQuote || state == inDoubleQuote) {
            throw new RuntimeException("unbalanced quotes in " + toProcess);
        }
        return v.toArray(new String[v.size()]);
    }

}
