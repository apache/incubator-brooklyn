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
    "brooklyn", "underscore", "jquery", "backbone",
    "model/application", "model/app-tree", "model/location", 
    "model/server-extended-status",
    "view/home", "view/application-explorer", "view/catalog", "view/apidoc", "view/script-groovy",
    "text!tpl/help/page.html","text!tpl/labs/page.html", "text!tpl/home/server-caution.html"
], function (Brooklyn, _, $, Backbone,
        Application, AppTree, Location, 
        serverStatus,
        HomeView, ExplorerView, CatalogView, ApidocView, ScriptGroovyView, 
        HelpHtml, LabsHtml, ServerCautionHtml) {

    var ServerCautionOverlay = Backbone.View.extend({
        template: _.template(ServerCautionHtml),
        scheduledRedirect: false,
        initialize: function() {
            var that = this;
            this.carryOnRegardless = false;
            _.bindAll(this);
            serverStatus.addCallback(this.renderAndAddCallback);
        },
        renderAndAddCallback: function() {
            this.renderOnUpdate();
            serverStatus.addCallback(this.renderAndAddCallback);
        },
        renderOnUpdate: function() {
            var that = this;
            if (this.carryOnRegardless) return this.renderEmpty();
            
            var state = {
                    loaded: serverStatus.loaded,
                    up: serverStatus.isUp(),
                    healthy: serverStatus.isHealthy(),
                    master: serverStatus.isMaster(),
                    masterUri: serverStatus.getMasterUri(),
                };
            if (state.loaded && state.up && state.healthy && state.master) return this.renderEmpty();
            
            this.warningActive = true;
            this.$el.html(this.template(state));
                
            $("#dismiss-standby-warning", this.$el).click(function() {
                that.carryOnRegardless = true;
                if (that.redirectPending) {
                    log("Cancelling redirect, using this non-master instance");
                    clearTimeout(that.redirectPending);
                    that.redirectPending = null;
                }       
                that.renderOnUpdate();
            });
            
            if (!state.master && state.masterUri) {
                if (!this.scheduledRedirect && !this.redirectPending) {
                    log("Not master; will redirect shortly to: "+state.masterUri);
                    var destination = state.masterUri + "#" + Backbone.history.fragment;
                    var time = 10;
                    this.scheduledRedirect = true;
                    log("Redirecting to " + destination + " in " + time + " seconds");
                    this.redirectPending = setTimeout(function () {
                        // re-check, in case the server's status changed in the wait
                        if (!serverStatus.isMaster()) {
                            if (that.redirectPending) {
                                window.location.href = destination;
                            } else {
                                log("Cancelled redirect, using this non-master instance");
                            }
                        } else {
                            log("Cancelled redirect, this instance is now master");
                        }
                    }, time * 1000);
                }
            }
            return this;
        },
        renderEmpty: function() {
            this.warningActive = false;
            this.$el.empty();
            return this;
        },
        beforeClose: function() {
            this.stopListening();
        },
        warnIfNotLoaded: function() {
            if (!this.loaded)
                this.renderOnUpdate();
        }
    });
    // look for ha-standby-overlay for compatibility with older index.html copies
    var serverCautionOverlay = new ServerCautionOverlay({ el: $("#server-caution-overlay").length ? $("#server-caution-overlay") : $("#ha-standby-overlay")});
    serverCautionOverlay.render();
    
    var Router = Backbone.Router.extend({
        routes:{
            'v1/home/*trail':'homePage',
            'v1/applications/:app/entities/*trail':'applicationsPage',
            'v1/applications/*trail':'applicationsPage',
            'v1/applications':'applicationsPage',
            'v1/locations':'catalogPage',
            'v1/catalog/:kind(/:id)':'catalogPage',
            'v1/catalog':'catalogPage',
            'v1/apidoc':'apidocPage',
            'v1/script/groovy':'scriptGroovyPage',
            'v1/help':'helpPage',
            'labs':'labsPage',
            '*path':'defaultRoute'
        },

        showView: function(selector, view) {
            // close the previous view - does binding clean-up and avoids memory leaks
            if (this.currentView) {
                this.currentView.close();
            }
            // render the view inside the selector element
            $(selector).html(view.render().el);
            this.currentView = view;
            return view;
        },

        defaultRoute: function() {
            this.homePage('auto')
        },

        applications: new Application.Collection,
        appTree: new AppTree.Collection,
        locations: new Location.Collection,

        homePage:function (trail) {
            var that = this;
            var veryFirstViewLoad, homeView;
            // render the page after we fetch the collection -- no rendering on error
            function render() {
                homeView = new HomeView({
                    collection:that.applications,
                    locations:that.locations,
                    cautionOverlay:serverCautionOverlay,
                    appRouter:that
                });
                veryFirstViewLoad = !that.currentView;
                that.showView("#application-content", homeView);
            }
            this.applications.fetch({success:function () {
                render();
                // show add application wizard if none already exist and this is the first page load
                if ((veryFirstViewLoad && trail=='auto' && that.applications.isEmpty()) || (trail=='add_application') ) {
                    if (serverStatus.isMaster()) {
                        homeView.createApplication();
                    }
                }
            }, error: render});
        },
        applicationsPage:function (app, trail, tab) {
            if (trail === undefined) trail = app
            var that = this
            this.appTree.fetch({success:function () {
                var appExplorer = new ExplorerView({
                    collection:that.appTree,
                    appRouter:that
                })
                that.showView("#application-content", appExplorer)
                if (trail !== undefined) appExplorer.show(trail)
            }})
        },
        catalogPage: function (catalogItemKind, id) {
            var catalogResource = new CatalogView({
                locations: this.locations,
                appRouter: this,
                kind: catalogItemKind,
                id: id
            });
            this.showView("#application-content", catalogResource);
        },
        apidocPage:function () {
            var apidocResource = new ApidocView({})
            this.showView("#application-content", apidocResource)
            $(".nav1").removeClass("active")
            $(".nav1_script").addClass("active")
            $(".nav1_apidoc").addClass("active")
        },
        scriptGroovyPage:function () {
            if (this.scriptGroovyResource === undefined)
                this.scriptGroovyResource = new ScriptGroovyView({})
            this.showView("#application-content", this.scriptGroovyResource)
            $(".nav1").removeClass("active")
            $(".nav1_script").addClass("active")
            $(".nav1_script_groovy").addClass("active")
        },
        helpPage:function () {
            $("#application-content").html(_.template(HelpHtml, {}))
            $(".nav1").removeClass("active")
            $(".nav1_help").addClass("active")
        },
        labsPage:function () {
            $("#application-content").html(_.template(LabsHtml, {}))
            $(".nav1").removeClass("active")
        },

        /** Triggers the Backbone.Router process which drives this GUI through Backbone.history,
         *  after starting background server health checks and waiting for confirmation of health
         *  (or user click-through). */
        startBrooklynGui: function() {
            serverStatus.whenUp(function() { Backbone.history.start(); });
            serverStatus.autoUpdate();
            _.delay(serverCautionOverlay.warnIfNotLoaded, 2*1000)
        }
    });

    $.ajax({
        type: "GET",
        url: "/v1/server/user",
        dataType: "text"
    }).done(function (data) {
        if (data != null) {
            $("#user").html(_.escape(data));
        }
    });

    return Router
})
