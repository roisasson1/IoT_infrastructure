document.addEventListener('DOMContentLoaded', function() {
    const registrationForm = document.getElementById('registrationForm');

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

    if (registrationForm) {
        registrationForm.addEventListener('submit', function(event) {
            event.preventDefault();

            clearErrors(registrationForm);

            if (!validateForm(registrationForm)) {
                showToast('Registration failed: Please correct the errors in the form.', 'error');
                return;
            }

            const formData = new FormData(registrationForm);

            fetch('/register', {
                method: 'POST',
                body: formData
            })
            .then(response => {
                if (response.ok) {
                    showToast('Registration successful! Redirecting...', 'success');
                    setTimeout(() => {
                        window.location.href = response.url;
                    }, 1000);
                    return;
                } else {
                    return response.text().then(errorMessage => {
                        throw new Error(errorMessage);
                    });
                }
            })
            .catch(error => {
                console.error('Registration failed:', error);
                showToast(error.message, 'error');
            });
        });
    }

    function clearErrors(form) {
        const errorInputs = form.querySelectorAll('.input-error');
        errorInputs.forEach(input => {
            input.classList.remove('input-error');
            input.removeAttribute('aria-invalid');
            input.removeAttribute('aria-describedby');
        });

        const errorMessages = form.querySelectorAll('.error-message');
        errorMessages.forEach(span => {
            span.textContent = '';
        });
    }

    function validateForm(form) {
        let isValid = true;

        const fields = [
            { el: form.company_name, name: "Company name", minLen: 3, pattern: /^[^.]+$/ },
            { el: form.password, name: "Password", minLen: 6 },
            { el: form.contact_name, name: "Contact name", minLen: 2 },
            { el: form.email, name: "Email", pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/ },
            { el: form.phone, name: "Phone number", pattern: /^\d{9,10}$/ },
            { el: form.linkedin, name: "LinkedIn link", pattern: /^https?:\/\/(www\.)?linkedin\.com\/.*$/i },
            { el: form.card_number, name: "Card number", pattern: /^\d{16}$/ },
            { el: form.exp_date, name: "Expiration date", pattern: /^\d{2}\/\d{2}$/ },
            { el: form.cvc, name: "CVC", pattern: /^\d{3,4}$/ },
        ];

        for (const field of fields) {
            const val = field.el.value.trim();
            if (!val) {
                displayError(field.el, `${field.name} is required.`);
                isValid = false;
                break;
            }
            if (field.minLen && val.length < field.minLen) {
                displayError(field.el, `${field.name} must be at least ${field.minLen} characters.`);
                isValid = false;
                break;
            }
            if (field.pattern && !field.pattern.test(val)) {
                displayError(field.el, `Invalid ${field.name.toLowerCase()} format.`);
                isValid = false;
                break;
            }
        }

        if (isValid) {
            const expDateStr = form.exp_date.value.trim();
            const [month, year] = expDateStr.split('/').map(Number);
            const currentYear = new Date().getFullYear() % 100;
            const currentMonth = new Date().getMonth() + 1;

            if (month < 1 || month > 12) {
                displayError(form.exp_date, "Expiration month must be between 01 and 12.");
                isValid = false;
            } else if (year < currentYear || (year === currentYear && month < currentMonth)) {
                displayError(form.exp_date, "Expiration date cannot be in the past.");
                isValid = false;
            }
        }

        return isValid;
    }

    function displayError(inputElement, message) {
        inputElement.classList.add('input-error');
        inputElement.setAttribute('aria-invalid', 'true');
        const errorSpanId = inputElement.name + '_error';
        const errorSpan = document.getElementById(errorSpanId);
        if (errorSpan) {
            errorSpan.textContent = message;
            inputElement.setAttribute('aria-describedby', errorSpanId);
            inputElement.focus();
        }
    }
});