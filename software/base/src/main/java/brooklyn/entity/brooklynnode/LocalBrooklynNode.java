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
package brooklyn.entity.brooklynnode;

import org.apache.brooklyn.api.entity.ImplementedBy;

/**
 * A {@link BrooklynNode} entity that represents the local Brooklyn service.
 * <p>
 * Management username and password can be specified in the {@code brooklyn.properties} file, as
 * either specific username and password (useful when the credentials are set with SHA-256 hashes
 * or via LDAP) or a username with separately configured webconsole plaintext password.
 * <pre>
 * brooklyn.entity.brooklynnode.local.user=admin
 * brooklyn.entity.brooklynnode.local.password=password
 * brooklyn.webconsole.security.user.admin.password=password
 * </pre>
 */
@ImplementedBy(LocalBrooklynNodeImpl.class)
public interface LocalBrooklynNode extends BrooklynNode {
}
