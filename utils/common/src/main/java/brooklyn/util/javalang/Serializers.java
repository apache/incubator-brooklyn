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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;

import brooklyn.util.stream.Streams;

public class Serializers {

    public interface ObjectReplacer {
        public static final ObjectReplacer NOOP = new ObjectReplacer() {
            @Override public Object replace(Object toserialize) {
                return toserialize;
            }
            @Override public Object resolve(Object todeserialize) {
                return todeserialize;
            }
        };
        
        public Object replace(Object toserialize);
        public Object resolve(Object todeserialize);
    }

    public static <T> T reconstitute(T object) throws IOException, ClassNotFoundException {
        return reconstitute(object, ObjectReplacer.NOOP);
    }
    
    public static <T> T reconstitute(T object, ObjectReplacer replacer) throws IOException, ClassNotFoundException {
        if (object == null) return null;
        return reconstitute(object, object.getClass().getClassLoader(), replacer);
    }
    
    public static <T> T reconstitute(T object, ClassLoader classLoader) throws IOException, ClassNotFoundException {
        return reconstitute(object, classLoader, ObjectReplacer.NOOP);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T reconstitute(T object, ClassLoader classLoader, final ObjectReplacer replacer) throws IOException, ClassNotFoundException {
        if (object == null) return null;
        
        class ReconstitutingObjectOutputStream extends ObjectOutputStream {
            public ReconstitutingObjectOutputStream(OutputStream outputStream) throws IOException {
                super(outputStream);
                enableReplaceObject(true);
            }
            @Override
            protected Object replaceObject(Object obj) throws IOException {
                return replacer.replace(obj);
            }
        };
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ReconstitutingObjectOutputStream(baos);
        oos.writeObject(object);
        oos.close();
        
        class ReconstitutingObjectInputStream extends ClassLoaderObjectInputStream {
            public ReconstitutingObjectInputStream(InputStream inputStream, ClassLoader classLoader) throws IOException {
                super(inputStream, classLoader);
                super.enableResolveObject(true);
            }
            @Override protected Object resolveObject(Object obj) throws IOException {
                return replacer.resolve(obj);
            }
        };
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ReconstitutingObjectInputStream ois = new ReconstitutingObjectInputStream(bais, classLoader);
        try {
            return (T) ois.readObject();
        } finally {
            Streams.closeQuietly(ois);
        }
    }
    
    /**
     * Follows pattern in org.apache.commons.io.input.ClassLoaderObjectInputStream
     */
    public static class ClassLoaderObjectInputStream extends ObjectInputStream {

        private final ClassLoader classLoader;
        
        public ClassLoaderObjectInputStream(InputStream inputStream, ClassLoader classLoader) throws IOException {
            super(inputStream);
            this.classLoader = classLoader;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass objectStreamClass) throws IOException, ClassNotFoundException {
            Class<?> clazz = Class.forName(objectStreamClass.getName(), false, classLoader);

            if (clazz != null) {
                return clazz;
            } else {
                return super.resolveClass(objectStreamClass);
            }
        }
    }
}
