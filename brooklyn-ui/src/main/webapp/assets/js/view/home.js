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
 * Renders the Applications page. From it we create all other application related views.
 */

define([
    "jquery", "underscore", "backbone",
    "view/viewutils", 
    "view/application-add-wizard",
    "view/ha-summary",
    "model/location",
    "text!tpl/home/applications.html",
    "text!tpl/home/summaries.html",
    "text!tpl/home/app-entry.html",
    "bootstrap", "brooklyn-utils"
], function ($, _, Backbone, ViewUtils,
        AppAddWizard, HASummary, Location,
        ApplicationsHtml, HomeSummariesHtml, AppEntryHtml) {

    var HomeView = Backbone.View.extend({
        tagName:"div",
        events:{
            'click #add-new-application':'createApplication',
            'click #reload-brooklyn-properties': 'reloadBrooklynProperties',
            'click #clear-ha-node-records': 'clearHaNodeRecords',
            'click .addApplication':'createApplication'
        },
        
        initialize:function () {
            var that = this
            this.$el.html(_.template(ApplicationsHtml, {} ))
            $(".nav1").removeClass("active");
            $(".nav1_home").addClass("active");
            this._appViews = {}
            this.summariesView = new HomeView.HomeSummariesView({
                applications:this.collection,
                locations:this.options.locations
            })
            this.collection.on('reset', this.render, this)
            this.options.locations.on('reset', this.renderSummaries, this)

            ViewUtils.fetchRepeatedlyWithDelay(this, this.collection, 
                    { fetchOptions: { reset: true }, doitnow: true, 
                    /* max is short here so the console becomes usable quickly */
                    backoffMaxPeriod: 10*1000 });
            ViewUtils.fetchRepeatedlyWithDelay(this, this.options.locations, { fetchOptions: { reset: true }, doitnow: true });

            var id = $(this.$el).find("#circles-map");
            if (this.options.offline) {
                id.find("#circles-map-message").html("(map off in offline mode)");
            } else {
                requirejs(["googlemaps"], function (GoogleMaps) {
                    _.defer( function() {
                        log("loading google maps")
                        var map = GoogleMaps.addMapToCanvas(id[0], 0, 0, 1);
                        var locatedLocations = new Location.UsageLocated()
                        // googlemaps.js isn't re-loaded during tab-to-tab navigation so we need to reset it each time
                        // the maps is re-drawn to reset the cached set of location markers
                        GoogleMaps.resetCircles()
                        that.updateCircles(that, locatedLocations, GoogleMaps, map)
                        that.callPeriodically("circles", function() {
                            that.updateCircles(that, locatedLocations, GoogleMaps, map)
                        }, 10000)
                    })
                }, function (error) {
                        id.find("#circles-map-message").html("(map not available)"); 
                });
            }            
        },
        
        updateCircles: function(that, locatedLocations, GoogleMaps, map) {
            locatedLocations.fetch({success:function() {
                GoogleMaps.drawCircles(map, locatedLocations.attributes)
            }})
        },
        
        // cleaning code goes here
        beforeClose:function () {
            this.haSummaryView.close();
            this.collection.off("reset", this.render)
            this.options.locations.off("reset", this.renderSummaries)
            _.invoke(this._appViews, "close");
            this._appViews = null
        },

        render:function () {
            this.renderSummaries();
            this.renderCollection();
            this.renderHighAvailabilitySummary();
            return this;
        },

        renderSummaries:function () {
            this.$('.home-summaries-row').html(this.summariesView.render().el )
        },

        renderHighAvailabilitySummary: function() {
            // The HA view handles updates itself.
            if (!this.haSummaryView)
                this.haSummaryView = new HASummary({ el: this.$("#ha-summary") }).render();
        },
        
        renderCollection:function () {
            var $tableBody = this.$('#applications-table-body').empty()
            if (this.collection==null)
                $tableBody.append("<tr><td colspan='3'><i>No data available</i></td></tr>");
            else if (this.collection.isEmpty())
                $tableBody.append("<tr><td colspan='3'><i>No applications deployed</i></td></tr>");
            else this.collection.each(function (app) {
                var appView = new HomeView.AppEntryView({model:app})
                if (this._appViews[app.cid]) {
                    // if the application has a view destroy it
                    this._appViews[app.cid].close()
                }
                this._appViews[app.cid] = appView
                $tableBody.append(appView.render().el)
            }, this)
        },

        createApplication:function () {
            if (this._modal) {
                this._modal.close()
            }
            var that = this;
            if (this.options.offline || (this.options.cautionOverlay && this.options.cautionOverlay.warningActive)) {
                // don't show wizard
            } else {
                var wizard = new AppAddWizard({appRouter:this.options.appRouter})
                this._modal = wizard
                this.$(".add-app #modal-container").html(wizard.render().el)
                this.$(".add-app #modal-container .modal")
                    .on("hidden",function () {
                        wizard.close()
                        that.collection.fetch({reset:true});
                    }).modal('show')
            }
        },

        reloadBrooklynProperties: function() {
            var self = this;
            // indicate submitted
            self.$('#reload-brooklyn-properties-indicator').show();
            $.ajax({
                type: "POST",
                url: "/v1/server/properties/reload",
                contentType: "application/json",
                success: function() {
                    console.log("reloaded brooklyn properties");
                    self.options.locations.fetch();
                    // clear submitted indicator
                    setTimeout(function() { self.$('#reload-brooklyn-properties-indicator').hide(); }, 250);
                },
                error: function(data) {
                    // TODO render the error better than poor-man's flashing
                    // (would just be connection error -- with timeout=0 we get a task even for invalid input)
                    self.$el.fadeTo(100,1).delay(200).fadeTo(200,0.2).delay(200).fadeTo(200,1);
                    self.$('#reload-brooklyn-properties-indicator').hide();
                    console.error("ERROR reloading brooklyn properties");
                    console.debug(data);
                }
            });
        },
        
        clearHaNodeRecords: function() {
            var self = this;
            // indicate submitted
            self.$('#clear-ha-node-records-indicator').show();
            $.ajax({
                type: "POST",
                url: "/v1/server/ha/states/clear",
                contentType: "application/json",
                success: function() {
                    console.log("cleared HA node records");
                    self.haSummaryView.updateNow();
                    // clear submitted indicator
                    setTimeout(function() { self.$('#clear-ha-node-records-indicator').hide(); }, 250);
                },
                error: function(data) {
                    // TODO render the error better than poor-man's flashing
                    // (would just be connection error -- with timeout=0 we get a task even for invalid input)
                    self.$el.fadeTo(100,1).delay(200).fadeTo(200,0.2).delay(200).fadeTo(200,1);
                    self.$('#clear-ha-node-records-indicator').hide();
                    console.error("ERROR clearing HA nodes");
                    console.debug(data);
                }
            });
        }
    })

    HomeView.HomeSummariesView = Backbone.View.extend({
        tagName:'div',
        template:_.template(HomeSummariesHtml),
        // no listening needed here; it's done by outer class
        render:function () {
            this.$el.html(this.template({
                apps:this.options.applications,
                locations:this.options.locations
            }))
            return this
        },
    })
    
    HomeView.AppEntryView = Backbone.View.extend({
        tagName:'tr',

        template:_.template(AppEntryHtml),

        initialize:function () {
            this.model.on('change', this.render, this)
            this.model.on('destroy', this.close, this)
        },
        render:function () {
            this.$el.html(this.template({
                cid:this.model.cid,
                link:this.model.getLinkByName("self"),
                name:this.model.getSpec().get("name"),
                status:this.model.get("status")
            }))
            return this
        },
        beforeClose:function () {
            this.off("change", this.render)
            this.off("destroy", this.close)
        }
    })

    return HomeView
})
