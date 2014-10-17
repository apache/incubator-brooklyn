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
    "model/application", "model/app-tree", "model/location", "model/ha",
    "view/home", "view/application-explorer", "view/catalog", "view/apidoc", "view/script-groovy",
    "text!tpl/help/page.html","text!tpl/labs/page.html", "text!tpl/home/server-not-ha-master.html"
], function (Brooklyn, _, $, Backbone,
        Application, AppTree, Location, ha,
        HomeView, ExplorerView, CatalogView, ApidocView, ScriptGroovyView,
        HelpHtml, LabsHtml, ServerNotMasterHtml) {

    /**
     * @returns {jquery.Deferred}
     *      A promise that resolves when the high availability status has been
     *      loaded. Actions to be taken on the view after it has loaded should
     *      be registered with calls to .done()
     */
    // Not just defined as a function on Router because the delay if the HA status
    // hasn't loaded requires a reference to the function, which we lose if we use
    // 'this.showView'.
    function showViewImpl(router, selector, view) {
        // Don't do anything until the HA status has loaded.
        var promise = $.Deferred()
            .done(function () {
                // close the previous view - does binding clean-up and avoids memory leaks
                if (router.currentView) {
                    router.currentView.close();
                }
                // render the view inside the selector element
                $(selector).html(view.render().el);
                router.currentView = view;
                return view
            });
        (function isComplete() {
            if (ha.loaded) {
                promise.resolve();
            } else {
                _.defer(isComplete, 100);
            }
        })();
        return promise;
    }

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
            return showViewImpl(this, selector, view);
        },

        defaultRoute: function() {
            this.homePage('auto')
        },

        applications: new Application.Collection,
        appTree: new AppTree.Collection,
        locations: new Location.Collection,

        homePage:function (trail) {
            var that = this;
            // render the page after we fetch the collection -- no rendering on error
            this.applications.fetch({success:function () {
                var homeView = new HomeView({
                    collection:that.applications,
                    locations:that.locations,
                    appRouter:that
                });
                var veryFirstViewLoad = !that.currentView;
                that.showView("#application-content", homeView);
                // show add application wizard if none already exist and this is the first page load
                if ((veryFirstViewLoad && trail=='auto' && that.applications.isEmpty()) ||
                     (trail=='add_application') ) {
                    ha.onLoad(function() {
                        if (ha.isMaster()) {
                            homeView.createApplication();
                        }
                    });
                }
            }})
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
        }
    })

    var HaStandbyOverlay = Backbone.View.extend({
        template: _.template(ServerNotMasterHtml),
        scheduledRedirect: false,
        initialize: function() {
            this.carryOnRegardless = false;
            this.listenTo(ha, "change", this.render);
        },
        render: function() {
            var that = this;
            
            if (!ha.isMaster() && !this.carryOnRegardless) {
                var masterUri = ha.getMasterUri();
                this.$el.html(this.template({"masterUri": masterUri}));
                
                log("render, redirect = "+this.redirectPending);
                
                $("#dismiss-standby-warning", this.$el).click(function() {
                    that.carryOnRegardless = true;
                    if (that.redirectPending) {
                        console.log("Cancelling redirect, using this non-master instance");
                        clearTimeout(that.redirectPending);
                        that.redirectPending = null;
                    }       
                    that.render();
                });
                
                if (masterUri && !this.scheduledRedirect && !this.redirectPending) {
                    var destination = masterUri + "#" + Backbone.history.fragment;
                    var time = 10;
                    this.scheduledRedirect = true;
                    console.log("Redirecting to " + destination + " in " + time + " seconds");
                    this.redirectPending = setTimeout(function () {
                        // re-check, in case the server's status changed in the wait
                        if (!ha.isMaster()) {
                            if (that.redirectPending) {
                                window.location.href = destination;
                            } else {
                                console.log("Cancelled redirect, using this non-master instance");
                            }
                        } else {
                            console.log("Cancelled redirect, this instance is now master");
                        }
                    }, time * 1000);
                }
            } else {
                this.$el.empty();
            }
            return this;
        },
        beforeClose: function() {
            this.stopListening();
        }
    });
    new HaStandbyOverlay({ el: $("#ha-standby-overlay") }).render();


    $.ajax({
        type: "GET",
        url: "/v1/server/user",
        dataType: "text"
    }).done(function (data) {
        if (data != null) {
            $("#user").html(data);
        }
    });

    return Router
})
