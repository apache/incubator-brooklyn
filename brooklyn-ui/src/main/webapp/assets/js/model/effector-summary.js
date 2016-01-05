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

    var EffectorSummary = {}

    EffectorSummary.Model = Backbone.Model.extend({
        idAttribute: 'name',
        defaults:function () {
            return {
                name:"",
                description:"",
                returnType:"",
                parameters:[],
                links:{
                    self:"",
                    entity:"",
                    application:""
                }
            }
        },
        getParameterByName:function (name) {
            if (name) {
                return _.find(this.get("parameters"), function (param) {
                    return param.name == name
                })
            }
        },
        getLinkByName:function (name) {
            if (name) return this.get("links")[name]
        }
    })

    EffectorSummary.Collection = Backbone.Collection.extend({
        model:EffectorSummary.Model
    })

    return EffectorSummary
})