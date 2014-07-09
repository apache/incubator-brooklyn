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
    "underscore", "view/entity-sensors",
    "text!tpl/apps/sensor-name.html"
], function (_, EntitySensorsView, SensorNameHtml) {

    function contains(string, value) {
        return string.indexOf(value) != -1;
    }

    describe("template/sensor-name", function () {
        var sensorNameHtml = _.template(SensorNameHtml);
        var context = {name: "name", description: "description", type: "type"};

        it("should not create an anchor tag in name", function() {
            var templated = sensorNameHtml(_.extend(context, {href: "href"}));
            expect(contains(templated, "<a href=\"href\"")).toBe(false);
        });
        
        it("should not fail if context.href is undefined", function() {
            var templated = sensorNameHtml(_.extend(context, {href: undefined}));
            expect(contains(templated, "<a href=")).toBe(false);
        });
    })

})