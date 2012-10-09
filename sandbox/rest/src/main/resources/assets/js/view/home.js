/**
 * Renders the Applications page. From it we create all other application related views.
 */

define([
    "underscore", "jquery", "backbone", "./modal-wizard", "text!tpl/home/applications.html",
    "text!tpl/home/app-entry.html", "bootstrap"
], function (_, $, Backbone, ModalWizard, ApplicationsHtml, AppEntryHtml) {

    var HomeView = Backbone.View.extend({
        tagName:"div",
        className:"container container-fluid",
        events:{
            'click #add-new-application':'createApplication',
            'click .delete':'deleteApplication'
        },
        initialize:function () {
            var that = this
            this.$el.html(_.template(ApplicationsHtml, {}))
            this.collection.on('reset', this.renderCollection, this)
            this.callPeriodically(function () {
                that.collection.fetch()
            }, 5000)
            this._appViews = {}
        },
        // cleaning code goes here
        beforeClose:function () {
            this.collection.off("reset", this.render)
            // iterate over all views and destroy them
            _.each(this._appViews, function (value) {
                value.close()
            })
            this._appViews = null
        },

        render:function () {
            this.renderCollection()
            return this
        },

        renderCollection:function () {
            var $tableBody = this.$('#applications-table-body').empty()
            this.collection.each(function (app) {
                var appView = new HomeView.AppEntryView({model:app})
                if (this._appViews[app.cid]) {
                    // if the application has a view destroy it
                    this._appViews[app.cid].destroy()
                }
                this._appViews[app.cid] = appView
                $tableBody.append(appView.render().el)
            }, this)
        },

        createApplication:function () {
            if (this._modal) {
                this._modal.close()
            }
            var wizard = new ModalWizard({appRouter:this.options.appRouter})
            this._modal = wizard
            this.$("#modal-container").html(wizard.render().el)
            this.$("#modal-container .modal")
                .on("hidden",function () {
                    wizard.close()
                }).modal('show')
        },

        deleteApplication:function (event) {
            // call Backbone destroy() which does HTTP DELETE on the model
            this.collection.getByCid(event.currentTarget['id']).destroy({wait:true})
        }
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