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
 *
 * 
 * Based on jquery.slideto.min.js in Swagger UI, added in:
 * https://github.com/wordnik/swagger-ui/commit/d2eb882e5262e135dfa3f5919796bbc3785880b8#diff-bd86720650a2ebd1ab11e870dc475564
 *
 * Swagger UI is distributed under ASL but it is not clear that this code originated in that project.
 * No other original author could be identified.
 *
 * Nearly identical code is referenced here:
 * http://stackoverflow.com/questions/12375440/scrolling-works-in-chrome-but-not-in-firefox-or-ie
 *
 * The project https://github.com/Sleavely/jQuery-slideto is NOT this.
 *
 * Rewritten for readability and to preserve license integrity.
 */
(function(jquery){
jquery.fn.slideto=function(opts) {
    opts = _.extend( {
            highlight: true,
            slide_duration: "slow",
            highlight_duration: 3000,
            highlight_color: "#FFFF99" },
        opts);
    return this.each(function() {
        $target=jquery(this);
        jquery("body").animate(
            { scrollTop: $target.offset().top },
            opts.slide_duration,
            function() {
                opts.highlight && 
                jquery.ui.version && 
                $target.effect(
                    "highlight",
                    { color: opts.highlight_color },
                    opts.highlight_duration)
            })
        });
}}) (jQuery);
