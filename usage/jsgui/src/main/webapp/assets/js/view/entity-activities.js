/**
 * Displays the list of activities/tasks the entity performed.
 */
define([
    "underscore", "jquery", "backbone", "view/viewutils",
    "text!tpl/apps/activities.html", "text!tpl/apps/activity-details.html", 
    "bootstrap", "formatJson", "jquery-datatables", "datatables-extensions", "brooklyn-utils"
], function (_, $, Backbone, ViewUtils, ActivitiesHtml, ActivityDetailsHtml) {

    var ActivitiesView = Backbone.View.extend({
        template:_.template(ActivitiesHtml),
        table:null,
        refreshActive:true,
        events:{
            "click #activities-table tr":"rowClick",
            'click .toggleAutoRefresh':'toggleAutoRefresh'
        },
        initialize:function () {
            this.$el.html(this.template({ }));
            $.ajaxSetup({ async:false });
            var that = this,
                $table = that.$('#activities-table');
            that.collection.url = that.model.getLinkByName("activities");
            that.table = ViewUtils.myDataTable($table, {
                "fnRowCallback": function( nRow, aData, iDisplayIndex, iDisplayIndexFull ) {
                    $(nRow).attr('id', aData[0])
                },
                "aoColumnDefs": [
                                 {
                                     "mRender": function ( data, type, row ) {
                                         return prep(data)
                                     },
                                     "aTargets": [ 0, 1, 2, 3 ]
                                 },
                                 { "bVisible": false,  "aTargets": [ 0 ] }
                             ]            
            });
            // TODO domain-specific filters
            ViewUtils.addAutoRefreshButton(that.table);
            ViewUtils.addRefreshButton(that.table);
            
            that.collection.on("reset", that.render, that);
            that.callPeriodically("entity-activities", function () {
                if (self.refreshActive)
                    that.collection.fetch();
            }, 3000);
            that.collection.fetch();
        },
        beforeClose:function () {
            this.collection.off("reset", this.render);
        },
        toggleAutoRefresh:function () {
            ViewUtils.toggleAutoRefresh(this);
        },
        enableAutoRefresh: function(isEnabled) {
            this.refreshActive = isEnabled
        },
        render:function () {
            var that = this;
            if (that.table == null || this.collection.length==0) {
                $(".has-no-activities").show();
                $("#activity-details-none-selected").hide();
            } else {
                $(".has-no-activities").hide();
                ViewUtils.updateMyDataTable(that.table, that.collection, function(task, index) {
                    return [ 
                                  // columns are: id, name, when submitted, status         
                                  task.get("id"),
                                  task.get("displayName"),
                                  task.get("submitTimeUtc"),
                                  task.get("currentStatus")
                              ];
                    //also have:
//                  startTimeUtc:task.get("startTimeUtc"),
//                  endTimeUtc:task.get("endTimeUtc"),
//                  entityDisplayName:task.get("entityDisplayName")
                })
            }
            return this;
        },
        rowClick:function(evt) {
            // TODO link row click, and details stuff
            var row = $(evt.currentTarget).closest("tr");
            var id = row.attr("id");
            $("#activities-table tr").removeClass("selected");
            if (this.activeTask == id) {
                // deselected
                this.showFullActivity(null);
            } else {
                row.addClass("selected");
                this.activeTask = id;
                this.showFullActivity(id);
            }
        },
        showFullActivity:function (id) {
            $("#activity-details-none-selected").hide(100);
            var task = this.collection.get(id);
            if (task==null) {
                this.activeTask = null;
                $("#activity-details").hide(100);
                $("#activity-details-none-selected").show(100);
                return;
            }
            var $ta = this.$("#activity-details textarea");
            if ($ta.length) {
                $ta.val(FormatJSON(task.toJSON()));
            } else {
                var html = _.template(ActivityDetailsHtml, {
                    displayName:this.model.get("displayName"),
                    description:FormatJSON(task.toJSON())
                });
                $("#activity-details").html(html);
            }
            $("#activity-details").show(100);
        }
    });

    ActivitiesView.Details = Backbone.View.extend({
        tagName:"div",
        className:"modal hide",
        template:_.template(ActivityDetailsHtml),
        render:function () {
            this.$el.html(this.template({
                displayName:this.model.get("displayName"),
                description:FormatJSON(this.model.toJSON())
            }));
            return this;
        }
    });
    return ActivitiesView;
});