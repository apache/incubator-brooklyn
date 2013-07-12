/**
 * Displays the list of activities/tasks the entity performed.
 */
define([
    "underscore", "jquery", "backbone", "brooklyn-utils", "view/viewutils",
    "text!tpl/apps/activities.html", "text!tpl/apps/activity-row-details.html", "text!tpl/apps/activity-full-details.html", 
    "bootstrap", "formatJson", "jquery-datatables", "datatables-extensions"
], function (_, $, Backbone, Util, ViewUtils, ActivitiesHtml, ActivityRowDetailsHtml, ActivityFullDetailsHtml) {

    var ActivitiesView = Backbone.View.extend({
        template:_.template(ActivitiesHtml),
        table:null,
        refreshActive:true,
        selectedId:null,
        selectedRow:null,
        events:{
            "click #activities-table tr":"rowClick",
            'click .refresh':'refreshNow',
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
                                         return Util.prep(data)
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
                if (that.refreshActive)
                    that.collection.fetch({reset: true});
            }, 3000);
            that.collection.fetch({reset: true});
        },
        refreshNow: function() {
            this.collection.fetch({reset: true});
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
                // nothing to do
            } else {
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
                this.showDetailRow(true);
            }
            return this;
        },
        rowClick:function(evt) {
            
//            $(this).toggleClass('icon-chevron-down icon-chevron-right')
//            var open = $(this).hasClass('icon-chevron-down')
            
            var that = this;
            var row = $(evt.currentTarget).closest("tr");
            var id = row.attr("id");
            
            if (id==null)
                // is the details row, ignore click here
                return;
            
            $("#activities-table tr").removeClass("selected");
            
            if (this.selectedRow!=null) {
                var r = this.selectedRow;
                // slide it up, then close once it is hidden (else it vanishes suddenly)
                // the slide effect isn't just cool, it helps keep rows in a visually consistent place
                // (so that it doesn't just jump when you click, if a row above it was previously selected)
                $('tr[#'+id+'] .activity-row-details').slideUp(50, "swing", function() { 
                    that.table.fnClose(r);
                })
            }
            
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
                this.showDetailRow(false);
            }
        },
        
        showDetailRow: function(updateOnly) {
            var id = this.selectedId;
            if (id==null) return;
            var task = this.collection.get(id);
            if (task==null) return;
            var html = _.template(ActivityRowDetailsHtml, { task: task==null ? null : task.attributes, updateOnly: updateOnly })
            $('tr#'+id).next().find('.row-expansion').html(html)
            $('tr#'+id).next().find('.row-expansion .activity-row-details').slideDown(50);
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
        }
    });

    return ActivitiesView;
});