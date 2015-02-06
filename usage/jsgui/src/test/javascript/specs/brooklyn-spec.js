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

    describe("_.stripComments", function () {
        it("should strip a basic comment", function () {
            var text = "<p>abc</p>\n <!-- comment-here --> <p>cba</p>";
            expect(_.stripComments(text)).toBe("<p>abc</p>\n  <p>cba</p>");
        });

        it("should return an empty string for an empty comment", function () {
            expect(_.stripComments("<!---->")).toBe("");
            expect(_.stripComments("<!-- -->")).toBe("");
        });

        it("should strip multiple comments", function () {
            var text = "a<!-- one -->b<!--two-->c<!-- three  -->";
            expect(_.stripComments(text)).toBe("abc");
        });

        it("should strip trailing newlines", function () {
            expect(_.stripComments("<!-- a -->\nb")).toBe("b");
            expect(_.stripComments("<!-- a -->\rb")).toBe("b");
        });

        it("should leave text with no comments untouched", function () {
            var text = "<p>abc</p>";
            expect(_.stripComments(text)).toBe(text);
        });

        it("should remove the Apache license header from an HTML template", function () {
            var text = "<!--\n" +
                    "Licensed to the Apache Software Foundation (ASF) under one\n" +
                    "or more contributor license agreements.  See the NOTICE file\n" +
                    "distributed with this work for additional information\n" +
                    "regarding copyright ownership.  The ASF licenses this file\n" +
                    "to you under the Apache License, Version 2.0 (the\n" +
                    "\"License\"); you may not use this file except in compliance\n" +
                    "with the License.  You may obtain a copy of the License at\n" +
                    "\n" +
                     "http://www.apache.org/licenses/LICENSE-2.0\n" +
                    "\n" +
                    "Unless required by applicable law or agreed to in writing,\n" +
                    "software distributed under the License is distributed on an\n" +
                    "\"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY\n" +
                    "KIND, either express or implied.  See the License for the\n" +
                    "specific language governing permissions and limitations\n" +
                    "under the License.\n" +
                    "-->\n" +
                    "real content";
            expect(_.stripComments(text)).toBe("real content");
        });
    });
});