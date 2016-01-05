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
    "brooklyn", "router"
], function (Brooklyn, Router) {

    var View = Backbone.View.extend({
        render:function () {
            this.$el.html("<p>fake view</p>")
            return this
        }
    })

    describe("router", function () {
        var view, router

        beforeEach(function () {
            view = new View
            router = new Router
            $("body").append('<div id="container"></div>')
        })

        afterEach(function () {
            $("#container").remove()
        })

        it("shows the view inside div#container", function () {
            expect($("body #container").length).toBe(1)
            expect($("#container").text()).toBe("")
            router.showView("#container", view)
            expect($("#container").text()).toBe("fake view")
        })

        it("should call 'close' of old views", function () {
            spyOn(view, "close")

            router.showView("#container", view)
            expect(view.close).not.toHaveBeenCalled()
            // it should close the old view
            router.showView("#container", new View)
            expect(view.close).toHaveBeenCalled()
        })
    })

    describe("Periodic functions", function() {
        var CallbackView = View.extend({
            initialize: function() {
                this.counter = 0;
                this.callPeriodically("test-callback", function() {
                        this.counter += 1;
                    }, 3)
            }
        });

        // Expects callback to have been called at least once
        it("should have 'this' set to the owning view", function() {
            Brooklyn.view.refresh = true;
            var view = new CallbackView();
            waits(15);
            runs(function() {
                expect(view.counter).toBeGreaterThan(0);
            });
        });

        it("should not be run if Brooklyn.view.refresh is false", function() {
            Brooklyn.view.refresh = false;
            var view = new CallbackView();
            waits(15);
            runs(function() {
                expect(view.counter).toEqual(0);
                Brooklyn.view.refresh = true;
            });
        });
    });

})