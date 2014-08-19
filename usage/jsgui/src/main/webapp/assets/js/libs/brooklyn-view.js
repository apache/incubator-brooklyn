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

    /**
     * A form that listens to modifications to its inputs, maintaining a model that is
     * submitted when a button with class 'submit' is clicked.
     */
    var GenericForm = Backbone.View.extend({
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

    return {
        Form: GenericForm
    };

});
