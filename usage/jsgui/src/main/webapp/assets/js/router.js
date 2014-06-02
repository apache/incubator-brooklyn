define([
    "brooklyn", "underscore", "jquery", "backbone",
    "model/application", "model/app-tree", "model/location", "model/ha",
    "view/home", "view/application-explorer", "view/catalog", "view/apidoc", "view/script-groovy", 
    "text!tpl/help/page.html","text!tpl/labs/page.html", "text!tpl/home/server-not-ha-master.html"
], function (Brooklyn, _, $, Backbone,
        Application, AppTree, Location, ha,
        HomeView, ExplorerView, CatalogView, ApidocView, ScriptGroovyView, 
        HelpHtml, LabsHtml, ServerNotMasterHtml) {

    // TODO this initialising - customising the View prototype - should be moved,
    // and perhaps expanded to include other methods from viewutils
    // see discussion at https://github.com/brooklyncentral/brooklyn/pull/939
    
    // add close method to all views for clean-up
    // (NB we have to update the prototype _here_ before any views are instantiated;
    //  see "close" called below in "showView") 
    Backbone.View.prototype.close = function () {
        // call user defined close method if exists
        this.viewIsClosed = true
        if (this.beforeClose) {
            this.beforeClose()
        }
        _.each(this._periodicFunctions, function(i) {
            clearInterval(i)
        })
        this.remove()
        this.unbind()
    }
    Backbone.View.prototype.viewIsClosed = false

    /**
     * Registers a callback (cf setInterval) that is unregistered cleanly when the view
     * closes. The callback is run in the context of the owning view, so callbacks can
     * refer to 'this' safely.
     */
    Backbone.View.prototype.callPeriodically = function (uid, callback, interval) {
        if (!this._periodicFunctions) {
            this._periodicFunctions = {}
        }
        var old = this._periodicFunctions[uid]
        if (old) clearInterval(old)

        // Wrap callback in function that checks whether updates are enabled
        var periodic = function() {
            if (Brooklyn.refresh) {
                callback.apply(this);
            }
        };
        // Bind this to the view
        periodic = _.bind(periodic, this);
        this._periodicFunctions[uid] = setInterval(periodic, interval)
    }

    // Not just defined as a function on Router because the delay if the HA status
    // hasn't loaded requires a reference to the function, which we lose if we use
    // 'this.showView'.
    var showViewImpl = function (router, selector, view) {
        // Don't do anything until the HA status has loaded.
        if (!ha.loaded) {
            _.delay(showViewImpl, 100, router, selector, view);
        } else {
            // close the previous view - does binding clean-up and avoids memory leaks
            if (router.currentView) {
                router.currentView.close();
            }
            // render the view inside the selector element
            $(selector).html(view.render().el);
            router.currentView = view;
            return view
        }
    };

    var Router = Backbone.Router.extend({
        routes:{
            'v1/home/*trail':'homePage',
            'v1/applications/:app/entities/*trail':'applicationsPage',
            'v1/applications/*trail':'applicationsPage',
            'v1/applications':'applicationsPage',
            'v1/locations':'catalogPage',
            'v1/catalog':'catalogPage',
            'v1/apidoc':'apidocPage',
            'v1/script/groovy':'scriptGroovyPage',
            'v1/help':'helpPage',
            'labs':'labsPage',
            '*path':'defaultRoute'
        },

        showView: function(selector, view) {
            showViewImpl(this, selector, view);
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
        catalogPage:function () {
            var that = this
            var catalogResource = new CatalogView({
                locations:that.locations,
                appRouter:that
            })
            that.showView("#application-content", catalogResource)
            catalogResource.refresh()
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
            this.listenTo(ha, "change", this.render);
        },
        render: function() {
            if (!ha.isMaster()) {
                var masterUri = ha.getMasterUri();
                this.$el.html(this.template({"masterUri": masterUri}));
                if (masterUri && !this.scheduledRedirect) {
                    var destination = masterUri + "#" + Backbone.history.fragment;
                    var time = 5;
                    this.scheduledRedirect = true;
                    console.log("Redirecting to " + destination + " in " + time + " seconds");
                    setTimeout(function () {
                        window.location.href = destination;
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

    return Router
})