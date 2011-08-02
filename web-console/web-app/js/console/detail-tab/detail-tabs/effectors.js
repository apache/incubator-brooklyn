Brooklyn.effectors = (function() {
    // Config
    var id = '#effector-data';
    var aoColumns = [ { "mDataProp": "name", "sTitle": "name", "sWidth":"100%", "bSortable": false }];

    // State
    var selectedRowData;

    function updateEffectorsList(json) {
        Brooklyn.tabs.getDataTable(id, ".", aoColumns, updateParameters, json);

        $('#effector-args').empty();
        var noSelectedEffector = document.createElement("p");
        noSelectedEffector.textContent = "Please select an effector to invoke";
        $('#effector-args').append(noSelectedEffector);

        $('#effectors-invoke-button').attr("disabled", "disabled");
        $(Brooklyn.eventBus).trigger('update_ok');
    }

    function updateParameters(event) {
        var settings = Brooklyn.tabs.getDataTable(id).fnSettings().aoData;
        for(row in settings) {
       		$(settings[row].nTr).removeClass('row_selected');
   		}
 		$(event.target.parentNode).addClass('row_selected');

        selectedRowData = Brooklyn.tabs.getDataTableSelectedRowData(id, event);

        $('#effectors-invoke-button').removeAttr("disabled")

        $('#effector-args').empty();

        var title = document.createElement("p");
        title.textContent = selectedRowData.description;
        $('#effector-args').append(title);

        if(selectedRowData.parameters.length === 0 ) {
            var argumentLabel = document.createElement('p');
            argumentLabel.textContent = "No arguments needed";
            $('#effector-args').append(argumentLabel);
        } else {
            for (parameter in selectedRowData.parameters){
                var textBox = document.createElement("input");
                var argumentLabel = document.createElement('label');
                var div = document.createElement('div');
                var parameterName = selectedRowData.parameters[parameter].name

                argumentLabel.setAttribute("name", parameterName + "Label");
                argumentLabel.setAttribute("for", parameterName + "Input");
                argumentLabel.textContent = parameterName + ":";
                div.appendChild(argumentLabel);

                textBox.setAttribute("name", parameterName + "Input");
                div.appendChild(textBox);

                $('#effector-args').append(div);
            }
        }
    }

    function updateList(e, entity_id) {
        if (entity_id) {
             $.getJSON("effectors?id=" + entity_id, updateEffectorsList).error(
                function() {$(Brooklyn.eventBus).trigger('update_failed', "Could not get effector data.");}
            );
        }
    }

    function invokeEffector(event){
        alert('Invoking effectors is currently unsupported');
        return false;
    }

    function init() {
        $('#effector-invoke').click(invokeEffector);
        $(Brooklyn.eventBus).bind("entity_selected", updateList);
    }

    return {
        init: init
    };

})();

$(document).ready(Brooklyn.effectors.init);
