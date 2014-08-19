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
    "jquery", "underscore", "backbone", "brooklyn-utils"
], function (
    $, _, Backbone, Util
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

    return module;

});
