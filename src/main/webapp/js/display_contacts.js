function getCookie(name) {
    const nameEQ = name + "=";
    const ca = document.cookie.split(';');
    for(let i=0; i < ca.length; i++) {
        let c = ca[i];
        while (c.charAt(0) === ' ') c = c.substring(1, c.length);
        if (c.indexOf(nameEQ) === 0) {
            let value = c.substring(nameEQ.length, c.length);
            return decodeURIComponent(value.replace(/\+/g, ' '));
        }
    }
    return null;
}

document.addEventListener("DOMContentLoaded", () => {
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

    const pathParts = window.location.pathname.split('/');
    let companyId = "N/A";

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
        console.log(`Updated back button href to: /company/${companyId}`);
    } else if (backButton) {
        backButton.href = "/";
    }

    const companyNameHeader = document.getElementById("company-name-header");
    const companyIdDisplay = document.getElementById("company-id-display");
    if (companyIdDisplay) {
        companyIdDisplay.textContent = companyId !== "N/A" ? companyId : "Unknown";
    }

    const tableBody = document.getElementById("contacts-table-body");

    if (companyId !== "N/A") {
        fetch(`/display_contacts?comp_id=${companyId}`)
            .then(response => {
                if (!response.ok) {
                    return response.text().then(text => { throw new Error(text) });
                }
                return response.json();
            })
            .then(contacts => {
                if (contacts && contacts.length > 0) {
                    contacts.forEach(contact => {
                        const row = document.createElement("tr");

                        row.innerHTML = `
                            <td>${contact.name}</td>
                            <td>${contact.email}</td>
                            <td>${contact.phone_number}</td> <td><a href="${contact.linkedin_account}" target="_blank">LinkedIn</a></td> `;
                        tableBody.appendChild(row);
                    });
                    showToast(`Successfully loaded ${contacts.length} contacts.`, 'success');
                } else {
                    console.warn("No contacts found for this company.");
                    const row = document.createElement("tr");
                    row.innerHTML = `<td colspan="4" style="text-align: center;">No contacts to display for this company.</td>`;
                    tableBody.appendChild(row);
                    showToast("No contacts found for this company.", 'info');
                }
            })
            .catch(error => {
                console.error("Error fetching contacts:", error);
                const row = document.createElement("tr");
                row.innerHTML = `<td colspan="4" style="text-align: center; color: red;">Failed to load contacts: ${error.message}</td>`;
                tableBody.appendChild(row);
                showToast(`Failed to load contacts: ${error.message}`, 'error');
            });
    } else {
        showToast("Company ID not found in URL. Cannot fetch contacts.", 'error');
        if (tableBody) {
             const row = document.createElement("tr");
             row.innerHTML = `<td colspan="4" style="text-align: center; color: red;">Error: Company ID not found.</td>`;
             tableBody.appendChild(row);
        }
    }
});