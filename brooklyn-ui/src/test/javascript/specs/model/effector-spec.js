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

    $.ajaxSetup({async: false});

    describe("effector-spec: EffectorSummary model", function () {
        var effectorCollection = new EffectorSummary.Collection;
        effectorCollection.url = "fixtures/effector-summary-list.json";
        effectorCollection.fetch();

        it("must have start, stop and restart effectors", function () {
            var actual = effectorCollection.pluck("name").sort();
            var expected = ["restart", "start", "stop"].sort();
            expect(actual).toEqual(expected);
        });

        describe("the start effector", function () {
            var startEffector = effectorCollection.at(0);
            it("has void return type and two parameters", function () {
                expect(startEffector.get("name")).toBe("start");
                expect(startEffector.get("returnType")).toBe("void");
                expect(startEffector.get("parameters").length).toBe(2);
            });

            it("has a parameter named 'locations'", function () {
                var parameter = new EffectorParam.Model(startEffector.getParameterByName("locations"));
                expect(parameter.get("name")).toBe("locations");
                expect(parameter.get("type")).toBe("java.util.Collection");
                expect(parameter.get("description")).toBe("A list of locations");
            });

            it("has a parameter named 'booleanValue'", function () {
                var parameter = new EffectorParam.Model(startEffector.getParameterByName("booleanValue"));
                expect(parameter.get("name")).toBe("booleanValue");
                expect(parameter.get("type")).toBe("java.lang.Boolean");
                expect(parameter.get("description")).toBe("True or false");
                expect(parameter.get("defaultValue")).toBe(true);
            });
        })
    })
});
