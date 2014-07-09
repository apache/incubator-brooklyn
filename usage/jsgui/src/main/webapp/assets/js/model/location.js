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

    var Location = {}

    Location.Model = Backbone.Model.extend({
        urlRoot:'/v1/locations',
        defaults:function () {
            return {
                id:'',
                name:'',
                spec:'',
                config:{},
                links:{
                    self:''
                }
            }
        },
        idFromSelfLink:function () {
            return this.get('id');
        },
        initialize:function () {
        },
        addConfig:function (key, value) {
            if (key) {
                var configs = this.get("config")
                configs[key] = value
                this.set('config', configs)
                return true
            }
            return false
        },
        removeConfig:function (key) {
            if (key) {
                var configs = this.get('config')
                delete configs[key]
                this.set('config', configs)
                return true
            }
            return false
        },
        getConfigByName:function (name) {
            if (name) return this.get("config")[name]
        },
        getLinkByName:function (name) {
            if (name) return this.get("links")[name]
        },
        hasSelfUrl:function (url) {
            return (this.getLinkByName("self") === url)
        },
        getPrettyName: function() {
            var name = null;
            if (this.get('config') && this.get('config')['displayName'])
                name = this.get('config')['displayName'];
            if (name!=null && name.length>0) return name
            name = this.get('name')
            if (name!=null && name.length>0) return name
            return this.get('spec')
        }

    })

    Location.Collection = Backbone.Collection.extend({
        model:Location.Model,
        url:'/v1/locations'
    })

    Location.UsageLocated = Backbone.Model.extend({
        url:'/v1/locations/usage/LocatedLocations'
    })

    return Location
})