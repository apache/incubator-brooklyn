define([
    "underscore", "jquery", "backbone", "formatJson",
    "model/location", "model/entity",
    "view/catalog-add-location-modal",
    "text!tpl/catalog/page.html",
    "text!tpl/catalog/details-entity.html",
    "text!tpl/catalog/details-generic.html",
    "text!tpl/catalog/nav-entry.html",
    "text!tpl/catalog/details-location.html",

    "bootstrap", "jquery-form"
], function(_, $, Backbone, FormatJSON, Location, Entity, AddLocationModalView,
        CatalogPageHtml, DetailsEntityHtml, DetailsGenericHtml, EntryHtml, LocationDetailsHtml) {

    // TODO: Loading item's details should perform page navigation
    var DetailsView = Backbone.View.extend({

        genericDetails: _.template(DetailsGenericHtml),
        entityDetails: _.template(DetailsEntityHtml),
        locationDetails: _.template(LocationDetailsHtml),

        render: function() {
            this.$el.html("<div class='catalog-details'><h3>Select an entry on the left</h3></div>");
        },

        showDetailsFor: function(event, type) {
            var $event = $(event.currentTarget);
            if ($event.hasClass("active")) return;

            $(".accordion-nav-row").removeClass("active");
            $event.addClass('active');
            var chosenId = $event.attr('id');
            var url, template, Model;
            if (type == 'applications' || type == 'entities') {
                // app templates are just normal entities, in the API
                url = '/v1/catalog/entities/' + chosenId;
                template = this.entityDetails;
                Model = Entity.Model;
            } else if (type == 'locations') {
                url = chosenId;
                template = this.locationDetails;
                Model = Location.Model;
            } else {
                url = '/v1/catalog/' + type + '/' + chosenId;
                template = this.genericDetails;
            }

            // TODO: Set 'Loading' template
            //this.$el.html(this.genericDetails({title: chosenId}));
            var that = this;
            $.ajax({ url: url,
                success: function (data) {
                    var defaults = {
                        "description": undefined,
                        "planYaml": undefined,
                        "sensors": [],
                        "effectors": [],
                        "id": undefined,
                        "name": undefined,
                        "spec": undefined,
                        "config": undefined
                    };
                    if (Model) {
                        defaults['model'] = new Model(data);
                    }
                    that.$el.html(template(_.extend(defaults, data)))
                },
                error: function (xhr, textStatus, error) {
                    that.$el.html(that.genericDetails({
                        title: chosenId,
                        json: FormatJSON({ "status": textStatus, "error": error })
                    }));
                }
            });
        }
    });

    var Catalog = Backbone.Collection.extend({
        initialize: function(models, options) {
            this.name = options["name"];
            if (!this.name) {
                throw new Error("Catalog collection must know its name");
            }
        },
        url: function() {
            return "/v1/catalog/" + this.name;
        }
    });

    var accordionBodyTemplate = _.template(
        "<div class='accordion-head capitalized'><%= name %></div>" +
        "<div class='accordion-body' style='display: <%= display %>'></div>");

    /** Use to fill single accordion view list. */
    var AccordionItemView = Backbone.View.extend({
        tag: "div",
        className: "accordion-item",
        events: {
            'click .accordion-head': 'toggle',
            'click .accordion-nav-row': 'showDetails'
        },

        initialize: function() {
            _.bindAll(this);
            this.name = this.options.name;
            if (!this.name) {
                throw new Error("Name should have been given for accordion entry");
            }

            // Generic templates
            this.template = _.template(this.options.template || EntryHtml);
            // Returns template applied to function arguments. Alter if collection altered. Will be run
            // in the context of the AccordionItemView.
            this.templateFn = this.options.templateFn || function(model, index) {
                return this.template({type: model.get("type"), id: model.get("id")});
            };

            // undefined argument is for existing models
            this.collection = this.options.collection || new Catalog(undefined, {"name": this.name});
            this.collection.on("sync", this.renderEntries);
            this.refresh();
        },
        beforeClose: function() {
            this.collection.off();
        },
        render: function() {
            this.$el.html(accordionBodyTemplate({
                name: this.name,
                display: this.options.autoOpen ? "block" : "none"
            }));
            this.renderEntries();
            return this;
        },
        renderEntries: function() {
            var elements = this.collection.map(this.templateFn, this);
            this.$el.find(".accordion-body")
                .empty()
                .append(elements.join(''));
        },
        refresh: function() {
            this.collection.fetch();
        },
        showDetails: function(event) {
            // TODO: Incorporate model from view collection.
            this.options.details.showDetailsFor(event, this.name);
        },
        toggle: function() {
            var body = this.$(".accordion-body");
            var hidden = this.hidden = body.css("display") == "none";
            if (hidden) {
                this.$el.addClass('active');
                body.removeClass("hide").slideDown('fast');
            } else {
                this.$el.removeClass('active');
                body.slideUp('fast')
            }
        }
    });

    var CatalogResourceView = Backbone.View.extend({
        tagName:"div",
        className:"container container-fluid",
        entryTemplate:_.template(EntryHtml),

        events:{
            'click .refresh':'refresh',
            'click #add-new-thing':'createNewThing',
            'click #add-new-entity':'addNewCatalogResource',
            'click #new-entity-submit':'newEntitySubmit',
            'click #add-new-location':'addLocation',
            'click .delete-location':'deleteLocation'
        },

        initialize: function() {
            $(".nav1").removeClass("active");
            $(".nav1_catalog").addClass("active");
            this.detailsView = new DetailsView();
            this.accordion = this.options.accordion || [
                new AccordionItemView({name: "applications", details: this.detailsView, autoOpen: true}),
                new AccordionItemView({name: "entities", details: this.detailsView}),
                new AccordionItemView({name: "policies", details: this.detailsView}),
                new AccordionItemView({
                    name: "locations",
                    details: this.detailsView,
                    collection: this.options.locations,
                    templateFn: function(location, index) {
                        // this reference is AccordionItemView intentionally
                        return this.template({
                            type: location.getPrettyName(),
                            id: location.getLinkByName("self")
                        });
                    }
                })
            ];
            _.bindAll(this);
        },

        beforeClose: function() {
            _.invoke(this.accordion, 'close');
        },

        render: function(eventName) {
            this.$el.html(_.template(CatalogPageHtml, {}));
            this.detailsView.$el = this.$("#details");
            this.detailsView.render();
            var parent = this.$(".catalog-accordion-parent");
            _.each(this.accordion, function(child) {
                parent.append(child.render().$el);
            });
//            this.accordion[0].toggle();
            return this
        },

        /** Refreshes the contents of each accordion pane */
        refresh: function() {
            _.invoke(this.accordion, 'refresh');
        },

        createNewThing: function(event) {
            var that = this;
            if (_.contains(that.genericTabs, that.activeAccordion)) {
                that.addNewCatalogResource(event)
            } else if (that.activeAccordion=='locations') {
                that.addLocation(event)
            } else {
                that.$('#accordion-empty-to-create-info-message').slideDown('slow').delay(2000).slideUp('slow')
            }
        },

        addNewCatalogResource: function(event) {
            $('#new-entity-modal').modal('show')
        },

        newEntitySubmit: function(event) {
            var $entityForm = $('#new-entity-form'),
                $entityModal = $('#new-entity-modal'),
                self = this;
            $entityModal.fadeTo(500,0.5);
            var options = {
                url:'/v1/catalog/',
                type:'post',
                success: function(data) {
                    $entityModal.modal('hide');
                    $entityModal.fadeTo(500,1);
                    self.refresh()
                },
                error: function(data) {
                    $entityModal.fadeTo(100,1).delay(200).fadeTo(200,0.2).delay(200).fadeTo(200,1);
                    // TODO render the error (want better feedback than this poor-man's flashing)
                }
            };
            $entityForm.ajaxSubmit(options);
            return false
        },
        
        addLocation: function(event) {
            var locationModalView = new AddLocationModalView({
                model:new Location.Model(),
                appRouter:this.options.appRouter
            });
            this.$('#new-location-modal').replaceWith(locationModalView.render().$el);
            this.$('#new-location-modal').modal('show');
        },

        deleteLocation: function(event) {
            this.model.get(event.currentTarget['id']).destroy();
        }
        
    });
    
    return CatalogResourceView
});
