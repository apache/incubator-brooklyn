/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
define([
    "model/effector-summary", "model/effector-param"
], function (EffectorSummary, EffectorParam) {
    $.ajaxSetup({ async:false });
    
    describe("effector-spec: EffectorSummary model", function () {
        var effectorCollection = new EffectorSummary.Collection
        effectorCollection.url = "fixtures/effector-summary-list.json"
        effectorCollection.fetch()

        it("must have 3 objects", function () {
            expect(effectorCollection.length).toBe(3)
        })

        it("has a first object 'name'", function () {
            var effector1 = effectorCollection.at(0)
            expect(effector1.get("name")).toBe("start")
            expect(effector1.get("returnType")).toBe("void")
            expect(effector1.get("parameters").length).toBe(1)
        })

        it(" effector1 has a first parameter named 'locations'", function () {
            var effector1 = effectorCollection.at(0)
            var param1 = new EffectorParam.Model(effector1.getParameterByName("locations"))
            expect(param1.get("name")).toBe("locations")
            expect(param1.get("type")).toBe("java.util.Collection")
            expect(param1.get("description")).toBe("")
        })
    })
})
