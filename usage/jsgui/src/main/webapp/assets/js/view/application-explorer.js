/**
 * This should render the main content in the Application Explorer page.
 * Components on this page should be rendered as sub-views.
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "view/viewutils", 
    "./application-add-wizard", "model/app-tree", "./application-tree", 
    "text!tpl/apps/page.html"
], function (_, $, Backbone, ViewUtils, AppAddWizard, AppTree, ApplicationTreeView, PageHtml) {

    var ApplicationExplorerView = Backbone.View.extend({
        tagName:"div",
        className:"container container-fluid",
        id:'application-explorer',
        template:_.template(PageHtml),
        events:{
            'click .application-tree-refresh': 'refreshApplicationsInPlace',
            'click #add-new-application':'createApplication',
            'click .delete':'deleteApplication'
        },
        initialize:function () {
            this.$el.html(this.template({}))
            $(".nav1").removeClass("active");
            $(".nav1_apps").addClass("active");

            this.treeView = new ApplicationTreeView({
                collection:this.collection
            })
            this.$('div#app-tree').html(this.treeView.renderFull().el)
            ViewUtils.fetchRepeatedlyWithDelay(this, this.collection)
        },
        refreshApplicationsInPlace: function() {
            // fetch without reset sets of change events, which now get handled correctly
            // (not a full visual recompute, which reset does - both in application-tree.js)
            this.collection.fetch();
        },
        beforeClose:function () {
            this.collection.off("reset", this.render)
            this.treeView.close()
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
            	callback:function() { that.refreshApplicationsInPlace() }
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
            this.collection.get(event.currentTarget['id']).destroy({wait:true})
        }
        
    })

    return ApplicationExplorerView
})
