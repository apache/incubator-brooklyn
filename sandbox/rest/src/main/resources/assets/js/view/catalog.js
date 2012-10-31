define([
    "underscore", "jquery", "backbone", "model/location", "view/location-modal", 
    "view/location-row",
    "text!tpl/catalog/page.html", 
    "text!tpl/catalog/entry.html", "bootstrap"
], function (_, $, Backbone, Location, LocationModalView, LocationRowView, CatalogPageHtml, EntryHtml) {

    var CatalogResourceView = Backbone.View.extend({
        tagName:"div",
        className:"container container-fluid",
        entryTemplate:_.template(EntryHtml),

        events:{
            'click #add-new-entity':'addNewCatalogResource',
            'click #add-new-location':'addLocation',
            'click .delete-location':'deleteLocation',
            'click .catalog-entry-show-details':'showCatalogEntryDetails',
            'click #new-entity-submit':'newEntitySubmit'
        },

        initialize:function () {
            var self = this
            self.$el.html(_.template(CatalogPageHtml, {}))
            this.policyList = []
            this.entityTypes = []
            this.on('change', this.render, this)
            this.model.on('reset', this.renderAddedLocations, this)
        },
        fetchModels:function () {
            var self = this
            $.get('/v1/catalog/policies', {}, function (result) {
                self.policyList = result
                self.trigger('change')
            })
            $.get('/v1/catalog/entities', {}, function (result) {
                self.entityTypes = result
                self.trigger('change')
            })
        },
        renderEntities:function () {
            var self = this
            _.each(self.entityTypes, function (entity, pos) {
                self.$el.find('#entities ul').append(self.entryTemplate({
                    index:pos,
                    catalogEntry:entity,
                    type:'entity'
                }))
            })
        },
        renderPolicies:function () {
            var self = this
            _.each(self.policyList, function (policy, pos) {
                self.$el.find('#policies ul').append(self.entryTemplate({
                    index:pos,
                    catalogEntry:policy,
                    type:'policy'
                }))
            })
        },
        renderAddedLocations:function () {
            var self = this
            self.$el.find('#locations-table-body').children().remove()
            _.each(self.model.models, function (aLocation) {
                self.$el.find('#locations-table-body').append(new LocationRowView({model:aLocation}).render().el)
            }, self)
        },
        render:function (eventName) {
            var self = this
            self.$el.html(_.template(CatalogPageHtml, {}))
            this.renderEntities()
            this.renderPolicies()
            this.renderAddedLocations()
            return this
        },
        showCatalogEntryDetails:function (event) {
            var $currentTarget = $(event.currentTarget)
            var url = '/v1/catalog/entities/' + $currentTarget.siblings("span").text()
            var $details = $currentTarget.siblings('.catalog-entry-details')
            // get the details if they don't exist yet
            if ($details.text().length === 0) {
                $.get(url, function (data) {
                    var $ul = $('<ul/>')
                    _.each(data, function (element) {
                        $ul.append('<li>' + _.escape(element) + '</li>')
                    })
                    $details.html($ul)
                    $details.show()
                })
            } else {
                $details.toggle()
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
        deleteLocation:function (event) {
            this.model.get(event.currentTarget['id']).destroy()
        },
        addLocation:function (event) {
            var locationModalView = new LocationModalView({
                model:new Location.Model(),
                appRouter:this.options.appRouter
            })
            this.$('#new-location-modal').replaceWith(locationModalView.render().$el)
            this.$('#new-location-modal').modal('show')
        }
    })
    return CatalogResourceView
})