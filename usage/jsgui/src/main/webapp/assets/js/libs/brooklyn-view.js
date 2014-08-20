/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
define([
    "jquery", "underscore", "backbone", "brooklyn-utils",
    "text!tpl/lib/basic-modal.html",
    "text!tpl/lib/config-key-type-value-input-pair.html"
    ], function (
    $, _, Backbone, Util,
    ModalHtml, ConfigKeyInputHtml
) {

    var module = {};

    module.refresh = true;

    /** Toggles automatic refreshes of instances of View. */
    module.toggleRefresh = function () {
        this.refresh = !this.refresh;
        return this.refresh;
    };

    // TODO this customising of the View prototype could be expanded to include
    // other methods from viewutils. see discussion at
    // https://github.com/brooklyncentral/brooklyn/pull/939

    // add close method to all views for clean-up
    // (NB we have to update the prototype _here_ before any views are instantiated;
    //  see "close" called below in "showView")
    Backbone.View.prototype.close = function () {
        // call user defined close method if exists
        this.viewIsClosed = true;
        if (_.isFunction(this.beforeClose)) {
            this.beforeClose();
        }
        _.each(this._periodicFunctions, function (i) {
            clearInterval(i);
        });
        this.remove();
        this.unbind();
    };

    Backbone.View.prototype.viewIsClosed = false;

    /**
     * Registers a callback (cf setInterval) that is unregistered cleanly when the view
     * closes. The callback is run in the context of the owning view, so callbacks can
     * refer to 'this' safely.
     */
    Backbone.View.prototype.callPeriodically = function (uid, callback, interval) {
        if (!this._periodicFunctions) {
            this._periodicFunctions = {};
        }
        var old = this._periodicFunctions[uid];
        if (old) clearInterval(old);

        // Wrap callback in function that checks whether updates are enabled
        var periodic = function () {
            console.log(module.refresh);
            if (module.refresh) {
                callback.apply(this);
            }
        };
        // Bind this to the view
        periodic = _.bind(periodic, this);
        this._periodicFunctions[uid] = setInterval(periodic, interval);
    };

    /**
     * A form that listens to modifications to its inputs, maintaining a model that is
     * submitted when a button with class 'submit' is clicked.
     */
    module.Form = Backbone.View.extend({
        events: {
            "change": "onChange",
            "submit": "onSubmit"
        },

        initialize: function() {
            if (!this.options.template) {
                throw new Error("template required by GenericForm");
            } else if (!this.options.onSubmit) {
                throw new Error("onSubmit function required by GenericForm");
            }
            this.onSubmitCallback = this.options.onSubmit;
            this.template = _.template(this.options.template);
            this.model = new (this.options.model || Backbone.Model);
            _.bindAll(this, "onSubmit", "onChange");
            this.render();
        },

        render: function() {
            this.$el.html(this.template());
            // Initialise the model with existing values
            Util.bindModelFromForm(this.model, this.$el);
            return this;
        },

        onChange: function(e) {
            var target = $(e.target);
            var name = target.attr("name");
            this.model.set(name, Util.inputValue(target), { silent: true });
        },

        onSubmit: function(e) {
            e.preventDefault();
            // TODO: Could validate model
            this.onSubmitCallback(this.model);
            return false;
        }

    });

    /**
     * A view to render another view in a modal. Give another view to render as
     * the `body' parameter that has an onSubmit function that will be called
     * when the modal's `Save' button is clicked.
     */
    module.Modal = Backbone.View.extend({

        id: _.uniqueId("modal"),
        className: "modal",
        template: _.template(ModalHtml),

        events: {
            "hide": "close",
            "click .modal-submit": "onSubmit"
        },

        initialize: function() {
            if (!this.options.body) {
                throw new Error("Modal view requires body to render");
            }
            _.bindAll(this);
            if (this.options.autoOpen) {
                this.show();
            }
        },

        beforeClose: function() {
            if (this.options.body) {
                this.options.body.close();
            }
        },

        render: function() {
            var optionalTitle = this.options.body.title;
            var title = _.isFunction(optionalTitle)
                    ? optionalTitle()
                    : _.isString(optionalTitle)
                        ? optionalTitle : this.options.title;
            this.$el.html(this.template({
                title: title
            }));
            this.options.body.render();
            this.$(".modal-body").html(this.options.body.$el);
            return this;
        },

        show: function() {
            this.render().$el.modal();
            return this;
        },

        onSubmit: function(event) {
            if (_.isFunction(this.options.body.onSubmit)) {
                var submission = this.options.body.onSubmit.apply(this.options.body, [event]);
                var self = this;
                submission.done(function() {
                    // Closes view via event.
                    self.$el.modal("hide");
                }).fail(function() {
                    // ?
                });
            }
            return false;
        }
    });

    /** Creates, displays and returns a modal with the given view used as its body */
    module.showModalWith = function (bodyView) {
        return new module.Modal({body: bodyView}).show();
    };

    /**
     * Presents inputs for config key names/values with  buttons to add/remove entries
     * and a function to extract a map of name->value.
     */
    module.ConfigKeyInputPairList = Backbone.View.extend({
        template: _.template(ConfigKeyInputHtml),
        // Could listen to input change events and add 'error' class to any type inputs
        // that duplicate values.
        events: {
            "click .config-key-row-remove": "rowRemove",
            "keypress .last": "rowAdd"
        },
        render: function () {
            if (this.options.configKeys) {
                var templated = _.map(this.options.configKeys, function (value, key) {
                    return this.templateRow(key, value);
                }, this);
                this.$el.html(templated.join(""));
            }
            this.$el.append(this.templateRow());
            this.markLast();
            return this;
        },
        rowAdd: function (event) {
            this.$el.append(this.templateRow());
            this.markLast();
        },
        rowRemove: function (event) {
            $(event.currentTarget).parent().remove();
            if (this.$el.children().length == 0) {
                this.rowAdd();
            }
            this.markLast();
        },
        markLast: function () {
            this.$(".last").removeClass("last");
            // Marking inputs rather than parent div to avoid weird behaviour when
            // remove row button is triggered with the keyboard.
            this.$(".config-key-type").last().addClass("last");
            this.$(".config-key-value").last().addClass("last");
        },
        templateRow: function (type, value) {
            return this.template({type: type || "", value: value || ""});
        },
        getConfigKeys: function () {
            var cks = {};
            this.$(".config-key-type").each(function (index, input) {
                input = $(input);
                var type = input.val() && input.val().trim();
                var value = input.next().val() && input.next().val().trim();
                if (type && value) {
                    cks[type] = value;
                }
            });
            return cks;
        }
    });

    return module;

});
