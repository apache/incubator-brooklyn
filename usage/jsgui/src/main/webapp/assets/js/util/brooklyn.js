/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


/** brooklyn extension to make console methods available and simplify access to other utils */

define([
    "underscore", "brooklyn-view", "brooklyn-utils"
], function (_, BrooklynViews, BrooklynUtils) {

    /**
     * Makes the console API safe to use:
     *  - Stubs missing methods to prevent errors when no console is present.
     *  - Exposes a global `log` function that preserves line numbering and formatting.
     *
     * Idea from https://gist.github.com/bgrins/5108712
     */
    (function () {
        var noop = function () {},
            consoleMethods = [
                'assert', 'clear', 'count', 'debug', 'dir', 'dirxml', 'error',
                'exception', 'group', 'groupCollapsed', 'groupEnd', 'info', 'log',
                'markTimeline', 'profile', 'profileEnd', 'table', 'time', 'timeEnd',
                'timeStamp', 'trace', 'warn'
            ],
            length = consoleMethods.length,
            console = (window.console = window.console || {});

        while (length--) {
            var method = consoleMethods[length];

            // Only stub undefined methods.
            if (!console[method]) {
                console[method] = noop;
            }
        }

        if (Function.prototype.bind) {
            window.log = Function.prototype.bind.call(console.log, console);
        } else {
            window.log = function () {
                Function.prototype.apply.call(console.log, console, arguments);
            };
        }
    })();

    var template = _.template;
    _.mixin({
        /**
         * @param {string} text
         * @return string The text with HTML comments removed.
         */
        stripComments: function (text) {
            return text.replace(/<!--(.|[\n\r\t])*?-->\r?\n?/g, "");
        },
        /**
         * As the real _.template, calling stripComments on text.
         */
        template: function (text, data, settings) {
            return template(_.stripComments(text), data, settings);
        }
    });

    var Brooklyn = {
        view: BrooklynViews,
        util: BrooklynUtils
    };

    return Brooklyn;
});
