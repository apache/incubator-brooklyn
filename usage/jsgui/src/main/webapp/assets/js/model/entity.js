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

    var Entity = {}

    Entity.Model = Backbone.Model.extend({
        defaults:function () {
            return {
                name:"",
                type:"",
                config:{}
            }
        },
        getVersionedAttr: function(name) {
            var attr = this.get(name);
            var version = this.get('version');
            if (version && version != '0.0.0') {
                return attr + ':' + version;
            } else {
                return attr;
            }
        },
        url: function() {
            var base = _.result(this, 'urlRoot') || _.result(this.collection, 'url') || urlError();
            if (this.isNew()) return base;
            return base + (base.charAt(base.length - 1) === '/' ? '' : '/') + 
                encodeURIComponent(this.get("symbolicName")) + '/' + encodeURIComponent(this.get("version"));
        },
        getConfigByName:function (key) {
            if (key) return this.get("config")[key]
        },
        addConfig:function (key, value) {
            if (key) {
                var configs = this.get("config")
                configs[key] = value
                this.set('config', configs)
                this.trigger("change")
                this.trigger("change:config")
                return true
            }
            return false
        },
        removeConfig:function (key) {
            if (key) {
                var configs = this.get('config')
                delete configs[key]
                this.set('config', configs)
                this.trigger("change")
                this.trigger("change:config")
                return true
            }
            return false
        }
    })

    Entity.Collection = Backbone.Collection.extend({
        model:Entity.Model,
        url:'entity-collection'
    })

    return Entity
})