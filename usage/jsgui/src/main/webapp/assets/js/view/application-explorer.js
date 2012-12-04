/**
 * This should render the main content in the Application Explorer page.
 * Components on this page should be rendered as sub-views.
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", 
    "./application-add-wizard", "model/app-tree", "./application-tree", 
    "text!tpl/apps/page.html"
], function (_, $, Backbone, AppAddWizard, AppTree, ApplicationTreeView, PageHtml) {

    var ApplicationExplorerView = Backbone.View.extend({
        tagName:"div",
        className:"container container-fluid",
        id:'application-explorer',
        template:_.template(PageHtml),
        events:{
            'click .refresh':'refreshApplications',
            'click #add-new-application':'createApplication',
            'click .delete':'deleteApplication'
        },
        initialize:function () {
            this.$el.html(this.template({}))
            $(".nav1").removeClass("active");
            $(".nav1_apps").addClass("active");

            this.collection.on('reset', this.render, this)
            this.treeView = new ApplicationTreeView({
                collection:this.collection
            })
            this.$('div#tree-list').html(this.treeView.render().el)
            this.treeView.render()
        },
        beforeClose:function () {
            this.collection.off("reset", this.render)
            this.treeView.close()
        },
        render:function () {
            return this
        },
        
        refreshApplications:function () {
            this.collection.fetch()
            return false
        },
        show: function(entityId) {
            this.treeView.displayEntityId(entityId)
        },
        preselectTab: function(tab) {
            this.treeView.preselectTab(tab)
        },
        
        createApplication:function () {
        	var that = this;
            if (this._modal) {
                this._modal.close()
            }
            var wizard = new AppAddWizard({
            	appRouter:that.options.appRouter,
            	callback:function() { that.refreshApplications() }
        	})
            this._modal = wizard
            this.$(".add-app #modal-container").html(wizard.render().el)
            this.$(".add-app #modal-container .modal")
                .on("hidden",function () {
                    wizard.close()
                }).modal('show')
        },
        deleteApplication:function (event) {
            // call Backbone destroy() which does HTTP DELETE on the model
            this.collection.getByCid(event.currentTarget['id']).destroy({wait:true})
        }
        
    })

    return ApplicationExplorerView
})