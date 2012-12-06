define([
        "underscore", "jquery", "backbone"
        ], function (_, $, Backbone) {

    var ViewUtils = {

        myDataTable:function($table) {
            $table.dataTable({
                "iDisplayLength": 25,
                "sPaginationType": "full_numbers",
                "sDom":"f<'brook-db-top-toolbar'>t<'brook-db-bot-toolbar'>ilp",
                "oLanguage":{
                    "sSearch": "",
                    "sInfo": "_START_ - _END_ of _TOTAL_ ",
                    "sInfoEmpty": "<i>No data</i> ",
                    "sEmptyTable": "<i>No matching records available</i>",
                    "sZeroRecords": "<i>No matching records found</i>",
                    "oPaginate": {
                        "sFirst": "&lt;&lt;",
                        "sPrevious": "&lt;",
                        "sNext": "&gt;",
                        "sLast": "&gt;&gt;"
                    },
                    "sInfoFiltered": "(of _MAX_)",
                    "sLengthMenu":'( <select>' +
                    '<option value="25">25</option>' +
                    '<option value="50">50</option>' +
                    '<option value="-1">all</option>' +
                    '</select> / page )'
                }
            })
            $('.brook-db-bot-toolbar', $table.parent().parent()).html(
                    '<i class="refresh icon-refresh handy" rel="tooltip" title="Reload immediately."></i>'+'&nbsp;&nbsp;'+
                    '<i class="filterEmpty icon-eye-open handy" rel="tooltip" title="Show/hide empty records"></i>'
            );
        },
        toggleFilterEmpty: function($table, column) {
            var hideEmpties = $('.filterEmpty', $table.parent().parent()).
                toggleClass('icon-eye-open icon-eye-close').hasClass('icon-eye-close')
            if (hideEmpties) $table.dataTable().fnFilter('.+', column, true);
            else $table.dataTable().fnFilter('.*', column, true);
        },

        attachToggler: function($scope) {
            var $togglers;
            if ($scope === undefined) $togglers = $(".toggler-header")
            else $togglers = $(".toggler-header", $scope);
            $togglers.click(this.onTogglerClick)
        },
        onTogglerClick: function(event) {
            var root = $(event.currentTarget).closest(".toggler-header");
            root.toggleClass("user-hidden");
            $(".toggler-icon", root).
                toggleClass("icon-chevron-left").
                toggleClass("icon-chevron-down");
                
            var next = root.next();
            if (root.hasClass("user-hidden")) 
                next.hide('fast');
            else 
                next.show('fast')
        },
        
        updateTextareaWithData: function($div, data, alwaysShow, minPx, maxPx) {
            var $ta = $("textarea", $div)
            var show = alwaysShow
            if (data !== undefined) {
                $ta.val(data)
                show = true
            } else {
                $ta.val("")
            }
            if (show) {
                $div.show(100)
                $ta.css("height", minPx);
                // scrollHeight prop works sometimes (e.g. groovy page) but not others (e.g. summary)
                var height = $ta.prop("scrollHeight")
                height = Math.min(height, maxPx)
                height = Math.max(height, minPx)
                $ta.css("height", height);
            } else {
                $div.hide()
            }
        }

    }
    
    return ViewUtils
})