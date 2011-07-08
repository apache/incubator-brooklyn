Brooklyn.effectors = (function() {

    var _jsonObject
    var _selectedEffector

    function _updateEffectorsList(json) {
        _jsonObject = json
        $('#effectorList').find('option').remove().end()

        for (var i=0; i<_jsonObject.length; i++) {
            option = document.createElement("option");
            option.text = _jsonObject[i].name;
            option.value = _jsonObject[i].name;
            $('#effectorList').get(0)[$('#effectorList option').length] = option;
        }

        $('#effectorList').change(_updateParameters);
        _updateParameters();
    }

    function _getEffector(effectorName){
        for (var i=0; i<_jsonObject.length; i++) {
            if (_jsonObject[i].name == effectorName) {
                return _jsonObject[i];
            }
        }
    }

    function _updateParameters(){
        //TODO update parameter panel
        if ($('#effectorList option:selected').length == 0) {
            var noSelectedEffector = document.createElement("label");
            noSelectedEffector.textContent = "Please select an effector to invoke";
            $('#effectorArgs').html(noSelectedEffector);
            $('#invokeButton').attr("disabled", "disabled");
        } else {
            $('#effectorArgs').find('label').remove().end();
            $('#effectorArgs').find('input').remove().end();
            _selectedEffector = _getEffector($('#effectorList option:selected')[0].text);
            _addElementsToPanel();
        }
    }

    function _addElementsToPanel(){
        $('#invokeButton').removeAttr("disabled")
        var arguments = _selectedEffector["parameters"];
        var form = document.createElement('form');

        if(!arguments.length > 0 ){
            var argumentLabel = document.createElement('label');
            argumentLabel.textContent = "No arguments needed";
            form.appendChild(argumentLabel);
        } else {
            for (parameter in arguments){
                var textBox = document.createElement("input");
                var argumentLabel = document.createElement('label');
                var div = document.createElement('div');
                var parameterName = arguments[parameter].name

                argumentLabel.setAttribute("name", parameterName + "Label");
                argumentLabel.setAttribute("for", parameterName + "Input");
                argumentLabel.textContent = parameterName + ":";
                div.appendChild(argumentLabel);

                textBox.setAttribute("name", parameterName + "Input");
                div.appendChild(textBox);

                form.appendChild(div);
            }
        }

        var invokeButton = document.createElement("input");
        invokeButton.setAttribute("type", "button");
        invokeButton.setAttribute("onclick", "Brooklyn.effectors.invokeEffector(this.form)");
        invokeButton.setAttribute("value", "Invoke");
        form.appendChild(invokeButton);
        $('#effectorArgs').append(form);
    }

    function _updateList(e, entity_id) {
        if (typeof entity_id === 'undefined') {
            return;
        }
        // TODO: Handle failure
        $.getJSON("effectors?id=" + entity_id, _updateEffectorsList);
    }

    function invokeEffector(form){
        //TODO: use form object correctly
        alert("Effector: " + _selectedEffector.name + " invoked");
    }

    function init() {
        $(Brooklyn.eventBus).bind("entity_selected", _updateList);
    }

    return {
        init: init,
        invokeEffector: invokeEffector
    };

})();

$(document).ready(Brooklyn.effectors.init);
