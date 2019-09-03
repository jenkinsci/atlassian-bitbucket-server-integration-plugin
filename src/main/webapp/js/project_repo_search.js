// function refillOnChange(e,onChange) {
//     var deps = [];
//
//     function h() {
//         var params = {};
//         deps.each(function (d) {
//             params[d.name] = controlValue(d.control);
//         });
//         onChange(params);
//     }
//     var v = e.getAttribute("fillDependsOn");
//     if (v!=null) {
//         v.split(" ").each(function (name) {
//             var c = findNearBy(e,name);
//             if (c==null) {
//                 if (window.console!=null)  console.warn("Unable to find nearby "+name);
//                 if (window.YUI!=null)      YUI.log("Unable to find a nearby control of the name "+name,"warn")
//                 return;
//             }
//             $(c).observe("change",h);
//             deps.push({name:Path.tail(name),control:c});
//         });
//     }
//     h();   // initial fill
// }

Behaviour.specify(".searchable", 'searchableField', 200, function (el) {
    var items = {};

    new ComboBox(el, function (value) {
        return Object.keys(items);
    }, {});

    el.addEventListener('input', function(e) {
        var parameters = {};
        var dependentFields = el.getAttribute("fillDependsOn");
        if (dependentFields) {
            dependentFields.split(" ").each(function(fieldName) {
                var dependentField = findNearBy(el, fieldName);
                if (dependentField) {
                    parameters[fieldName] = dependentField.value;
                }
            })
        }
        new Ajax.Request(el.getAttribute("fillUrl"), {
            parameters: parameters,
            onSuccess: function (rsp) {
                items = rsp.responseJSON.data || {};
            }
        });
    });

    el.addEventListener('change', function(e) {
        var resultField = document.getElementById(el.getAttribute('resultField'));
        resultField.value = items[e.target.value] || e.target.value;
        resultField.dispatchEvent(new Event('change')); // trigger validation
    })
});
