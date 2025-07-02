document.addEventListener('DOMContentLoaded', function() {
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

    const companyIndex = pathParts.indexOf("company");
    if (companyIndex > -1 && companyIndex + 1 < pathParts.length) {
        const potentialCompanyId = pathParts[companyIndex + 1];
        if (!isNaN(parseInt(potentialCompanyId))) {
            companyId = potentialCompanyId;
        }
    }

    const backButton = document.getElementById("backButton");
    if (backButton && companyId !== "N/A") {
        backButton.href = `/company/${companyId}`;
    } else if (backButton) {
        backButton.href = "/";
    }

    const hiddenCompIdInput = document.getElementById('comp_id');
    if (hiddenCompIdInput && companyId !== "N/A") {
        hiddenCompIdInput.value = companyId;
    } else if (hiddenCompIdInput) {
        hiddenCompIdInput.value = '';
    }

    const productRegistrationForm = document.getElementById('productRegistrationForm');
    const messageContainer = document.getElementById('messageContainer');

    if (productRegistrationForm) {
        productRegistrationForm.addEventListener('submit', function(event) {
            event.preventDefault();

            clearErrors(productRegistrationForm, messageContainer);

            const validationError = validateForm(productRegistrationForm);
            if (validationError !== null) {
                showToast(validationError, 'error');
                return;
            }

            const formData = new FormData(productRegistrationForm);

            fetch('/register_product', {
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
                showToast('Product registered successfully!', 'success');

                setTimeout(() => {
                    if (companyId !== "N/A") {
                        window.location.href = `/company/${companyId}`;
                    } else {
                        window.location.href = "/";
                    }
                }, 500);
            })
            .catch(error => {
                messageContainer.textContent = 'Error: ' + error.message;
                messageContainer.style.color = 'red';
                showToast('Product registration failed: ' + error.message, 'error');
            });
        });
    }

    function clearErrors(form, msgContainer) {
        msgContainer.textContent = '';
        msgContainer.style.color = 'black';

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
        const errorSpanId = inputElement.id + '_error';
        const errorSpan = document.getElementById(errorSpanId);
        if (errorSpan) {
            errorSpan.textContent = message;
        }
    }


    function validateForm(form) {
        let firstErrorMessage = null;

        clearErrors(form, document.getElementById('messageContainer'));

        const productName = form.product_name.value.trim();
        const version = form.version.value.trim();
        const compIdStr = form.comp_id.value.trim();

        if (!productName) {
            displayError(form.product_name, "Product name is required.");
            if (firstErrorMessage === null) firstErrorMessage = "Product name is required.";
        } else if (productName.length < 2) {
            displayError(form.product_name, "Product name must be at least 2 characters.");
            if (firstErrorMessage === null) firstErrorMessage = "Product name must be at least 2 characters.";
        }

        if (!version) {
            displayError(form.version, "Version is required.");
            if (firstErrorMessage === null) firstErrorMessage = "Version is required.";
        }

        if (!compIdStr || compIdStr === "N/A" || parseInt(compIdStr) <= 0) {
            displayError(form.comp_id, "Company ID is missing or invalid.");
            if (firstErrorMessage === null) firstErrorMessage = "Company ID is missing or invalid.";
        } else if (isNaN(parseInt(compIdStr))) {
             displayError(form.comp_id, "Company ID must be a valid number.");
             if (firstErrorMessage === null) firstErrorMessage = "Company ID must be a valid number.";
        }


        return firstErrorMessage;
    }
});