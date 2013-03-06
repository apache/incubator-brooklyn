/**
 * Displays the list of activities/tasks the entity performed.
 */
define([
    "underscore", "jquery", "backbone", "view/viewutils",
    "text!tpl/apps/activities.html", "text!tpl/apps/activity-row-details.html", "text!tpl/apps/activity-full-details.html", 
    "bootstrap", "formatJson", "jquery-datatables", "datatables-extensions", "brooklyn-utils"
], function (_, $, Backbone, ViewUtils, ActivitiesHtml, ActivityRowDetailsHtml, ActivityFullDetailsHtml) {

    var ActivitiesView = Backbone.View.extend({
        template:_.template(ActivitiesHtml),
        table:null,
        refreshActive:true,
        selectedId:null,
        selectedRow:null,
        events:{
            "click #activities-table tr":"rowClick",
            'click .toggleAutoRefresh':'toggleAutoRefresh',
            'click .toggleFullDetail':'toggleFullDetail'
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
                    $(nRow).addClass('activity-row')
                },
                "aoColumnDefs": [
                                 {
                                     "mRender": function ( data, type, row ) {
                                         return prep(data)
                                     },
                                     "aTargets": [ 1, 2, 3 ]
                                 },
                                 { "bVisible": false,  "aTargets": [ 0 ] }
                                 // or could use a chevron
//                                 {
//                                     "mRender": function ( data, type, row ) {
//                                         return '<i class="activity-expander icon-chevron-right handy"></i>'
//                                     },
//                                     "bSortable": false,
//                                     "sWidth": "20px",
//                                     "aTargets": [ 0 ]
//                                 }
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
            $('.expanded-node', that.table).remove()
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
            
//            $(this).toggleClass('icon-chevron-down icon-chevron-right')
//            var open = $(this).hasClass('icon-chevron-down')
            
            var row = $(evt.currentTarget).closest("tr");
            var id = row.attr("id");
            
            if (id==null)
                // is the details row, ignore click here
                return;
            
            $("#activities-table tr").removeClass("selected");
            
            if (this.selectedRow!=null)
                this.table.fnClose(this.selectedRow);
            
            if (this.selectedId == id) {
                // deselected
                this.selectedRow = null;
                this.selectedId = null;
                this.showFullActivity(null);
                
            } else {
                row.addClass("selected");
                this.selectedRow = row[0];
                this.selectedId = id;
                this.table.fnOpen(row[0], '', 'row-expansion'); 
                this.showDetailRow();
            }
        },
        
        showDetailRow: function() {
            var task = this.collection.get(this.selectedId);
            var html = _.template(ActivityRowDetailsHtml, { task: task==null ? null : task.attributes })
            $('.row-expansion').html(html) 
        },
        toggleFullDetail: function() {
            var i = $('.toggleFullDetail');
            i.toggleClass('active')
            if (i.hasClass('active'))
                this.showFullActivity()
            else
                this.hideFullActivity()
        },
        showFullActivity: function() {
            var id = this.selectedId
            $("#activity-details-none-selected").slideUp(50);
            var task = this.collection.get(id);
            if (task==null) {
                this.hideFullActivity();
                return;
            }
            var html = _.template(ActivityFullDetailsHtml, { task: task });
            $("#activity-details").html(html);
            $("#activity-details").slideDown(100);
        },
        hideFullActivity: function() {
            $("#activity-details").slideUp(100);
            $("#activity-details-none-selected").slideDown(50);
        }
    });

//    ActivitiesView.Details = Backbone.View.extend({
//        tagName:"div",
//        className:"modal hide",
//        template:_.template(ActivityFullDetailsHtml),
//        render:function () {
//            this.$el.html(this.template({
//                displayName:this.model.get("displayName"),
//                description:FormatJSON(this.model.toJSON())
//            }));
//            return this;
//        }
//    });
    return ActivitiesView;
});