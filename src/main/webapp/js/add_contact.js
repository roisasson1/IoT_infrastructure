document.addEventListener("DOMContentLoaded", function() {
    const pathParts = window.location.pathname.split('/');
    let companyId = "N/A";

    function showToast(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = 'toast ' + type;
        toast.textContent = message;
        document.body.appendChild(toast);

        setTimeout(() => {
            toast.classList.add('hide');
            toast.addEventListener('transitionend', () => toast.remove());
        }, 3000);
    }

    if (pathParts.length > 2 && pathParts[1] === "company" && !isNaN(parseInt(pathParts[2]))) {
        companyId = pathParts[2];
    }

    const backButton = document.getElementById("backButton");
    if (backButton) {
        backButton.href = `/company/${companyId}`;
        console.log(`Updated back button href to: /company/${companyId}`);
    } else {
        console.warn("Back button with ID 'backButton' not found in add_contact.js.");
    }

    const addContactForm = document.getElementById('addContactForm');

    if (addContactForm) {
        addContactForm.addEventListener('submit', function(event) {
            event.preventDefault();

            clearErrors(addContactForm);

            const validationError = validateForm(addContactForm);
            if (validationError !== null) {
                showToast(validationError, 'error');
                return;
            }

            const formData = new FormData(addContactForm);
            formData.append('comp_id', companyId);

            fetch('/add_contact', {
                method: 'POST',
                body: formData
            })
            .then(response => {
                if (!response.ok) {
                    return response.text().then(text => { throw new Error(text) });
                }
                return response.text();
            })
            .then(data => {
                showToast('Contact added successfully!', 'success');
                addContactForm.reset();

                setTimeout(() => {
                    window.location.href = `/company/${companyId}`;
                }, 1500);
            })
            .catch(error => {
                showToast('Failed to add contact: ' + error.message, 'error');
            });
        });
    }

    function clearErrors(form) {
        const errorInputs = form.querySelectorAll('.input-error');
        errorInputs.forEach(input => {
            input.classList.remove('input-error');
        });

        const errorMessages = form.querySelectorAll('.error-message');
        errorMessages.forEach(span => {
            span.textContent = '';
        });
    }

    function displayError(inputElement, message) {
        inputElement.classList.add('input-error');
        const errorSpanId = inputElement.name + '_error';
        const errorSpan = document.getElementById(errorSpanId);
        if (errorSpan) {
            errorSpan.textContent = message;
        }
    }

    function validateForm(form) {
        let firstErrorMessage = null;

        const fields = [
            { el: form.contact_name, name: "Contact Name", minLen: 2 },
            { el: form.email, name: "Email", pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/ },
            { el: form.phone, name: "Phone Number", pattern: /^\d{9,10}$/ },
            { el: form.linkedin, name: "LinkedIn Link", pattern: /^https?:\/\/(www\.)?linkedin\.com\/.*$/i },
        ];

        for (const field of fields) {
            const val = field.el.value.trim();
            if (!val) {
                displayError(field.el, `${field.name} is required.`);
                if (firstErrorMessage === null) firstErrorMessage = `${field.name} is required.`;
                break;
            }
            if (field.minLen && val.length < field.minLen) {
                displayError(field.el, `${field.name} must be at least ${field.minLen} characters.`);
                if (firstErrorMessage === null) firstErrorMessage = `${field.name} must be at least ${field.minLen} characters.`;
                break;
            }
            if (field.pattern && !field.pattern.test(val)) {
                displayError(field.el, `Invalid ${field.name.toLowerCase()} format.`);
                if (firstErrorMessage === null) firstErrorMessage = `Invalid ${field.name.toLowerCase()} format.`;
                break;
            }
        }

        return firstErrorMessage;
    }
});