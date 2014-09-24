/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

var brooklyn = (function ($, _) {

    return {
        findItemOfType: function(items, type) {
            return _.findWhere(items, { type: type });
        },

        entityCard: _.template(
            "<a class='plain' data-type='<%= type %>' href='entities/<%= type %>.html'>" +
            "<div class='card'>" +
            "<span class='glyphicon glyphicon-chevron-right'/>" +
            "<div class='name'><%=name%></div>" +
            "<div class='type'><%=type%></div>" +
            "<div class='description'><%=description%></div>" +
            "</div>" +
            "</a>"
        ),
        policyCard: _.template(
            "<a class='plain' data-type='<%= type %>' href='policies/<%= type %>.html'>" +
            "<div class='card'>" +
            "<span class='glyphicon glyphicon-chevron-right'/>" +
            "<div class='name'><%=name%></div>" +
            "<div class='type'><%=type%></div>" +
            "<div class='description'><%=description%></div>" +
            "</div>" +
            "</a>"
        ),
        enricherCard: _.template(
            "<a class='plain' data-type='<%= type %>' href='enrichers/<%= type %>.html'>" +
            "<div class='card'>" +
            "<span class='glyphicon glyphicon-chevron-right'/>" +
            "<div class='name'><%=name%></div>" +
            "<div class='type'><%=type%></div>" +
            "<div class='description'><%=description%></div>" +
            "</div>" +
            "</a>"
        ),

        typeSummary: _.template(
            "<div class='summaryLabel'><%=name%></div>" +
            "<div class='summaryType'><%=type%></div>" +
            "<div class='description'><%=description%></div>"
        ),

        configKeyCard: _.template(
            "<div class='card configKey'>" +
            "<div class='name'><%=name%></div>" +
            "<dl>" +
            "<dt>description</dt><dd><%=(description||'&nbsp;')%></dd>" +
            "<dt>value type</dt><dd class='java'><%=(type||'&nbsp;')%></dd>" +
            "<dt>default value</dt><dd><%=(defaultValue||'&nbsp;')%></dd>" +
            "</dl>" +
            "</div>"
        ),
        sensorCard: _.template(
            "<div class='card sensor'>" +
            "<div class='name'><%=name%></div>" +
            "<dl>" +
            "<dt>description</dt><dd><%=(description||'&nbsp;')%></dd>" +
            "<dt>value type</dt><dd class='java'><%=(type||'&nbsp;')%></dd>" +
            "</dl>" +
            "</div>"
        ),
        effectorCard: _.template(
            "<div class='card effector'>" +
            "<div class='name'><%=name%></div>" +
            "<dl>" +
            "<dt>description</dt><dd><%=(description||'&nbsp;')%></dd>" +
            "<dt>return type</dt><dd class='java'><%=(returnType||'&nbsp;')%></dd>" +
            "</dl>" +
            "</div>"
        )
    };

}(jQuery, _));
