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

    // NB: THIS IS NOT USED CURRENTLY
    // the logic in application-add-wizard.js simply loads and manipulates json;
    // logic in catalog.js (view) defines its own local model
    // TODO change those so that they use this backbone model + collection,
    // allowing a way to specify on creation what we are looking up in the catalog -- apps or entities or policies
    
    var CatalogItem = {}

    CatalogItem.Model = Backbone.Model.extend({
        defaults:function () {
            return {
                id:"",
                name:"",
                type:"",
                description:"",
                planYaml:"",
                iconUrl:""
            }
        }
    })

    CatalogItem.Collection = Backbone.Collection.extend({
        model:CatalogItem.Model,
        url:'/v1/catalog'  // TODO is this application or entities or policies? (but note THIS IS NOT USED)
    })

    return CatalogItem
})