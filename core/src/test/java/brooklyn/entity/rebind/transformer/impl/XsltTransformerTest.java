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
package brooklyn.entity.rebind.transformer.impl;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import brooklyn.entity.rebind.transformer.impl.XsltTransformer;
import brooklyn.entity.rebind.transformer.impl.XsltTransformerTest;
import brooklyn.util.ResourceUtils;
import brooklyn.util.text.TemplateProcessor;

import com.google.common.collect.ImmutableMap;

public class XsltTransformerTest {

    private static String NEWLINE = System.getProperty("line.separator");
    
    @Test
    public void testRenameType() throws Exception {
        String xsltTemplate = ResourceUtils.create(XsltTransformerTest.class).getResourceAsString("classpath://brooklyn/entity/rebind/transformer/renameType.xslt");
        String xslt = TemplateProcessor.processTemplateContents(xsltTemplate, ImmutableMap.of("old_val", "mytype.Before", "new_val", "mytype.After"));
        String input = 
                "<entity myattrib=\"myval\">"+NEWLINE+
                "  <type myattrib2=\"myval2\">mytype.Before</type>"+NEWLINE+
                "  <nested>"+NEWLINE+
                "    <type myattrib3=\"myval3\">doesNotMatch</type>"+NEWLINE+
                "    <type myattrib4=\"myval4\">partial.mytype.Before</type>"+NEWLINE+
                "    <type myattrib5=\"myval5\">mytype.Before</type>"+NEWLINE+
                "  </nested>"+NEWLINE+
                "  <id>myid</id>"+NEWLINE+
                "</entity>";
        String expected = 
                "<entity myattrib=\"myval\">"+NEWLINE+
                "  <type myattrib2=\"myval2\">mytype.After</type>"+NEWLINE+
                "  <nested>"+NEWLINE+
                "    <type myattrib3=\"myval3\">doesNotMatch</type>"+NEWLINE+
                "    <type myattrib4=\"myval4\">partial.mytype.Before</type>"+NEWLINE+
                "    <type myattrib5=\"myval5\">mytype.After</type>"+NEWLINE+
                "  </nested>"+NEWLINE+
                "  <id>myid</id>"+NEWLINE+
                "</entity>";
        
        XsltTransformer transformer = new XsltTransformer(xslt);
        String result = transformer.transform(input);
        assertEquals(result, expected);
    }
    
    @Test
    public void testRenameField() throws Exception {
        String xsltTemplate = ResourceUtils.create(XsltTransformerTest.class).getResourceAsString("classpath://brooklyn/entity/rebind/transformer/renameField.xslt");
        String xslt = TemplateProcessor.processTemplateContents(xsltTemplate, ImmutableMap.of("class_name", "MyClass", "old_val", "myFieldBefore", "new_val", "myFieldAfter"));
        String input = 
                "<entity myattrib=\"myval\">"+NEWLINE+
                "  <type myattrib2=\"myval2\">mytype.Before</type>"+NEWLINE+
                "  <config>"+NEWLINE+
                "    <test.conf1>"+NEWLINE+
                "      <MyClass>"+NEWLINE+
                "        <myFieldBefore class=\"string\">myfieldval</myFieldBefore>"+NEWLINE+
                "      </MyClass>"+NEWLINE+
                "    </test.conf1>"+NEWLINE+
                "    <test.conf2>"+NEWLINE+
                "      <MyOtherClass>"+NEWLINE+
                "        <myFieldBefore class=\"string\">myfieldval</myFieldBefore>"+NEWLINE+
                "      </MyOtherClass>"+NEWLINE+
                "    </test.conf2>"+NEWLINE+
                "  </config>"+NEWLINE+
                "</entity>";
        String expected = 
                "<entity myattrib=\"myval\">"+NEWLINE+
                "  <type myattrib2=\"myval2\">mytype.Before</type>"+NEWLINE+
                "  <config>"+NEWLINE+
                "    <test.conf1>"+NEWLINE+
                "      <MyClass>"+NEWLINE+
                "        <myFieldAfter class=\"string\">myfieldval</myFieldAfter>"+NEWLINE+
                "      </MyClass>"+NEWLINE+
                "    </test.conf1>"+NEWLINE+
                "    <test.conf2>"+NEWLINE+
                "      <MyOtherClass>"+NEWLINE+
                "        <myFieldBefore class=\"string\">myfieldval</myFieldBefore>"+NEWLINE+
                "      </MyOtherClass>"+NEWLINE+
                "    </test.conf2>"+NEWLINE+
                "  </config>"+NEWLINE+
                "</entity>";
        
        XsltTransformer transformer = new XsltTransformer(xslt);
        String result = transformer.transform(input);
        assertEquals(result, expected);
    }
    
    @Test
    public void testRenameClass() throws Exception {
        String xsltTemplate = ResourceUtils.create(XsltTransformerTest.class).getResourceAsString("classpath://brooklyn/entity/rebind/transformer/renameClass.xslt");
        String xslt = TemplateProcessor.processTemplateContents(xsltTemplate, ImmutableMap.of("old_val", "MyClassBefore", "new_val", "MyClassAfter"));
        String input = 
                "<entity myattrib=\"myval\">"+NEWLINE+
                "  <type myattrib2=\"myval2\">mytype.Before</type>"+NEWLINE+
                "  <config>"+NEWLINE+
                "    <test.conf1>"+NEWLINE+
                "      <MyClassBefore>"+NEWLINE+
                "      </MyClassBefore>"+NEWLINE+
                "    </test.conf1>"+NEWLINE+
                "  </config>"+NEWLINE+
                "</entity>";
        String expected = 
                "<entity myattrib=\"myval\">"+NEWLINE+
                "  <type myattrib2=\"myval2\">mytype.Before</type>"+NEWLINE+
                "  <config>"+NEWLINE+
                "    <test.conf1>"+NEWLINE+
                "      <MyClassAfter>"+NEWLINE+
                "      </MyClassAfter>"+NEWLINE+
                "    </test.conf1>"+NEWLINE+
                "  </config>"+NEWLINE+
                "</entity>";
        
        XsltTransformer transformer = new XsltTransformer(xslt);
        String result = transformer.transform(input);
        assertEquals(result, expected);
    }
}
