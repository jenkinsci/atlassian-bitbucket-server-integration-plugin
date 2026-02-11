/**
 * Searchable field autocomplete combobox for Bitbucket Jenkins plugin.
 *
 * Provides compatibility with both Jenkins 2.492+ (tippy.js-based dropdowns)
 * and earlier versions (legacy ComboBox).
 *
 * @see https://github.com/jenkinsci/jenkins/pull/9462
 * @see https://github.com/jenkinsci/jenkins/blob/master/war/src/main/js/components/autocomplete/index.js
 */

/** Maximum number of dropdown items to display */
var MAX_DROPDOWN_ITEMS = 20;

/** Delay in milliseconds before hiding dropdown on focus out, allows click events to complete */
var FOCUSOUT_DELAY_MS = 200;

/**
 * Dispatch a 'change' event on an element to trigger validation and dependent field updates.
 *
 * @param {HTMLElement} element - The element to trigger the change event on
 */
function triggerChange(element) {
    element.dispatchEvent(new Event('change'));
}

/**
 * Debounce function calls to prevent excessive executions.
 *
 * @param {Function} func - The function to debounce
 * @param {number} wait - The timeout period in milliseconds
 * @returns {Function} The debounced function
 */
function debounce(func, wait) {
    var timeout;
    return function () {
        var context = this;
        var args = arguments;
        function later() {
            timeout = null;
            func.apply(context, args);
        }
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

/**
 * Check if we're running on Jenkins 2.492+ which uses tippy.js-based dropdowns.
 *
 * Note: This detection relies on tippy.js being loaded, which Jenkins 2.492+ includes
 * as part of its core dropdown implementation. While another plugin could theoretically
 * load tippy.js on older Jenkins, this is unlikely in practice. If more robust detection
 * is needed, consider checking for other Jenkins 2.492+ specific APIs.
 *
 * @returns {boolean} True if modern dropdown API is available
 */
function isModernJenkins() {
    return typeof tippy !== 'undefined';
}

/**
 * Create a dropdown item configuration for a suggestion.
 * Pattern follows Jenkins' autocomplete/index.js convertSuggestionToItem.
 *
 * @param {string} suggestion - The suggestion text
 * @param {HTMLElement} inputEl - The input element
 * @param {Object} dropdownRef - Reference object containing the dropdown instance.
 *                               We use a reference object pattern here because the tippy
 *                               instance is created after item configurations are generated,
 *                               but item event handlers need access to the instance to hide it.
 * @returns {Object} Item configuration for generateDropdownItems
 */
function createDropdownItem(suggestion, inputEl, dropdownRef) {
    function selectItem() {
        inputEl.value = suggestion;
        triggerChange(inputEl);
        inputEl.focus();
    }

    return {
        label: suggestion,
        type: 'button',
        onClick: function () {
            selectItem();
        },
        onKeyPress: function (evt) {
            if (evt.key === 'Tab' || evt.key === 'Enter') {
                selectItem();
                if (dropdownRef.instance) {
                    dropdownRef.instance.hide();
                }
                evt.preventDefault();
            }
        }
    };
}

/**
 * Generate dropdown menu items element.
 * Reuses Jenkins' dropdown CSS classes for consistent styling.
 *
 * @param {Array<Object>} items - Array of item configurations
 * @returns {HTMLElement} The dropdown menu element
 */
function generateDropdownItems(items) {
    var menuItems = document.createElement('div');
    menuItems.classList.add('jenkins-dropdown');
    menuItems.classList.add('jenkins-dropdown--compact');

    if (items.length === 0) {
        var placeholder = document.createElement('p');
        placeholder.classList.add('jenkins-dropdown__placeholder');
        placeholder.textContent = 'No results';
        menuItems.appendChild(placeholder);
        return menuItems;
    }

    items.forEach(function (item) {
        var button = document.createElement('button');
        button.type = 'button';
        button.classList.add('jenkins-dropdown__item');
        button.textContent = item.label;

        if (item.onClick) {
            button.addEventListener('click', item.onClick);
        }
        if (item.onKeyPress) {
            button.onkeypress = item.onKeyPress;
        }

        menuItems.appendChild(button);
    });

    return menuItems;
}

/**
 * Modern dropdown implementation using tippy.js (Jenkins 2.492+).
 * Pattern follows Jenkins' components/autocomplete/index.js.
 *
 * @param {HTMLElement} inputEl - The input element to attach the dropdown to
 * @returns {Object} Dropdown controller with setItems and hide methods
 */
function createModernDropdown(inputEl) {
    var dropdownRef = { instance: null };

    // Configure input for dropdown behavior
    inputEl.setAttribute('autocomplete', 'off');
    inputEl.dataset.hideOnClick = 'false';
    inputEl.style.position = 'relative';

    return {
        /**
         * Update dropdown with new items and show/hide accordingly.
         *
         * @param {Array<string>} results - Array of suggestion strings
         */
        setItems: function (results) {
            var items = results.slice(0, MAX_DROPDOWN_ITEMS).map(function (suggestion) {
                return createDropdownItem(suggestion, inputEl, dropdownRef);
            });
            var menuContent = generateDropdownItems(items);

            if (!dropdownRef.instance) {
                dropdownRef.instance = tippy(inputEl, {
                    content: menuContent,
                    interactive: true,
                    trigger: 'manual',
                    placement: 'bottom-start',
                    arrow: false,
                    theme: 'dropdown',
                    appendTo: document.body,
                    offset: [0, 0],
                    maxWidth: 'none',
                    hideOnClick: false,
                    onCreate: function (instance) {
                        instance.popper.style.minWidth = inputEl.offsetWidth + 'px';
                    }
                });
            } else {
                dropdownRef.instance.setContent(menuContent);
            }

            if (results.length > 0) {
                dropdownRef.instance.show();
            } else {
                dropdownRef.instance.hide();
            }
        },

        /**
         * Hide the dropdown.
         */
        hide: function () {
            if (dropdownRef.instance) {
                dropdownRef.instance.hide();
            }
        }
    };
}

/**
 * Legacy dropdown implementation using the ComboBox class (Jenkins < 2.492).
 *
 * @see https://github.com/jenkinsci/jenkins/blob/master/war/src/main/webapp/scripts/combobox.js
 * @param {HTMLElement} inputEl - The input element to attach the combobox to
 * @returns {Object} Dropdown controller with setItems and hide methods
 */
function createLegacyDropdown(inputEl) {
    var results = [];

    // ComboBox is provided by Jenkins core scripts/combobox.js
    var combobox = new ComboBox(inputEl, function () {
        return results;
    }, {});

    return {
        /**
         * Update combobox with new items.
         *
         * @param {Array<string>} items - Array of suggestion strings
         */
        setItems: function (items) {
            results = items;
            combobox.valueChanged();
        },

        /**
         * Hide the dropdown by clearing results.
         */
        hide: function () {
            results = [];
            combobox.valueChanged();
        }
    };
}

/**
 * Create appropriate dropdown based on Jenkins version.
 *
 * @param {HTMLElement} inputEl - The input element
 * @returns {Object} Dropdown controller
 */
function createDropdown(inputEl) {
    if (isModernJenkins()) {
        return createModernDropdown(inputEl);
    }
    return createLegacyDropdown(inputEl);
}

/**
 * Modified from the Jenkins combobox code.
 * @see https://github.com/jenkinsci/jenkins/blob/master/core/src/main/resources/lib/form/combobox/combobox.js
 *
 * The Jenkins combobox only fills items 'onChange' of dependent fields. This means that if a combobox
 * is dependent on itself (e.g a search field) then it is filled too late. This will fill the combobox
 * options on 'input' and then when one is selected the human readable value will be in the combobox
 * input and a computer-readable value will go in the associated value field.
 */
Behaviour.specify('.searchable', 'searchableField', 200, function (el) {
    var dropdown = createDropdown(el);

    /**
     * Handle input changes - fetch suggestions and update dropdown.
     */
    function handleInput() {
        // Only perform a search if there are enough characters
        if (el.value.length < 2) {
            dropdown.setItems([]);
            return;
        }

        // Get the values of the fields this depends on
        var fillDependsOn = el.getAttribute('fillDependsOn') || '';
        var parameters = fillDependsOn.split(' ').reduce(function (params, fieldName) {
            if (!fieldName) {
                return params;
            }
            var dependentField = findNearBy(el, fieldName);
            if (dependentField) {
                params[fieldName] = dependentField.value;
            }
            return params;
        }, {});

        // Request the search results
        fetch(el.getAttribute('fillUrl'), {
            headers: crumb.wrap({
                'Content-Type': 'application/x-www-form-urlencoded'
            }),
            method: 'post',
            body: new URLSearchParams(parameters)
        }).then(function (response) {
            if (response.ok) {
                response.json().then(function (json) {
                    var results = (json.data || []).map(function (value) {
                        return value.name;
                    });
                    dropdown.setItems(results);
                });
            } else {
                dropdown.setItems([]);
            }
        }).catch(function () {
            dropdown.setItems([]);
        });
    }

    /**
     * Handle blur event to trigger validation and clear dependent fields.
     *
     * @param {Event} e - The blur event
     */
    function handleBlur(e) {
        // There's a race condition in combobox that means sometimes this isn't fired
        // which messes with validation
        triggerChange(el);

        // Clear the dependent fields
        var fieldName = e.target.name.replace('_.', '');
        document.querySelectorAll('[filldependson~="' + fieldName + '"]')
            .forEach(function (dependentField) {
                if (dependentField.name !== e.target.name) {
                    dependentField.value = '';
                    dependentField.setAttribute('value', '');
                    triggerChange(dependentField);
                }
            });
    }

    /**
     * Handle focus out to hide dropdown after a delay.
     * Delay allows click events on dropdown items to complete before hiding.
     * Pattern follows Jenkins' autocomplete/index.js.
     */
    function handleFocusOut() {
        setTimeout(function () {
            dropdown.hide();
        }, FOCUSOUT_DELAY_MS);
    }

    el.addEventListener('input', debounce(handleInput, 300));
    el.addEventListener('blur', handleBlur);
    el.addEventListener('focusout', handleFocusOut);
});
