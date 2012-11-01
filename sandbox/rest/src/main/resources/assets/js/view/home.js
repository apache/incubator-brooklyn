/**
 * Renders the Applications page. From it we create all other application related views.
 */

define([
    "underscore", "jquery", "backbone", "./modal-wizard",
    "text!tpl/home/applications.html", "text!tpl/home/summaries.html", "text!tpl/home/app-entry.html", 
    "bootstrap"
], function (_, $, Backbone, ModalWizard, ApplicationsHtml, HomeSummariesHtml, AppEntryHtml) {

    var HomeView = Backbone.View.extend({
        tagName:"div",
        className:"container container-fluid",
        events:{
            'click #add-new-application':'createApplication',
            'click .delete':'deleteApplication'
        },
        
        summariesView:{},
        
        initialize:function () {
            var that = this
            
            this.$el.html(_.template(ApplicationsHtml, {} ))
            $(".nav1").removeClass("active");
            $(".nav1_home").addClass("active");
            
            this._appViews = {}
            this.summariesView = new HomeView.HomeSummariesView({
            	applications:this.collection,
            	locations:this.options.locations,
        	})
            this.renderSummaries()
            this.collection.on('reset', this.render, this)
            this.options.locations.on('reset', this.renderSummaries, this)

            id = $(this.$el).find("#circles-map");
            requirejs(["googlemaps"], function (GoogleMaps) {
            		GoogleMaps.addMapToCanvas(id[0], 40.7063, -73.9971, 14)
            	}, function (error) {
            		id.find("#circles-map-message").html("(map not available)"); 
            });
            
            this.callPeriodically(function() {
            	that.refresh(that);     	            	
            }, 5000)
            this.refresh(this)
        },
        
        refresh:function (that) {
        	that.collection.fetch()
        	that.options.locations.fetch()     	
        },
        
        // cleaning code goes here
        beforeClose:function () {
            this.collection.off("reset", this.render)
            this.options.locations.off("reset", this.renderSummaries)
            // iterate over all (sub)views and destroy them
            _.each(this._appViews, function (value) {
                value.close()
            })
            this._appViews = null
        },

        render:function () {
            this.renderSummaries()
            this.renderCollection()
            return this
        },

    	renderSummaries:function () {
        	this.$('.home-summaries-row').html(this.summariesView.render().el )
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

    HomeView.HomeSummariesView = Backbone.View.extend({
    	tagName:'div',
        template:_.template(HomeSummariesHtml),

        initialize:function () {
//            this.apps.on('change', this.render, this)
        },
        render:function () {
            this.$el.html(this.template({
                apps:this.options.applications,
                locations:this.options.locations
            }))
            return this
        },
        beforeClose:function () {
//            this.off("change", this.render)
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