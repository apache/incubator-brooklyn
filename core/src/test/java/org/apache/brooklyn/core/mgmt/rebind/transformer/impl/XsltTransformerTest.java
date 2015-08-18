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
package org.apache.brooklyn.core.mgmt.rebind.transformer.impl;

import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.core.mgmt.rebind.transformer.impl.XsltTransformer;
import org.apache.brooklyn.core.mgmt.rebind.transformer.impl.XsltTransformerTest;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.text.TemplateProcessor;
import org.apache.brooklyn.util.os.Os;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

/**
 * Tests the low-level XSLT transformer logic.
 * <p>
 * Some of the tests use xslt files which are no longer used to perform type/class/field-specific changes,
 * but they are included here because they are still useful test cases for XSLT. 
 */
public class XsltTransformerTest {

    private static final String SAMPLE_TRANSFORMER_RECURSIVE_COPY = "classpath://org/apache/brooklyn/core/mgmt/rebind/transformer/recursiveCopyWithExtraRules.xslt";
    private static String NEWLINE = Os.LINE_SEPARATOR;

    @Test
    public void testRecursiveCopyExtraRules() throws Exception {
        String xsltTemplate = ResourceUtils.create(XsltTransformerTest.class).getResourceAsString(SAMPLE_TRANSFORMER_RECURSIVE_COPY);
        String xslt = TemplateProcessor.processTemplateContents(xsltTemplate, ImmutableMap.of(
            "extra_rules", "<xsl:template match=\"nested\"><empty_nest/></xsl:template>"));
        String input = 
                "<entity myattrib=\"myval\">"+NEWLINE+
                "  <type myattrib2=\"myval2\">mytype</type>"+NEWLINE+
                "  <nested>"+NEWLINE+
                "    <type myattrib3=\"myval3\">foo</type>"+NEWLINE+
                "    bar"+NEWLINE+
                "  </nested>"+NEWLINE+
                "  <id>myid</id>"+NEWLINE+
                "</entity>";
        String expected = 
                "<entity myattrib=\"myval\">"+NEWLINE+
                "  <type myattrib2=\"myval2\">mytype</type>"+NEWLINE+
                "  <empty_nest/>"+NEWLINE+
                "  <id>myid</id>"+NEWLINE+
                "</entity>";
        
        XsltTransformer transformer = new XsltTransformer(xslt);
        String result = transformer.transform(input);
        assertEquals(result, expected);
    }
    
    @Test
    public void testRenameType() throws Exception {
        String xsltTemplate = ResourceUtils.create(XsltTransformerTest.class).getResourceAsString("classpath://brooklyn/entity/rebind/transformer/impl/renameType.xslt");
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
        String xsltTemplate = ResourceUtils.create(XsltTransformerTest.class).getResourceAsString("classpath://brooklyn/entity/rebind/transformer/impl/renameField.xslt");
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
        String xsltTemplate = ResourceUtils.create(XsltTransformerTest.class).getResourceAsString("classpath://brooklyn/entity/rebind/transformer/impl/renameClass.xslt");
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
