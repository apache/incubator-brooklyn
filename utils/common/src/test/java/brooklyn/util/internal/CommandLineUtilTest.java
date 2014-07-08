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
package brooklyn.util.internal;

import static org.testng.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.testng.annotations.Test;

import brooklyn.util.CommandLineUtil;

import com.google.common.collect.Lists;

public class CommandLineUtilTest {

	@Test
	public void testGetCommandReturnsDefaultIfNotPresent() throws Exception {
		List<String> args = Lists.newArrayList("k1", "v1");
		String result = CommandLineUtil.getCommandLineOption(args, "notthere", "mydefault");
		assertEquals(result, "mydefault");
		assertEquals(args, Arrays.asList("k1", "v1"));
	}
	
	@Test
	public void testGetCommandReturnsParamAndRemovesIt() throws Exception {
	    List<String> args = Lists.newArrayList("k1", "v1");
		String result = CommandLineUtil.getCommandLineOption(args, "k1");
		assertEquals(result, "v1");
		assertEquals(args, Arrays.asList());
	}
	
	@Test
	public void testGetCommandReturnsParamAndRemovesItButLeavesOtherVals() throws Exception {
	    List<String> args = Lists.newArrayList("k1", "v1", "k2", "v2");
		String result = CommandLineUtil.getCommandLineOption(args, "k1");
		assertEquals(result, "v1");
		assertEquals(args, Arrays.asList("k2", "v2"));
	}
	
	@Test
	public void testGetCommandReturnsParamAndRemovesItButLeavesOtherValsWhenDuplicateVals() throws Exception {
	    List<String> args = Lists.newArrayList("k1", "vdup", "k2", "v2", "k3", "vdup");
		String result = CommandLineUtil.getCommandLineOption(args, "k3");
		assertEquals(result, "vdup");
		assertEquals(args, Arrays.asList("k1", "vdup", "k2", "v2"));
	}
}
