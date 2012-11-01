define([
    'underscore', 'jquery', 'backbone', "model/application", "model/app-tree", "model/location",
    "view/home", "view/application-explorer", "view/catalog"
], function (_, $, Backbone, Application, AppTree, Location, HomeView, ExplorerView, CatalogView) {

    // add close method to all views for clean-up
    Backbone.View.prototype.close = function () {
        // call user defined close method if exists
        if (this.beforeClose) {
            this.beforeClose()
        }
        for (var index in this._periodicFunctions) {
            clearInterval(this._periodicFunctions[index])
        }
        this.remove()
        this.unbind()
    }

    Backbone.View.prototype.callPeriodically = function (callback, interval) {
        if (!this._periodicFunctions) {
            this._periodicFunctions = []
        }
        this._periodicFunctions.push(setInterval(callback, interval))
    }

    var Router = Backbone.Router.extend({
        routes:{
            'v1/home':'homePage',
            'v1/applications':'applicationsPage',
            'v1/locations':'catalogPage',
            'v1/catalog':'catalogPage',
            '*path':'defaultRoute'
        },
        showView:function (selector, view) {
            // close the previous view - does binding clean-up and avoids memory leaks
            if (this.currentView) this.currentView.close()
            // render the view inside the selector element
            $(selector).html(view.render().el)
            this.currentView = view
            return view
        },
        defaultRoute:function () {
            this.homePage()
        },
        homePage:function () {
            var that = this,
                applications = new Application.Collection
            // render the page after we fetch the collection -- no rendering on error
            applications.fetch({success:function () {
                var homeView = new HomeView({
                    collection:applications,
                    appRouter:that
                })
                that.showView("#application-content", homeView)
            }})
        },
        applicationsPage:function () {
            var that = this,
                appTree = new AppTree.Collection
            appTree.fetch({success:function () {
                var appExplorer = new ExplorerView({
                    collection:appTree,
                    appRouter:that
                })
                that.showView("#application-content", appExplorer)
            }})
        },
        catalogPage:function () {
            var that = this,
                locations = new Location.Collection
            locations.fetch({ success:function () {
                var catalogResource = new CatalogView({
                    model:locations,
                    appRouter:that
                })
                catalogResource.fetchModels()
                that.showView("#application-content", catalogResource)
            }})
        }
    })

    return Router
})