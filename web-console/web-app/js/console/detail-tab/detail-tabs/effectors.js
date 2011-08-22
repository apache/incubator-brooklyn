Brooklyn.effectors = (function() {
    // Config
    var id = '#effector-data';
    var aoColumns = [ { "mDataProp": "name", "sTitle": "name", "sWidth":"100%", "bSortable": false }];

    // State
    var selectedRowData;
    var selectedEntityId;

    function updateEffectorsList(json) {
        Brooklyn.util.getDataTable(id, ".", aoColumns, updateParameters, json, false);

        $('#effector-args').empty();
        var noSelectedEffector = document.createElement("p");
        noSelectedEffector.textContent = "Please select an effector to invoke";
        $('#effector-args').append(noSelectedEffector);

        $('#effectors-invoke-button').attr("disabled", "disabled");
        $(Brooklyn.eventBus).trigger('update_ok');
    }

    function updateParameters(event) {
        var settings = Brooklyn.util.getDataTable(id).fnSettings().aoData;
        for(row in settings) {
       		$(settings[row].nTr).removeClass('row_selected');
   		}
 		$(event.target.parentNode).addClass('row_selected');

        selectedRowData = Brooklyn.util.getDataTableSelectedRowData(id, event);

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
                var parameterClassName = selectedRowData.parameters[parameter].parameterClassName
                var argumentLabel = document.createElement('label');
                var div = document.createElement('div');
                var parameterName = selectedRowData.parameters[parameter].name
                var isList = false;
                var textBox;

                argumentLabel.setAttribute("name", parameterName + "Label");
                argumentLabel.setAttribute("for", parameterName);
                argumentLabel.textContent = parameterName + ":";
                div.appendChild(argumentLabel);

                if (parameterClassName && /(Collection|List)$/.test(parameterClassName)) {
                    isList = true;
                }

                if (isList) {
                    textBox = document.createElement("textarea");
                } else {
                    textBox = document.createElement("input");
                }

                textBox.setAttribute("id", 'effector-args-' + parameterName);
                textBox.setAttribute("name", parameterName);
                div.appendChild(textBox);

                if (isList) {
                    var instructions = document.createElement('span');
                    instructions.textContent = 'one per line';
                    div.appendChild(instructions);
                }

                $('#effector-args').append(div);

                if (/Date$/.test(parameterClassName)) {
                    $('#effector-args-' + parameterName).datepicker();
                }
            }
        }
    }

    function addParameter(event) {

    }

    function updateList(e, entity_id) {
        if (entity_id) {
             $.getJSON("../entity/effectors?id=" + entity_id, updateEffectorsList).error(
                function() {$(Brooklyn.eventBus).trigger('update_failed', "Could not get effector data.");}
            );
            selectedEntityId = entity_id;
        }
    }

    function invokeEffector(event){
        var dataMap = new Object();
        dataMap["entityId"] = selectedEntityId;
        dataMap["effectorName"] = selectedRowData.name;

        if(selectedRowData.parameters.length != 0 ){
            for(parameter in selectedRowData.parameters){
                var parameterName = selectedRowData.parameters[parameter].name;
                var parameterValue = $('#effector-args-'+parameterName).val();
                dataMap[parameterName] = parameterValue;
            }
        }

        $.ajax({
            url: "../entity/invoke",
            data: dataMap,
            success: function(){
                alert('Effector: "' + selectedRowData.name + '" invoked');
            }
        });
    }

    function init() {
        $('#effectors-invoke-button').click(invokeEffector);
        $(Brooklyn.eventBus).bind("entity_selected", updateList);
    }

    return {
        init: init
    };

})();

$(document).ready(Brooklyn.effectors.init);
