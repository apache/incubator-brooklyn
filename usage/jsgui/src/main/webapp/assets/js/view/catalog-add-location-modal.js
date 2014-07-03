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
/**
 * Modal dialog to add a new location.
 */
define([
    "underscore", "jquery", "backbone", 
    "text!tpl/catalog/add-location-modal.html", "text!tpl/catalog/location-config.html",
    "bootstrap"
], function (_, $, Backbone, LocationModalHtml, LocationConfigHtml) {

    var LocationModalView = Backbone.View.extend({
        tagName:'div',
        className:'modal hide fade',
        id:'new-location-modal',
        events:{
            'click #add-location-config':'addLocationConfig',
            'click .remove':'removeLocationConfig',
            'click #new-location-submit':'newLocationSubmit',
            'click #show-config-form':'showConfigForm',
            'blur #provider':'updateLocationName'
        },
        template:_.template(LocationModalHtml),
        addedConfigTemplate:_.template(LocationConfigHtml),

        initialize:function () {
            _.bindAll(this, 'render')
        },
        render:function (eventName) {
            this.$el.html(this.template({}))
            this.renderLocationName()
            this.renderAddedConfigs()
            return this
        },
        renderLocationName:function () {
            this.$('#provider').val(this.model.get("provider"))
        },
        renderAddedConfigs:function () {
            var $addedConfigs = this.$('#added-configs ul').empty(),
                that = this
            _.each(this.model.get("config"), function (theValue, theKey) {
                $addedConfigs.append(that.addedConfigTemplate({ key:theKey, value:theValue}))
            })
        },
        removeLocationConfig:function (event) {
            var key = $(event.currentTarget).parent().find('span.key').html()
            this.model.removeConfig(key)
            this.render()
        },
        newLocationSubmit:function (event) {
            event.preventDefault()
            if (this.isNewLocationValid()) {
                var self = this
                Backbone.sync('create', this.model, {
                    success:function () {
                        self.options.appRouter.navigate("v1/locations")
                    }

                })
                this.$el.modal('hide')
                this.$el.empty()
            } else {
                this.showMessage('A location must have a non-empty name and at least one configuration.')
            }
        },
        updateLocationName:function (eventName) {
            this.model.set("provider", this.$('#provider').val())
            this.render()
        },
        isNewLocationValid:function () {
            if (this.model.get("provider").length == 0 || _.isEmpty(this.model.get("config"))) {
                return false
            }
            return true
        },
        addLocationConfig:function (event) {
            // add a new key-value option to the new location form
            var key = $.trim(this.$('input#key').val())
            if (key.length != 0) {
                this.model.addConfig(key, this.$('input#value').val())
                this.render()
            } else {
                this.showMessage('Key must not be empty!')
            }
            return false
        },
        showConfigForm:function () {
            this.$('#config-form').show()
        },
        showMessage:function (message) {
            var $div = this.$('div.info-message')
            $div.find('span#message').html(message)
            $div.show('slow').delay(2000).hide('slow')
            return false
        }
    })

    return LocationModalView
})