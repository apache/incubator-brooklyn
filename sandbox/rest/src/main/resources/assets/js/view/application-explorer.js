/**
 * This should render the main content in the Application Explorer page.
 * Components on this page should be rendered as sub-views.
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "model/app-tree", "./application-tree", "text!tpl/apps/page.html"
], function (_, $, Backbone, AppTree, ApplicationTreeView, PageHtml) {

    var ApplicationExplorerView = Backbone.View.extend({
        tagName:"div",
        className:"container container-fluid",
        id:'application-explorer',
        template:_.template(PageHtml),
        events:{
            'click .refresh':'refreshApplications'
        },
        initialize:function () {
            this.$el.html(this.template({}))
            this.collection.on('reset', this.render, this)
            this.treeView = new ApplicationTreeView({
                collection:this.collection
            })
            this.$('div#tree-list').html(this.treeView.render().el)
        },
        beforeClose:function () {
            this.collection.off("reset", this.render)
            this.treeView.close()
        },
        render:function () {
            this.treeView.render()
            return this
        },
        refreshApplications:function () {
            this.collection.fetch()
            return false
        }
    })

    return ApplicationExplorerView
})