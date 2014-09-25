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

    @Test
    public void testRenameType() throws Exception {
        String xsltTemplate = ResourceUtils.create(XsltTransformerTest.class).getResourceAsString("classpath://brooklyn/entity/rebind/transformer/renameType.xslt");
        String xslt = TemplateProcessor.processTemplateContents(xsltTemplate, ImmutableMap.of("old_val", "mytype.Before", "new_val", "mytype.After"));
        String input = 
                "<entity myattrib=\"myval\">"+"\n"+
                "  <type myattrib2=\"myval2\">mytype.Before</type>"+"\n"+
                "  <nested>"+"\n"+
                "    <type myattrib3=\"myval3\">doesNotMatch</type>"+"\n"+
                "    <type myattrib4=\"myval4\">partial.mytype.Before</type>"+"\n"+
                "    <type myattrib5=\"myval5\">mytype.Before</type>"+"\n"+
                "  </nested>"+"\n"+
                "  <id>myid</id>"+"\n"+
                "</entity>";
        String expected = 
                "<entity myattrib=\"myval\">"+"\n"+
                "  <type myattrib2=\"myval2\">mytype.After</type>"+"\n"+
                "  <nested>"+"\n"+
                "    <type myattrib3=\"myval3\">doesNotMatch</type>"+"\n"+
                "    <type myattrib4=\"myval4\">partial.mytype.Before</type>"+"\n"+
                "    <type myattrib5=\"myval5\">mytype.After</type>"+"\n"+
                "  </nested>"+"\n"+
                "  <id>myid</id>"+"\n"+
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
                "<entity myattrib=\"myval\">"+"\n"+
                "  <type myattrib2=\"myval2\">mytype.Before</type>"+"\n"+
                "  <config>"+"\n"+
                "    <test.conf1>"+"\n"+
                "      <MyClass>"+"\n"+
                "        <myFieldBefore class=\"string\">myfieldval</myFieldBefore>"+"\n"+
                "      </MyClass>"+"\n"+
                "    </test.conf1>"+"\n"+
                "    <test.conf2>"+"\n"+
                "      <MyOtherClass>"+"\n"+
                "        <myFieldBefore class=\"string\">myfieldval</myFieldBefore>"+"\n"+
                "      </MyOtherClass>"+"\n"+
                "    </test.conf2>"+"\n"+
                "  </config>"+"\n"+
                "</entity>";
        String expected = 
                "<entity myattrib=\"myval\">"+"\n"+
                "  <type myattrib2=\"myval2\">mytype.Before</type>"+"\n"+
                "  <config>"+"\n"+
                "    <test.conf1>"+"\n"+
                "      <MyClass>"+"\n"+
                "        <myFieldAfter class=\"string\">myfieldval</myFieldAfter>"+"\n"+
                "      </MyClass>"+"\n"+
                "    </test.conf1>"+"\n"+
                "    <test.conf2>"+"\n"+
                "      <MyOtherClass>"+"\n"+
                "        <myFieldBefore class=\"string\">myfieldval</myFieldBefore>"+"\n"+
                "      </MyOtherClass>"+"\n"+
                "    </test.conf2>"+"\n"+
                "  </config>"+"\n"+
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
                "<entity myattrib=\"myval\">"+"\n"+
                "  <type myattrib2=\"myval2\">mytype.Before</type>"+"\n"+
                "  <config>"+"\n"+
                "    <test.conf1>"+"\n"+
                "      <MyClassBefore>"+"\n"+
                "      </MyClassBefore>"+"\n"+
                "    </test.conf1>"+"\n"+
                "  </config>"+"\n"+
                "</entity>";
        String expected = 
                "<entity myattrib=\"myval\">"+"\n"+
                "  <type myattrib2=\"myval2\">mytype.Before</type>"+"\n"+
                "  <config>"+"\n"+
                "    <test.conf1>"+"\n"+
                "      <MyClassAfter>"+"\n"+
                "      </MyClassAfter>"+"\n"+
                "    </test.conf1>"+"\n"+
                "  </config>"+"\n"+
                "</entity>";
        
        XsltTransformer transformer = new XsltTransformer(xslt);
        String result = transformer.transform(input);
        assertEquals(result, expected);
    }
}
