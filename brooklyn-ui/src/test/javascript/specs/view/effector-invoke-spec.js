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
    "underscore", "view/effector-invoke", "model/effector-summary", "model/entity", "model/location"
], function (_, EffectorInvokeView, EffectorSummary, Entity, Location) {

    var collection = new EffectorSummary.Collection()
    collection.url = "fixtures/effector-summary-list.json"
    collection.fetch()
    
    var entityFixture = new Entity.Collection
    entityFixture.url = 'fixtures/entity.json'
    entityFixture.fetch()
    
    var locationsFixture = new Location.Collection
    locationsFixture.url = 'fixtures/location-list.json'
    locationsFixture.fetch()

    const effector = collection.at(0);

    var modalView = new EffectorInvokeView({
        tagName:"div",
        className:"modal",
        model: effector,
        entity:entityFixture.at(0),
        locations: locationsFixture
    })

    describe("view/effector-invoke", function () {
        // render and keep the reference to the view
        modalView.render()

        it("must render a bootstrap modal", function () {
            expect(modalView.$(".modal-header").length).toBe(1)
            expect(modalView.$(".modal-body").length).toBe(1)
            expect(modalView.$(".modal-footer").length).toBe(1)
        })

        it("must have effector name, entity name, and effector description in header", function () {
            expect(modalView.$(".modal-header h3").html()).toContain("start")
            expect(modalView.$(".modal-header h3").html()).toContain("Vanilla")
            expect(modalView.$(".modal-header p").html()).toBe("Start the process/service represented by an entity")
        })

        it("must have the list of parameters in body", function () {
            expect(modalView.$(".modal-body table").length).toBe(1);
            // +1 because one <tr> from table head
            expect(modalView.$(".modal-body tr").length).toBe(effector.get("parameters").length + 1)
        });

        it("must properly extract parameters from table", function () {
            // Select the third item in the option list rather than the "None" and
            // horizontal bar placeholders.
            window.m = modalView;
            modalView.$(".select-location option:eq(2)").attr("selected", "selected");

            var params = modalView.extractParamsFromTable();
            console.log(params);
            expect(params["locations"]).toBe("123")
            expect(params).toEqual({
                "locations": "123",
                "booleanValue": "true"
            });
        });
    })
})