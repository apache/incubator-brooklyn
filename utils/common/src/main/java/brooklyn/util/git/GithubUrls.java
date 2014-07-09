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
package brooklyn.util.git;

import brooklyn.util.net.Urls;

public class GithubUrls {

    public static String BASE_URL = "https://github.com/";
    
    /** returns URL for the root of the given repo */
    public static String root(String owner, String repo) {
        return Urls.mergePaths(BASE_URL, owner, repo);
    }
    
    /** returns URL for downloading a .tar.gz version of a tag of a repository */
    public static String tgz(String owner, String repo, String tag) {
        return Urls.mergePaths(root(owner, repo), "archive", tag+".tar.gz");
    }

    /** returns URL for downloading a .zip version of a tag of a repository */
    public static String zip(String owner, String repo, String tag) {
        return Urls.mergePaths(root(owner, repo), "archive", tag+".zip");
    }

}
