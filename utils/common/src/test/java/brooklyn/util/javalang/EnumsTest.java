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

import org.testng.Assert;
import org.testng.annotations.Test;

public class EnumsTest {

    private static enum SomeENum { E_300, E_624, WORD_UP, aliceTheCamel }
    
    @Test
    public static void testValueOf() {
        Assert.assertEquals(Enums.valueOfIgnoreCase(SomeENum.class, "e_300").get(), SomeENum.E_300);
        Assert.assertEquals(Enums.valueOfIgnoreCase(SomeENum.class, "e_624").get(), SomeENum.E_624);
        Assert.assertEquals(Enums.valueOfIgnoreCase(SomeENum.class, "ALICE_THE_CAMEL").get(), SomeENum.aliceTheCamel);
        Assert.assertEquals(Enums.valueOfIgnoreCase(SomeENum.class, "alice_the_camel").get(), SomeENum.aliceTheCamel);
        Assert.assertEquals(Enums.valueOfIgnoreCase(SomeENum.class, "wordUp").get(), SomeENum.WORD_UP);
        Assert.assertEquals(Enums.valueOfIgnoreCase(SomeENum.class, "wordup").get(), SomeENum.WORD_UP);
        Assert.assertFalse(Enums.valueOfIgnoreCase(SomeENum.class, "MSG").isPresent());
        Assert.assertFalse(Enums.valueOfIgnoreCase(SomeENum.class, "alice_thecamel").isPresent());
        Assert.assertFalse(Enums.valueOfIgnoreCase(SomeENum.class, "_word_up").isPresent());
    }

    @Test
    public static void testAllValuesEnumerated() {
        Enums.checkAllEnumeratedIgnoreCase(SomeENum.class, "e_300", "E_624", "WORD_UP", "aliceTheCamel");
    }

    @Test(expectedExceptions=IllegalStateException.class, expectedExceptionsMessageRegExp = ".*leftover values.*vitamin.*")
    public static void testAllValuesEnumeratedExtra() {
        Enums.checkAllEnumeratedIgnoreCase(SomeENum.class, "e_300", "E_624", "Vitamin C", "wordUp", "alice_the_camel");
    }

    @Test(expectedExceptions=IllegalStateException.class, expectedExceptionsMessageRegExp = ".*leftover enums.*E_624.*leftover values.*")
    public static void testAllValuesEnumeratedMissing() {
        Enums.checkAllEnumeratedIgnoreCase(SomeENum.class, "e_300", "word_UP", "aliceTheCamel");
    }

    @Test(expectedExceptions=IllegalStateException.class, expectedExceptionsMessageRegExp = ".*leftover enums.*E_624.*leftover values.*msg.*")
    public static void testAllValuesEnumeratedMissingAndExtra() {
        Enums.checkAllEnumeratedIgnoreCase(SomeENum.class, "e_300", "MSG", "WORD_UP", "aliceTheCamel");
    }

    @Test(expectedExceptions=IllegalStateException.class, expectedExceptionsMessageRegExp = ".*leftover enums.*\\[aliceTheCamel\\].*leftover values.*alice_thecamel.*")
    public static void testAllValuesEnumeratedNoMatchBadCamel() {
        Enums.checkAllEnumeratedIgnoreCase(SomeENum.class, "e_300", "E_624", "WORD_UP", "alice_TheCamel");
    }

}
