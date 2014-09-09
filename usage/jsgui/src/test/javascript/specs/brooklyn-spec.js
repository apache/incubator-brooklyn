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
    "brooklyn", "backbone"
], function (B, Backbone) {

    describe("view", function () {
        describe("form", function() {
            var formTemplate = _.template('<form>' +
                '<input name="id" type="text"/>' +
                '<input name="initialvalue" type="text" value="present"/>' +
                '<button type="submit" class="submit">Submit</button>' +
                '</form>');

            it("should set existing values on the model", function() {
                var form = new B.view.Form({template: formTemplate, onSubmit: function() {}});
                expect(form.model.get("initialvalue")).toBe("present");
                expect(form.model.get("id")).toBe("");
            });

            it("should maintain a model as inputs change", function () {
                var form = new B.view.Form({
                    template: formTemplate,
                    onSubmit: function() {}
                });
                // simulate id entry
                form.$("[name=id]").val("987");
                form.$("[name=id]").trigger("change");
                expect(form.model.get("id")).toBe("987");
            });

            it("should call the onSubmit callback when the form is submitted", function () {
                var wasCalled = false;
                var onSubmit = function (model) {
                    wasCalled = true;
                };
                var form = new B.view.Form({
                    template: formTemplate,
                    onSubmit: onSubmit
                });
                console.log(form.$(".submit"));
                form.$("form").trigger("submit");
                expect(wasCalled).toBe(true);
            });

            it("should fail if called without template or onSubmit", function () {
                try {
                    new B.view.Form({template: ""});
                    fail;
                } catch (e) {
                    // expected
                }
                try {
                    new B.view.Form({onSubmit: function() {}});
                    fail;
                } catch (e) {
                    // expected
                }

            });
        });

    });
});