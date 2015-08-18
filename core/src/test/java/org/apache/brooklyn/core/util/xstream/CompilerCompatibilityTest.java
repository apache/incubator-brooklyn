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
package org.apache.brooklyn.core.util.xstream;

import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;

import org.apache.brooklyn.core.util.xstream.CompilerIndependentOuterClassFieldMapper;
import org.apache.brooklyn.core.util.xstream.CompilerCompatibilityTest.EnclosingClass.DynamicClass;
import org.apache.brooklyn.core.util.xstream.CompilerCompatibilityTest.EnclosingClass.DynamicExtendingClass;
import org.apache.brooklyn.core.util.xstream.CompilerCompatibilityTest.EnclosingClass.EnclosingDynamicClass;
import org.apache.brooklyn.core.util.xstream.CompilerCompatibilityTest.EnclosingClass.EnclosingDynamicClass.NestedDynamicClass;
import org.eclipse.jetty.util.log.Log;
import org.testng.annotations.Test;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.mapper.MapperWrapper;

// To get the generated synthetic fields use the command:
/*
   find core/target/test-classes -name CompilerCompatibilityTest\$EnclosingClass\$* | \
   sed s@core/target/test-classes/@@ | sed 's@.class$@@' | sed s@/@.@g | \
   xargs javap -classpath core/target/test-classes/ | grep -B1 this
*/
@SuppressWarnings("unused")
public class CompilerCompatibilityTest {
    private EnclosingClass enclosingClass = new EnclosingClass();
    private DynamicClass dynamicClass = enclosingClass.new DynamicClass();
    private DynamicExtendingClass dynamicExtendingClass = enclosingClass.new DynamicExtendingClass();
    private EnclosingDynamicClass enclosingDynamicClass = enclosingClass.new EnclosingDynamicClass();
    private NestedDynamicClass nestedDynamicClass = enclosingDynamicClass.new NestedDynamicClass();
//  NOT SUPPORTED
//  private DynamicExtendingClassWithDifferentScope dynamicExtendingClassWithDifferentScope =
//      enclosingClass.new DynamicExtendingClassWithDifferentScope(enclosingDynamicClass);

    public static class EnclosingClass {
        public class DynamicClass {
            //Oracle/OpenJDK/IBM generates
            //final EnclosingClass this$0;

            //eclipse-[groovy-]compiler generates
            //final EnclosingClass this$1;
        }

        public class DynamicExtendingClass extends DynamicClass {
            //The field here masks the parent field

            //Oracle/OpenJDK/IBM generates
            //final EnclosingClass this$0;

            //eclipse-[groovy-]compiler generates
            //final EnclosingClass this$1;
        }

        public class EnclosingDynamicClass {
            //Oracle/OpenJDK/IBM generates
            //final EnclosingClass this$0;

            //eclipse-[groovy-]compiler generates
            //final EnclosingClass this$1;

            public class NestedDynamicClass {
                //Oracle/OpenJDK/IBM generates
                //final EnclosingClass this$1;

                //eclipse-[groovy-]compiler generates
                //final EnclosingClass this$2;
            }
        }

//        WARNING: Combination NOT SUPPORTED. Not enough information in XML to deserialize reliably,
//        having in mind that different compilers could be used for parent/child classes.
//        If we really need to, we can extend the heuristic to check for field types or assume that
//        only one compiler was used for the whole class hierarchy covering some more cases.
//
//        The problem is that we have two fields with different names, without relation between the
//        indexes in each one. Changing compilers (or combination of compilers) could change the 
//        indexes independently in each field. This makes it impossible to infer which field in the xml
//        maps to which field in the object.
//        When having identical field names with parent classes XStream will put a defined-in attribute
//        which makes it possible to deserialize, but it can't be forced to put it in each element.
//
        public class DynamicExtendingClassWithDifferentScope extends NestedDynamicClass {
            //Oracle/OpenJDK/IBM generates
            //final EnclosingClass this$0;

            //eclipse-[groovy-]compiler generates
            //final EnclosingClass this$1;

            //constructor required to compile
            public DynamicExtendingClassWithDifferentScope(EnclosingDynamicClass superEnclosingScope) {
                superEnclosingScope.super();
            }
        }
    }

    @Test
    public void testXStreamDeserialize() throws Exception {
        deserialize("/brooklyn/entity/rebind/compiler_compatibility_eclipse.xml");
        deserialize("/brooklyn/entity/rebind/compiler_compatibility_oracle.xml");
    }

    private void deserialize(String inputUrl) throws Exception {
        XStream xstream = new XStream() {
            @Override
            protected MapperWrapper wrapMapper(MapperWrapper next) {
                return new CompilerIndependentOuterClassFieldMapper(super.wrapMapper(next));
            }
        };

        InputStream in = this.getClass().getResourceAsStream(inputUrl);
        try {
            Object obj = xstream.fromXML(in);
            assertNonNullOuterFields(obj);
        } catch (Exception e) {
        	System.out.println(e.getMessage());
        	throw e;
        } finally {
            in.close();
        }
    }

    private void assertNonNullOuterFields(Object obj) throws Exception {
        Field[] testInstances = obj.getClass().getDeclaredFields();
        for (Field instanceField : testInstances) {
            Object instance = instanceField.get(obj);
            Class<?> type = instance.getClass();
            do {
                for (Field field : type.getDeclaredFields()) {
                    if (field.getName().startsWith("this$")) {
                        Object value = field.get(instance);
                        assertTrue(value != null, field + " should not be null");
                    }
                }
                type = type.getSuperclass();
            } while (type != null);
        }
    }
}
