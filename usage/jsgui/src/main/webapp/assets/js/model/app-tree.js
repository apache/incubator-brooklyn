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
    "underscore", "backbone"
], function (_, Backbone) {

    var AppTree = {}

    AppTree.Model = Backbone.Model.extend({
        defaults:function () {
            return {
                id:"",
                name:"",
                type:"",
                iconUrl:"",
                serviceUp:"",
                serviceState:"",
                applicationId:"",
                parentId:"",
                children:[]
            }
        },
        getDisplayName:function () {
            return this.get("name")
        },
        hasChildren:function () {
            return this.get("children").length > 0
        }
    })

    AppTree.Collection = Backbone.Collection.extend({
        model: AppTree.Model,
        includedEntities: [],

        getApplications: function () {
            var entities = [];
            _.each(this.models, function(it) {
                if (it.get('id') == it.get('applicationId'))
                    entities.push(it.get('id'));
            });
            return entities;
        },
        getNonApplications: function () {
            var entities = [];
            _.each(this.models, function(it) {
                if (it.get('id') != it.get('applicationId'))
                    entities.push(it.get('id'));
            });
            return entities;
        },
        includeEntities: function (entities) {
            // accepts id as string or object with id field
            var oldLength = this.includedEntities.length;
            var newList = [].concat(this.includedEntities)
            for (entityId in entities) {
                var entity = entities[entityId]
                if (typeof entity === 'string')
                    newList.push(entity)
                else
                    newList.push(entity.id)
            }
            this.includedEntities = _.uniq(newList)
            return (this.includedEntities.length > oldLength);
        },
        /**
         * Depth-first search of entries in this.models for the first entity whose ID matches the
         * function's argument. Includes each entity's children.
         */
        getEntityNameFromId: function (id) {
            if (!this.models.length) return undefined;

            for (var i = 0, l = this.models.length; i < l; i++) {
                var model = this.models[i];
                if (model.get("id") === id) {
                    return model.getDisplayName()
                } else {
                    // slice(0) makes a shallow clone of the array
                    var queue = model.get("children").slice(0);
                    while (queue.length) {
                        var child = queue.pop();
                        if (child.id === id) {
                            return child.name;
                        } else {
                            if (_.has(child, 'children')) {
                                queue = queue.concat(child.children);
                            }
                        }
                    }
                }
            }

            // Is this surprising? If we returned undefined and the caller concatenates it with
            // a string they'll get "stringundefined", whereas this way they'll just get "string".
            return "";
        },
        url: function() {
            if (this.includedEntities.length) {
                var ids = this.includedEntities.join(",");
                return "/v1/applications/fetch?items="+ids;
            } else
                return "/v1/applications/fetch";
        }
    })

    return AppTree
})