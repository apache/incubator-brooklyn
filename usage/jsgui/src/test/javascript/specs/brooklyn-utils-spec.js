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
    'brooklyn-utils'
], function (Util) {

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
});
