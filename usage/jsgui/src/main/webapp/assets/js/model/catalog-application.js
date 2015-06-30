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
    
    var CatalogApplication = {}

    CatalogApplication.Model = Backbone.Model.extend({
        defaults: function () {
            return {
                id: "",
                type: "",
                name: "",
                version: "",
                description: "",
                planYaml: "",
                iconUrl: ""
            }
        }
    })

    CatalogApplication.Collection = Backbone.Collection.extend({
        model: CatalogApplication.Model,
        url: '/v1/catalog/applications',
        getDistinctApplications: function() {
            return this.groupBy('type');
        },
        getTypes: function(type) {
            return _.uniq(this.chain().map(function(model) {return model.get('type')}).value());
        },
        hasType: function(type) {
            return this.where({type: type}).length > 0;
        },
        getVersions: function(type) {
            return this.chain().filter(function(model) {return model.get('type') === type}).map(function(model) {return model.get('version')}).value();
        }
    })

    return CatalogApplication
})