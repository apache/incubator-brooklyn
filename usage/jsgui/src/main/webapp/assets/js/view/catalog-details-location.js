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
    "underscore", "jquery", "backbone", "text!tpl/catalog/details-location.html"
], function (_, $, Backbone, LocationDetailsHtml) {
    /**
     * Renders one location element.
     */
    var LocationDetailsView = Backbone.View.extend({
        template:_.template(LocationDetailsHtml),

        initialize:function () {
//            this.model.bind('change', this.render, this)
            this.model.bind('destroy', this.close, this)
        },

        render:function (eventName) {
            this.$el.html(this.template({
                title: this.model.getPrettyName(),
                id: this.model.id,
                name: this.model.get('name'),
                spec: this.model.get('spec'),
                config: this.model.get("config")
            }))
            if (_.size(this.model.get("config"))==0) {
                $(".has-no-config", this.$el).show()
            }
            
            return this
        },

        close:function (eventName) {
//            this.$el.unbind()
//            this.$el.remove()
        }
    })

    return LocationDetailsView
})