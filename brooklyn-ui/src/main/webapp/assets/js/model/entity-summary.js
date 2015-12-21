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
define(["underscore", "backbone"], function (_, Backbone) {

    var EntitySummary = {}

    EntitySummary.Model = Backbone.Model.extend({
        defaults:function () {
            return {
                'id':'',
                'name':'',
                'type':'',
                'catalogItemId':'',
                'links':{}
            }
        },
        getLinkByName:function (name) {
            if (name) return this.get("links")[name]
        },
        getDisplayName:function () {
            var name = this.get("name")
            if (name) return name;
            var type = this.get("type")
            var appId = this.getLinkByName('self')
            if (type && appId) {
                return type.slice(type.lastIndexOf('.') + 1) + ':' + appId.slice(appId.lastIndexOf('/') + 1)
            }
        },
        getSensorUpdateUrl:function () {
            return this.getLinkByName("self") + "/sensors/current-state"
        },
        getConfigUpdateUrl:function () {
            return this.getLinkByName("self") + "/config/current-state"
        }
    })

    EntitySummary.Collection = Backbone.Collection.extend({
        model:EntitySummary.Model,
        url:'entity-summary-collection',
        findByDisplayName:function (displayName) {
            if (displayName) return this.filter(function (element) {
                return element.getDisplayName() === displayName
            })
        }
    })

    return EntitySummary
})