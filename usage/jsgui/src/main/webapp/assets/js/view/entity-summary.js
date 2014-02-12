/**
 * Render the application/entity summary tab.
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "view/viewutils",
    "text!tpl/apps/summary.html", "formatJson"
], function (_, $, Backbone, ViewUtils, SummaryHtml, FormatJSON) {

    var EntitySummaryView = Backbone.View.extend({
        events:{
            'click a.open-tab':'tabSelected'
        },
        template:_.template(SummaryHtml),
        initialize: function() {
            _.bindAll(this)
            var that = this
            var ej = FormatJSON(this.model.toJSON());
            this.$el.html(this.template({
                entity:this.model,
                application:this.options.application,
                entityJson:ej,
                applicationJson:FormatJSON(this.options.application.toJSON())
            }))
            ViewUtils.updateTextareaWithData($(".for-textarea", this.$el), ej, true, false, 150, 400)
            ViewUtils.attachToggler(this.$el)

            // TODO we should have a backbone object exported from the sensors view which we can listen to here
            // (currently we just take the URL from that view) - and do the same for active tasks;
            ViewUtils.getRepeatedlyWithDelay(this, this.model.getSensorUpdateUrl(),
                function(data) { that.updateWithData(data) });
            // however if we only use external objects we must either subscribe to their errors also
            // or do our own polling against the server, so we know when to disable ourselves
//            ViewUtils.fetchRepeatedlyWithDelay(this, this.model, { period: 10*1000 })
        },
        render:function () {
            return this
        },
        revealIfHasValue: function(sensor, $div, renderer, values) {
            var that = this;
            if (!renderer) renderer = function(data) { return _.escape(data); }
            
            if (values) {
                var data = values[sensor]
                if (data || data===false) {
                    $(".value", $div).html(renderer(data))
                    $div.show()
                } else {
                    $div.hide();
                }
            } else {
              // direct ajax call not used anymore - but left just in case
              $.ajax({
                url: that.model.getLinkByName("sensors")+"/"+sensor,
                contentType:"application/json",
                success:function (data) {
                    if (data || data===false) {
                        $(".value", $div).html(renderer(data))
                        $div.show()
                    } else {
                        $div.hide();
                    }
                    that.updateStatusIcon();
                }})
            }
        },
        updateWithData: function (data) {
            this.revealIfHasValue("service.state", this.$(".status"), null, data)
            this.revealIfHasValue("service.isUp", this.$(".serviceUp"), null, data)
            
            var renderAsLink = function(data) { return "<a href='"+_.escape(data)+"'>"+_.escape(data)+"</a>" };
            this.revealIfHasValue("webapp.url", this.$(".url"), renderAsLink, data)

            var status = this.updateStatusIcon();
            
            if (status.problem) {
                this.updateAddlInfoForProblem();
            } else {
                this.$(".additional-info-on-problem").html("").hide()
            }
        },
        updateSensorsNow: function() {
            this.updateWithData();
        },
        updateStatusIcon: function() {
            var statusIconInfo = ViewUtils.computeStatusIconInfo(this.$(".serviceUp .value").html(), this.$(".status .value").html());
            if (statusIconInfo.url) {
                this.$('#status-icon').html('<img src="'+statusIconInfo.url+'" '+
                        'style="max-width: 64px; max-height: 64px;"/>');
            } else {
                this.$('#status-icon').html('');
            }
            return statusIconInfo;
        },
        updateAddlInfoForProblem: function() {
            if (!this.options.tasks)
                // if tasks not supplied, then don't attempt to show status info!
                return;
            
            var problemDetails = "";
            var lastFailedTask = null, that = this;
            // ideally get the time the status changed, and return the last failure on or around that time
            // (or take it from some causal log)
            // but for now, we just return the most recent failed task
            this.options.tasks.each(function(it) {
                if (it.isError() && it.isLocalTopLevel()) {
                    if (!lastFailedTask || it.attributes.endTimeUtc < lastFailedTask.attributes.endTimeUtc)
                        lastFailedTask = it;
                }
            } );

            if (lastFailedTask) {
                var path = "activities/subtask/"+lastFailedTask.id;
                var base = this.model.getLinkByName("self");
                problemDetails = "<b>"+_.escape("Failure running task ")
                    +"<a class='open-tab' tab-target='"+path+"'" +
                    		"href='#"+base+"/"+path+"'>" +
            				"<i>"+_.escape(lastFailedTask.attributes.displayName)+"</i> "
                    +"("+lastFailedTask.id+")</a>: </b>"+
                    _.escape(lastFailedTask.attributes.result);
            } else {
                // trigger callback to get tasks
                problemDetails = "<i>Loading problem details...</i>";
                ViewUtils.get(this, this.options.tasks.url, function() {
                    that.updateAddlInfoForProblem();
                });
            }      
            if (problemDetails) {
                this.$(".additional-info-on-problem").html(problemDetails).show();
            } else {
                var base = this.model.getLinkByName("self");
                this.$(".additional-info-on-problem").html(
                        "The entity appears to have failed externally. " +
                        "<br style='line-height: 24px;'>" +
                        "No Brooklyn-managed task failures reported. " +
                        "For more information, investigate " +
                            "<a class='open-tab' tab-target='sensors' href='#"+base+"/sensors'>sensors</a> and " +
                            "streams on recent " +
                            "<a class='open-tab' tab-target='activities' href='#"+base+"/activities'>activity</a>, " +
                            "as well as external systems and logs where necessary.").show();
            }
        },
        tabSelected: function(event) {
            if (event.metaKey || event.shiftKey)
                // trying to open in a new tab, do not act on it here!
                return;
            var tab = $(event.currentTarget).attr('tab-target');
            this.options.tabView.openTab(this.model.id, tab);
            // and prevent the a from firing
            event.preventDefault();
            return false;
        }
    });

    return EntitySummaryView;
});
