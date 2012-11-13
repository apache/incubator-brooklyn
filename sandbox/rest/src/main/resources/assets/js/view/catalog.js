define([
    "underscore", "jquery", "backbone", "model/location", 
    "text!tpl/catalog/page.html", 
    "text!tpl/catalog/details-generic.html",
    "text!tpl/catalog/nav-entry.html", 
    
    "view/catalog-details-location",
    "view/catalog-add-location-modal", 
    
    "formatJson", "bootstrap"
], function (_, $, Backbone, Location, 
        CatalogPageHtml, 
        DetailsGenericHtml, 
        EntryHtml,
        DetailsLocationView,
        AddLocationModalView 
        ) {

    var CatalogResourceView = Backbone.View.extend({
        tagName:"div",
        className:"container container-fluid",
        entryTemplate:_.template(EntryHtml),

        events:{
            'click .accordion-head':'toggleAccordion',
            'click .accordion-nav-row':'showDetailsFor',
            'click .refresh':'refresh',
            'click #add-new-thing':'createNewThing',
            'click #add-new-entity':'addNewCatalogResource',
            'click #new-entity-submit':'newEntitySubmit',
            'click #add-new-location':'addLocation',
            'click .delete-location':'deleteLocation'
        },

        initialize:function () {
            $(".nav1").removeClass("active");
            $(".nav1_catalog").addClass("active");
            this.activeAccordion = null
            this.activeItem = null
            this.genericTabs = ['applications','entities','policies']
            _.bindAll(this, "renderLocationsAccordion");
        },
        fetchModels: function() {},
        render:function (eventName) {
            this.$el.html(_.template(CatalogPageHtml, {}))
            this.$(".accordion-body", this.$el).hide()
            this.renderLocationsAccordion()
            this.refresh()
            return this
        },
        refresh: function() {
            _.each(this.genericTabs, this.refreshGenericAccordion, this)
            this.refreshLocationsAccordion()
        },
        refreshGenericAccordion: function(name) {
            var that = this
            $.get('/v1/catalog/'+name, {}, function (result) {
                that.renderGenericAccordion($("#"+name+" div.accordion-body"), result)
            })            
        },
        renderGenericAccordion: function(accordion, data) {
            accordion.html('')
            _.each(data, function (id, pos) {
                accordion.append(this.entryTemplate({type:id, id:id}));
            }, this)
            accordion.find("div[id='"+this.activeItem+"']").addClass('active')
        },
        refreshLocationsAccordion: function() {
            this.options.locations.fetch({success:this.renderLocationsAccordion})
        },
        renderLocationsAccordion: function() {
            var accordion = $("#"+"locations"+" div.accordion-body")
            accordion.html('')
            _.each(this.options.locations.models, function (loc, pos) {
                accordion.append(this.entryTemplate({
                    type:loc.getPrettyName(), 
                    id: loc.getLinkByName("self")}));
            }, this)
            accordion.find("div[id='"+this.activeItem+"']").addClass('active')
        },
        toggleAccordion: function(event) {
            var hidden = $(event.currentTarget).next()[0].style.display!='block'
            $(event.currentTarget).parent().parent().find('.accordion-head').removeClass('active')
            $(event.currentTarget).parent().parent().find('.accordion-body').hide('fast')
            if (hidden) {
                $(event.currentTarget).addClass('active')
                $(event.currentTarget).next().show('fast')
                this.activeAccordion = $(event.currentTarget).parent().attr('id')
            } else {
                this.activeAccordion = null
            }
        },
        showDetailsFor: function(event) {
            var that = this
            var wasActive = $(event.currentTarget).hasClass('active')
            this.$(".accordion-nav-row").removeClass('active')
            $(".catalog-details").hide()
            if (!wasActive) {
                $(event.currentTarget).addClass('active')
                that.activeItem = $(event.currentTarget).attr('id')
                if (_.contains(that.genericTabs, that.activeAccordion)) {
                    var url;
                    if (that.activeAccordion == 'applications')
                        // app templates are just normal entities, in the API
                        url = '/v1/catalog/'+'entities'+'/' + that.activeItem
                    else
                        url = '/v1/catalog/'+that.activeAccordion+'/' + that.activeItem
                    $("#details-"+that.activeAccordion).html(_.template(DetailsGenericHtml, {
                        title: that.activeItem
                    }));
                    $.get(url, function (data) {
                        $("#details-"+that.activeAccordion).html(_.template(DetailsGenericHtml, {
                            title: that.activeItem,
                            json: FormatJSON(data)
                        }))
                    })
                } else {
                    that.showLocationDetails(that.activeItem)
                }
                $("#details-"+this.activeAccordion).show()
            //TODO locations
            } else {
                this.activeItem = null
            }
        },
        showLocationDetails: function(id) {
            var pane = $('#details-locations')
            var l = _.find(this.options.locations.models, function(it) { return id == it.getLinkByName("self") });
            pane.html(new DetailsLocationView({model:l}).render().el)
        },
        
        createNewThing: function(event) {
            var that = this
            if (_.contains(that.genericTabs, that.activeAccordion)) {
                that.addNewCatalogResource(event)
            } else if (that.activeAccordion=='locations') {
                that.addLocation(event)
            } else {
                that.$('#accordion-empty-to-create-info-message').show('slow').delay(2000).hide('slow')
            }
        },

        addNewCatalogResource:function (event) {
            $('#new-entity-modal').modal('show')
        },
        newEntitySubmit:function (event) {
            var $entityForm = $('#new-entity-form'),
                $entityModal = $('#new-entity-modal'),
                self = this
            var options = {
                success:function (data) {
                    $entityModal.modal('hide')
                    self.fetchModels()
                },
                url:'/v1/catalog/',
                type:'post'
            }
            $entityForm.ajaxSubmit(options)
            return false
        },
        
        addLocation:function (event) {
            var locationModalView = new AddLocationModalView({
                model:new Location.Model(),
                appRouter:this.options.appRouter
            })
            this.$('#new-location-modal').replaceWith(locationModalView.render().$el)
            this.$('#new-location-modal').modal('show')
        },
        deleteLocation:function (event) {
            this.model.get(event.currentTarget['id']).destroy()
        }
        
    })
    
    return CatalogResourceView
})