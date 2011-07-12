Brooklyn.effectors = (function() {

    // Config
    var id = '#effector-data';
    var aoColumns = [ { "mDataProp": "name", "sTitle": "name", "sWidth":"100%"  }];

    // State
    var selectedRowData;

    function _updateEffectorsList(json) {
        var _jsonObject = json;
        var table = Brooklyn.tabs.getDataTable(id, ".", aoColumns);
        table.fnClearTable(false);
        table.fnAddData(_jsonObject);

        $('#effector-args').empty();
        var noSelectedEffector = document.createElement("p");
        noSelectedEffector.textContent = "Please select an effector to invoke";
        $('#effector-args').append(noSelectedEffector);

        $('#effectors-invoke-button').attr("disabled", "disabled");

        $(id + " tbody").click(updateParameters);
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

        if(selectedRowData.parameters.length == 0 ) {
            var argumentLabel = document.createElement('p');
            argumentLabel.textContent = "No arguments needed:";
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
        if (typeof entity_id === 'undefined') {
            return;
        }
        // TODO: Handle failure
        $.getJSON("effectors?id=" + entity_id, _updateEffectorsList);
    }

    function invokeEffector(event){
        //TODO: use form object correctly
        alert('Effector: "' + selectedRowData.name + '" invoked');
        return false;
    }

    function init() {
        $('#effector-invoke').submit(invokeEffector);
        $(Brooklyn.eventBus).bind("entity_selected", updateList);
    }

    return {
        init: init,
    };

})();

$(document).ready(Brooklyn.effectors.init);
