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
    'brooklyn-utils', "backbone"
], function (Util, Backbone) {

    describe('Rounding numbers', function () {

        var round = Util.roundIfNumberToNumDecimalPlaces;

        it("should round in the correct direction", function() {
            // unchanged
            expect(round(1, 2)).toBe(1);
            expect(round(1.1, 1)).toBe(1.1);
            expect(round(1.9, 1)).toBe(1.9);
            expect(round(1.123123123, 6)).toBe(1.123123);
            expect(round(-22.222, 3)).toBe(-22.222);

            // up
            expect(round(1.9, 0)).toBe(2);
            expect(round(1.5, 0)).toBe(2);
            expect(round(1.49, 1)).toBe(1.5);

            // down
            expect(round(1.01, 1)).toBe(1.0);
            expect(round(1.49, 0)).toBe(1);
            expect(round(1.249, 1)).toBe(1.2);
            expect(round(1.0000000000000000000001, 0)).toBe(1);
        });

        it("should round negative numbers correctly", function() {
            // up
            expect(round(-10, 0)).toBe(-10);
            expect(round(-10.49999, 0)).toBe(-10);

            // down
            expect(round(-10.5, 0)).toBe(-11);
            expect(round(-10.50001, 0)).toBe(-11);
            expect(round(-10.49999, 1)).toBe(-10.5);
        });

        it("should ignore non-numeric values", function() {
            expect(round("xyz", 1)).toBe("xyz");
            expect(round("2.4", 0)).toBe("2.4");
            expect(round({a: 2}, 0)).toEqual({a: 2});
        });

        it("should ignore negative mantissas", function() {
            expect(round(10.5, -1)).toBe(10.5);
            expect(round(100, -1)).toBe(100);
            expect(round(0, -1)).toBe(0);
        });

    });

    describe("pathOf", function() {

        it("should extract the path component of a URI", function() {
            expect(Util.pathOf("http://www.example.com/path/to/resource#more?a=b&c=d")).toBe("/path/to/resource");
        });

        it("should return an empty path for an empty URL", function() {
            expect(Util.pathOf("")).toBe("");
        });

        it("should handle input without domain", function() {
            expect(Util.pathOf("/a/b/c/d#e")).toBe("/a/b/c/d");
        })
    });

    describe("inputValue", function () {
        it("should return inputs as strings", function () {
            expect(Util.inputValue($('<input type="text" value="bob"/>'))).toBe("bob");
            expect(Util.inputValue($('<textarea rows="10" cols="5">content</textarea>'))).toBe("content");
        });

        it("should return true/false for checkboxes", function () {
            var input = $('<input type="checkbox" checked/>');
            expect(Util.inputValue(input)).toBe(true);
            input = $('<input type="checkbox" />');
            expect(Util.inputValue(input)).toBe(false);
        });
    });

    describe("bindModelFromForm", function () {
        // pretend to be a Backbone model without bringing in Backbone as a dependency
        var TestModel = Backbone.Model.extend({
            urlRoot: function () {
                return "/foo/bar/";
            }

        });
        var form = $("<form>" +
            "<input name='id' type='input' value='text'/>" +
            "<input name='bool' type='checkbox' checked/>" +
            "</form>");

        it("should create a new model if given a constructor", function () {
            var model = Util.bindModelFromForm(TestModel, form);
            expect(model instanceof TestModel).toBe(true);
            expect(model.url()).toBe("/foo/bar/text");
            var inputs = model.attributes;
            expect(_.keys(inputs).length).toBe(2);
            expect(inputs.id).toBe("text");
            expect(inputs.bool).toBe(true);
        });

        it("should update an existing model", function () {
            var model = new TestModel({initialAttribute: "xyz"});
            Util.bindModelFromForm(model, form);
            var inputs = model.attributes;
            expect(_.keys(inputs).length).toBe(3);
            expect(inputs.id).toBe("text");
            expect(inputs.bool).toBe(true);
            expect(inputs.initialAttribute).toBe("xyz");
        });
    });
});
