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
package brooklyn.mementos;

import java.util.List;

/**
 * A simple tree structure, where a node references a parent and children using their ids.
 * 
 * e.g. could be used to represent the entity hierarchy within mementos, where the 
 * String is the id of parent/child entities.
 * 
 * @author aled
 */
public interface TreeNode {

    /**
     * The id of this node in the tree. This id will be used by the parent's getChildren(), 
     * and by each child's getParent().
     */
    String getId();
    
    /**
     * The id of the parent entity, or null if none.
     */
    String getParent();
    
    /**
     * The ids of the children.
     */
    List<String> getChildren();
}
