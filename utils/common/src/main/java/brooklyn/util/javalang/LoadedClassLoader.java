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
package brooklyn.util.javalang;

import java.util.LinkedHashMap;
import java.util.Map;

/** a classloader which allows you to register classes and resources which this loader will return when needed,
 * (essentially a registry rather than a classloader, but useful if you need to make new classes available in
 * an old context) */
public class LoadedClassLoader extends ClassLoader {

    Map<String, Class<?>> loadedClasses = new LinkedHashMap<String, Class<?>>();
    
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> result = loadedClasses.get(name);
        if (result==null) throw new ClassNotFoundException(""+name+" not known here");
        if (resolve) resolveClass(result);
        return result;
    }

    public void addClass(Class<?> clazz) {
        loadedClasses.put(clazz.getName(), clazz);
    }
    
    // TODO could also add resources
    
}
