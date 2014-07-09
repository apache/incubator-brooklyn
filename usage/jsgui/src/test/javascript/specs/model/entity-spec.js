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
    "underscore", "model/entity"
], function (_, Entity) {
    $.ajaxSetup({ async:false });
    
    describe("model/entity", function () {
        // keep these in describe so jasmine-maven will load them from the file pointed by URL
        var entityFixture = new Entity.Collection
        entityFixture.url = 'fixtures/entity.json'
        entityFixture.fetch()

        it('loads all model properties defined in fixtures/entity.json', function () {
            expect(entityFixture.length).toEqual(1)
            var entity = entityFixture.at(0)
            expect(entity.get("name")).toEqual('Vanilla Java App')
            expect(entity.get("type")).toEqual('brooklyn.entity.java.VanillaJavaApp')
            expect(entity.get("config")).toEqual({})
        })
    })
})