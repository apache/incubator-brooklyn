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
package org.apache.brooklyn.util.yaml;

import static org.testng.Assert.assertEquals;

import java.util.Iterator;
import java.util.List;

import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.exceptions.UserFacingException;
import org.apache.brooklyn.util.yaml.Yamls;
import org.apache.brooklyn.util.yaml.YamlsTest;
import org.apache.brooklyn.util.yaml.Yamls.YamlExtract;
import org.testng.Assert;
import org.testng.TestNG;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class YamlsTest {

    @Test
    public void testGetAs() throws Exception {
        MutableList<String> list = MutableList.of("x");
        assertEquals(Yamls.getAs(list.iterator(), List.class), list);
        assertEquals(Yamls.getAs(list.iterator(), Iterable.class), list);
        assertEquals(Yamls.getAs(list.iterator(), Iterator.class), list.iterator());
        assertEquals(Yamls.getAs(list.iterator(), String.class), "x");
    }
        
    @Test
    public void testGetAt() throws Exception {
        // leaf of map
        assertEquals(Yamls.getAt("k1: v", ImmutableList.of("k1")), "v");
        assertEquals(Yamls.getAt("k1: {k2: v}", ImmutableList.of("k1", "k2")), "v");
        
        // get list
        assertEquals(Yamls.getAt("k1: [v1, v2]", ImmutableList.<String>of("k1")), ImmutableList.of("v1", "v2"));

        // get map
        assertEquals(Yamls.getAt("k1: v", ImmutableList.<String>of()), ImmutableMap.of("k1", "v"));
        assertEquals(Yamls.getAt("k1: {k2: v}", ImmutableList.of("k1")), ImmutableMap.of("k2", "v"));
        
        // get array index
        assertEquals(Yamls.getAt("k1: [v1, v2]", ImmutableList.<String>of("k1", "[0]")), "v1");
        assertEquals(Yamls.getAt("k1: [v1, v2]", ImmutableList.<String>of("k1", "[1]")), "v2");
    }
    
    
    @Test
    public void testExtractMap() {
        String sample = "#before\nk1:\n- v1\nk2:\n  # comment\n  k21: v21\nk3: v3\n#after\n";
        
        Assert.assertEquals(Yamls.getTextOfYamlAtPath(sample, "k1").withKeyIncluded(true).getMatchedYamlText(),
            sample.substring(0, sample.indexOf("k2")));
        Assert.assertEquals(Yamls.getTextOfYamlAtPath(sample, "k3").withKeyIncluded(true).getMatchedYamlText(),
            sample.substring(sample.indexOf("k3")));
        
        // comments and no key, outdented - the default
        Assert.assertEquals(Yamls.getTextOfYamlAtPath(sample, "k2", "k21").getMatchedYamlText(),
            "# comment\nv21");
        Assert.assertEquals(Yamls.getTextOfYamlAtPath(sample, "k2", "k21").getMatchedYamlText(),
            "# comment\nv21");
        // comments and key
        Assert.assertEquals(Yamls.getTextOfYamlAtPath(sample, "k2", "k21").withKeyIncluded(true).getMatchedYamlText(),
            "# comment\nk21: v21");
        // no comments
        Assert.assertEquals(Yamls.getTextOfYamlAtPath(sample, "k2", "k21").withKeyIncluded(true).withPrecedingCommentsIncluded(false).getMatchedYamlText(),
            "k21: v21");
        // no comments and no key
        Assert.assertEquals(Yamls.getTextOfYamlAtPath(sample, "k2", "k21").withPrecedingCommentsIncluded(false).getMatchedYamlText(),
            "v21");

        // comments and no key, not outdented
        Assert.assertEquals(Yamls.getTextOfYamlAtPath(sample, "k2", "k21").withOriginalIndentation(true).getMatchedYamlText(),
            "  # comment\n  v21");
        // comments and key
        Assert.assertEquals(Yamls.getTextOfYamlAtPath(sample, "k2", "k21").withKeyIncluded(true).withOriginalIndentation(true).getMatchedYamlText(),
            "  # comment\n  k21: v21");
        // no comments
        Assert.assertEquals(Yamls.getTextOfYamlAtPath(sample, "k2", "k21").withKeyIncluded(true).withPrecedingCommentsIncluded(false).withOriginalIndentation(true).getMatchedYamlText(),
            "  k21: v21");
        // no comments and no key
        Assert.assertEquals(Yamls.getTextOfYamlAtPath(sample, "k2", "k21").withPrecedingCommentsIncluded(false).withOriginalIndentation(true).getMatchedYamlText(),
            "  v21");
    }

    @Test
    public void testExtractInList() {
        String sample = 
            "- a\n" +
            "- b: 2\n" +
            "- # c\n" +
            " c1:\n" +
            "  1\n" +
            " c2:\n" +
            "  2\n" +
            "-\n" +
            " - a # for a\n" +
            " # for b\n" +
            " - b\n";
        
        Assert.assertEquals(Yamls.getTextOfYamlAtPath(sample, 0).getMatchedYamlText(), "a");
        Assert.assertEquals(Yamls.getTextOfYamlAtPath(sample, 1, "b").getMatchedYamlText(), "2");
        Assert.assertEquals(Yamls.getTextOfYamlAtPath(sample, 3, 0).getMatchedYamlText(), 
            "a"
            // TODO comments after on same line not yet included - would be nice to add
//            "a # for a"
            );
        
        // out-dent
        Assert.assertEquals(Yamls.getTextOfYamlAtPath(sample, 2).getMatchedYamlText(), "c1:\n 1\nc2:\n 2\n");
        // don't outdent
        Assert.assertEquals(Yamls.getTextOfYamlAtPath(sample, 2).withOriginalIndentation(true).getMatchedYamlText(), " c1:\n  1\n c2:\n  2\n");
        Assert.assertEquals(Yamls.getTextOfYamlAtPath(sample, 3, 0).withOriginalIndentation(true).getMatchedYamlText(), 
            "   a"
            // as above, comments after not included
//            "   a # for a"
            );

        // with preceding comments
        // TODO final item includes newline (and comments) after - this behaviour might change, it's inconsistent,
        // but it means the final comments aren't lost
        Assert.assertEquals(Yamls.getTextOfYamlAtPath(sample, 3, 1).getMatchedYamlText(), "# for b\nb\n");
        
        // exclude preceding comments
        Assert.assertEquals(Yamls.getTextOfYamlAtPath(sample, 3, 1).withPrecedingCommentsIncluded(false).getMatchedYamlText(), "b\n");
    }
    
    @Test
    public void testExtractMapIgnoringPreviousComments() {
        String sample = "a: 1 # one\n"
            + "b: 2 # two";
        Assert.assertEquals(Yamls.getTextOfYamlAtPath(sample, "b").getMatchedYamlText(),
            "2 # two");
    }
    
    @Test
    public void testExtractMapWithOddWhitespace() {
        Assert.assertEquals(Yamls.getTextOfYamlAtPath("x: a\n bc", "x").getMatchedYamlText(),
            "a\n bc");
    }

    @Test
    public void testReplace() {
        Assert.assertEquals(Yamls.getTextOfYamlAtPath("x: a\n bc", "x").getFullYamlTextWithExtractReplaced("\nc: 1\nd: 2"),
            "x: \n   c: 1\n   d: 2");
    }

    @Test
    public void testExtractNoOOBE() {
        // this might log a warning, as item not found, but won't throw
        YamlExtract r1 = Yamls.getTextOfYamlAtPath(
            "items:\n- id: sample2\n  itemType: location\n  item:\n    type: jclouds:aws-ec2\n    brooklyn.config:\n      key2: value2\n\n",
            "item");
        
        // won't throw
        r1.getMatchedYamlTextOrWarn();
        // will throw
        try {
            r1.getMatchedYamlText();
            Assert.fail();
        } catch (UserFacingException e) {
            // expected, it should give a vaguely explanatory exception and no trace
        }
    }
    
    // convenience, since running with older TestNG IDE plugin will fail (older snakeyaml dependency);
    // if you run as a java app it doesn't bring in the IDE TestNG jar version, and it works
    public static void main(String[] args) {
        TestNG testng = new TestNG();
        testng.setTestClasses(new Class[] { YamlsTest.class });
//        testng.setVerbose(9);
        testng.run();
    }
    
}
