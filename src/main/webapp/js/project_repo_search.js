Behaviour.specify(".searchable", 'searchableField', 200, function (el) {
    var results = {};

    new ComboBox(el, function (query) {
        return Object.keys(results);
    }, {});

    el.addEventListener('input', function(e) {
        var valueIdentifier = el.getAttribute("valueIdentifier");
        var parameters = {};
        (el.getAttribute("fillDependsOn") || "").split(" ")
            .each(function(fieldName) {
                var dependentField = findNearBy(el, fieldName);
                if (dependentField) {
                    parameters[fieldName] = dependentField.value;
                }
            });
        new Ajax.Request(el.getAttribute("fillUrl"), {
            parameters: parameters,
            onSuccess: function (rsp) {
                results = (rsp.responseJSON.data && rsp.responseJSON.data.values || {})
                    .reduce(function (flattened, result) {
                        // This might be key or slug depending on what we're searching for
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
