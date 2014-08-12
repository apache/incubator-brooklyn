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
 * Render an entity effector as a modal.
 */
define([
    "underscore", "jquery", "backbone",
    "model/location",
    "text!tpl/apps/effector-modal.html",
    "text!tpl/app-add-wizard/deploy-location-row.html", 
    "text!tpl/app-add-wizard/deploy-location-option.html",
    "text!tpl/apps/param.html",
    "text!tpl/apps/param-list.html",
    "bootstrap"
], function (_, $, Backbone, Location, EffectorModalHtml, 
        DeployLocationRowHtml, DeployLocationOptionHtml, ParamHtml, ParamListHtml) {

    var EffectorInvokeView = Backbone.View.extend({
        template:_.template(EffectorModalHtml),
        locationRowTemplate:_.template(DeployLocationRowHtml),
        locationOptionTemplate:_.template(DeployLocationOptionHtml),
        effectorParam:_.template(ParamHtml),
        effectorParamList:_.template(ParamListHtml),

        events:{
            "click .invoke-effector":"invokeEffector",
            "shown": "onShow",
            "hide": "onHide"
        },

        initialize:function () {
            this.locations = this.options.locations /* for testing */
              || new Location.Collection();
        },

        onShow: function() {
            this.delegateEvents();
            this.$el.fadeTo(500,1);
        },

        onHide: function() {
            this.undelegateEvents();
        },

        render:function () {
            var that = this, params = this.model.get("parameters")
            this.$el.html(this.template({
                name:this.model.get("name"),
                entityName:this.options.entity.get("name"),
                description:this.model.get("description")?this.model.get("description"):""
            }))
            // do we have parameters to render?
            if (params.length !== 0) {
                this.$(".modal-body").html(this.effectorParamList({}))
                // select the body of the table we just rendered and append params
                var $tbody = this.$("tbody")
                _(params).each(function (param) {
                    $tbody.append(that.effectorParam({
                        name:param.name,
                        type:param.type,
                        description:param.description?param.description:"",
                        defaultValue:param.defaultValue
                    }))
                })
                var container = this.$("#selector-container")
                if (container.length) {                    
                    this.locations.fetch({async:false})
                    container.empty()
                    var chosenLocation = this.locations[0];
                    container.append(that.locationRowTemplate({
                        initialValue : chosenLocation,
                        rowId : 0
                    }))
                    var $selectLocations = container.find('#select-location')
                    this.locations.each(function(aLocation) {
                        var $option = that.locationOptionTemplate({
                            id:aLocation.id,
                            url:aLocation.getLinkByName("self"),
                            name:aLocation.getPrettyName()
                        })
                        $selectLocations.append($option)
                    })
                    $selectLocations.each(function(i) {
                        var url = $($selectLocations[i]).parent().attr('initialValue');
                        $($selectLocations[i]).val(url)
                    })
                }
            }
            this.$(".modal-body").find('*[rel="tooltip"]').tooltip()
            return this
        },

        extractParamsFromTable:function () {
            var parameters = {}
            
            // iterate over the rows
            this.$(".effector-param").each(function (index) {
                var key = $(this).find(".param-name").text();
                var value = $(this).find(".param-value").attr('id') == 'selector-container' ? 
                        $(this).find(".param-value option:selected").attr("value") : 
                        $(this).find(".param-value").val();
                parameters[key] = value;
            })
            return parameters
        },

        invokeEffector:function () {
            var that = this
            var url = this.model.getLinkByName("self")
            var parameters = this.extractParamsFromTable()
            this.$el.fadeTo(500,0.5);
            $.ajax({
                type:"POST",
                url:url+"?timeout=0",
                data:JSON.stringify(parameters),
                contentType:"application/json",
                success:function (data) {
                    that.$el.modal("hide")
                    that.$el.fadeTo(500,1);
                    if (that.options.openTask)
                        that.options.tabView.openTab('activities/subtask/'+data.id);
                },
                error: function(data) {
                    that.$el.fadeTo(100,1).delay(200).fadeTo(200,0.2).delay(200).fadeTo(200,1);
                    // TODO render the error better than poor-man's flashing
                    // (would just be connection error -- with timeout=0 we get a task even for invalid input)
                    
                    console.error("ERROR invoking effector")
                    console.debug(data)
                }})
            // un-delegate events
            this.undelegateEvents()
        }

    })
    return EffectorInvokeView
})
