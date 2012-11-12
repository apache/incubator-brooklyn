define([
        "underscore", "jquery", "backbone"
        ], function (_, $, Backbone) {

    var ViewUtils = Backbone.View.extend({

        myDataTable:function($table) {
            $table.dataTable({
                "iDisplayLength": 25,
                "sPaginationType": "full_numbers",
                "sDom":"f<'brook-db-top-toolbar'>t<'brook-db-bot-toolbar'>ilp",
                "oLanguage":{
                    "sSearch": "",
                    "sInfo": "_START_ - _END_ of _TOTAL_ ",
                    "sInfoEmpty": "<i>No data</i> ",
                    "sEmptyTable": "<i>No matching records currently available</i>",
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
        }

    })
    return ViewUtils
})