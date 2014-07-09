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
    "underscore", "jquery", "model/app-tree", "view/application-tree",
    "model/entity-summary", "model/application"
], function (_, $, AppTree, ApplicationTreeView, EntitySummary, Application) {

    // TODO json content must be updated to reflect new "batch" style result
    // now used by the tree
    
//    var apps = new AppTree.Collection
//    apps.url = 'fixtures/application-tree.json'
//    apps.fetch({async:true})
//
//    describe('view/application-tree renders the list of applications as a tree', function () {
//        var view, entityFetch, applicationFetch, defer;
//
//        beforeEach(function () {
//            // ApplicationTree makes fetch requests to EntitySummary and Application models
//            // with hard-coded URLs, causing long stacktraces in mvn output. This workaround
//            // turns their fetch methods into empty functions.
//            entityFetch = EntitySummary.Model.prototype.fetch;
//            applicationFetch = Application.Model.prototype.fetch;
//            defer = _.defer;
//            _.defer = EntitySummary.Model.prototype.fetch = Application.Model.prototype.fetch = function() {};
//
//            // Append a #details div for not found test
//            $("body").append('<div id="details"></div>');
//
//            view = new ApplicationTreeView({
//                collection:apps
//            }).render()
//        })
//
//        // Restore EntitySummary and Application fetch.
//        afterEach(function() {
//            EntitySummary.Model.prototype.fetch = entityFetch;
//            Application.Model.prototype.fetch = applicationFetch;
//            _.defer = defer;
//            $("#details").remove();
//        });
//
//        it("builds the entity tree for each application", function () {
//            expect(view.$("#riBZUjMq").length).toBe(1)
//            expect(view.$("#fXyyQ7Ap").length).toBe(1)
//            expect(view.$("#child-02").length).toBe(1)
//            expect(view.$("#child-03").length).toBe(1)
//            expect(view.$("#child-04").length).toBe(1)
//            
//            expect(view.$("#child-nonesuch").length).toBe(0)
//        })
//
//        it("shows a 'not found' error for unknown entities", function() {
//            view.displayEntityId("nonexistant");
//            expect($("#details").text().toLowerCase()).toContain("failed to load entity nonexistant");
//        });
//    })
    
})