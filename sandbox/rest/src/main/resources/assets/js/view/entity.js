define([
    "underscore", "jquery", "backbone", "text!tpl/home/entity-form.html", "text!tpl/home/key-value.html"
], function (_, $, Backbone, EntityFormHtml, KeyValueHtml) {

    /**
     * Renders the Entity form inside ApplicationWizardStep2.
     */
    var EntityView = Backbone.View.extend({
        id:'entity-form',
        attributes:{
            'style':'padding:10px'
        },
        events:{
            'blur #entity-name':'updateName',
            'change #entity-type':'updateType',
            'click #add-config':'addConfig',
            'click .remove':'removeConfig'
        },
        template:_.template(EntityFormHtml),
        initialize:function () {
            _.bindAll(this, 'render')
            this.entities = []
            var self = this
            $.get('/v1/catalog/entities', {}, function (result) {
                self.entities = result
                self.renderTypeSelector()
            })
        },
        renderTypeSelector:function () {
            var $type = this.$('#entity-type')
            $type.empty()
            _.each(this.entities, function (type) {
                var $typeOption = $('<option/>').val(type).html(type)
                $type.append($typeOption)
            })
            if (_.isEmpty(this.model.get("type"))) {
                $type.val(this.entities[0])
            } else {
                $type.val(this.model.get("type"))
            }
        },
        renderConfigs:function () {
            this.$('#entity-config ul').empty()
            var config = this.model.get("config")
            var self = this
            _.each(config, function (theValue, theKey) {
                this.$('#entity-config ul').append(_.template(KeyValueHtml, {
                    key:theKey,
                    value:theValue
                }))
            })
        },
        renderName:function () {
            this.$('#entity-name').val(this.model.get("name"))
        },
        render:function () {
            this.$el.html(this.template({}))
            this.renderName()
            this.renderConfigs()
            this.renderTypeSelector()
            this.delegateEvents()
            return this
        },
        updateName:function () {
            this.model.set("name", this.$('#entity-name').val())
        },
        updateType:function () {
            this.model.set("type", this.$('#entity-type').val())
        },
        addConfig:function () {
            this.model.addConfig(this.$('#key').val(), this.$('#value').val())
        },
        removeConfig:function (event) {
            var key = this.$(event.currentTarget).siblings('.key').text()
            this.model.removeConfig(key)
        }
    })

    return EntityView
})