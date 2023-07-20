/**
 * Call the function if it hasn't been called recently
 * @param func the function to call
 * @param wait the timeout period in millis
 * @returns {Function}
 */
var debounce = function (func, wait) {
    var timeout;
    return function () {
        var context = this, args = arguments;
        var later = function () {
            timeout = null;
            func.apply(context, args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
};

/**
 * Modified from the Jenkins combobox code
 * https://github.com/jenkinsci/jenkins/blob/master/core/src/main/resources/lib/form/combobox/combobox.js
 *
 * The Jenkins combobox only fills items 'onChange' of dependent fields. This means that if a combobox is dependent on
 * itself (e.g a search field) then it is filled too late. This will fill the combobox options on 'input' and then
 * when one is selected the human readable value will be in the combobox input and a computer-readable value will go
 * in the associated value field.
 **/
Behaviour.specify('.searchable', 'searchableField', 200, function (el) {
    var results = [];

    /**
     * This combobox comes from https://github.com/jenkinsci/jenkins/blob/master/war/src/main/webapp/scripts/combobox.js
     */
    var combobox = new ComboBox(el, function(query) {
        return results;
    }, {});

    el.addEventListener('input', debounce(function (e) {
        if (el.value.length < 2) { // Only perform a search if there are enough characters
            results = [];
            combobox.valueChanged();
            return;
        }

        // Get the values of the field this depends on
        var parameters = (el.getAttribute('fillDependsOn') || '').split(' ')
            .reduce(function (params, fieldName) {
                var dependentField = findNearBy(el, fieldName);
                if (dependentField) {
                    params[fieldName] = dependentField.value;
                }
                return params;
            }, {});
        // Request the search results
        fetch(el.getAttribute("fillUrl"), {
            headers: crumb.wrap({
              "Content-Type": "application/x-www-form-urlencoded",
            }),
            method: "post",
            body: new URLSearchParams(parameters),
        }).then((response) => {
            if (response.ok) {
                response.json().then((json) => {
                    results = (json.data || []).map(value => value.name);
                    combobox.valueChanged();
                });
            } else {
                results = [];
                combobox.valueChanged();
            }
        }).catch(() => {
            results = [];
            combobox.valueChanged();
        });
    }, 300));

    el.addEventListener('blur', function (e) {
        // clear the existing results
        results = [];
        // There's a race condition in combobox that means sometimes this isn't fired which messes with validation
        el.dispatchEvent(new Event('change'));
        // Clear the dependent fields
        document.querySelectorAll('[filldependson~="' + e.target.name.replace('_.', '') + '"]')
            .forEach(function (dependentField) {
                if (dependentField.name !== e.target.name) {
                    dependentField.value = '';
                    dependentField.setAttribute('value', '');
                    dependentField.dispatchEvent(new Event('change'));
            }
        });
    });

});
