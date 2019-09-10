/**
 * Modified from the Jenkins combobox code
 * https://github.com/jenkinsci/jenkins/blob/master/core/src/main/resources/lib/form/combobox/combobox.js
 *
 * The Jenkins combobox only fills items 'onChange' of dependent fields. This means that if a combobox is dependent on
 * itself (e.g a search field) then it is filled too late. This will fill the combobox options on 'input' and then
 * when one is selected the human readable value will be in the combobox input and a computer-readable value will go
 * in the associated value field.
 **/
Behaviour.specify(".searchable", 'searchableField', 200, function(el) {
    var results = {};

    new ComboBox(el, function(query) {
        return Object.keys(results);
    }, {});

    el.addEventListener('input', function(e) {
        var parameters = (el.getAttribute("fillDependsOn") || "").split(" ")
            .reduce(function(params, fieldName) {
                var dependentField = findNearBy(el, fieldName);
                if (dependentField) {
                    params[fieldName] = dependentField.value;
                }
                return params;
            }, {});
        new Ajax.Request(el.getAttribute("fillUrl"), {
            parameters: parameters,
            onSuccess: function(rsp) {
                var valueIdentifier = el.getAttribute("valueIdentifier");
                results = (rsp.responseJSON.data && rsp.responseJSON.data.values || [])
                    .reduce(function (flattened, result) {
                        flattened[result.name] = result[valueIdentifier];
                        return flattened;
                    }, {});
            }
        });
    });

    el.addEventListener('change', function(e) {
        var valueField = document.getElementById(el.getAttribute('valueField'));
        valueField.value = results[e.target.value] || e.target.value; // default to the field value if not found
        valueField.dispatchEvent(new Event('change')); // trigger validation
    });
});
