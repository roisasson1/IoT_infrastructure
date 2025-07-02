document.addEventListener("DOMContentLoaded", function() {
    function getCookie(name) {
        const nameEQ = name + "=";
        const ca = document.cookie.split(';');
        for(let i=0; i < ca.length; i++) {
            let c = ca[i];
            while (c.charAt(0) === ' ') c = c.substring(1, c.length);
            if (c.indexOf(nameEQ) === 0) {
                let cookieValue = c.substring(nameEQ.length, c.length);
                cookieValue = cookieValue.replace(/\+/g, ' ');
                return decodeURIComponent(cookieValue);
            }
        }
        return null;
    }

    const companyName = getCookie("companyName") || "Your Company";

    const pathParts = window.location.pathname.split('/');
    let companyIdFromUrl = "N/A";
    if (pathParts.length > 2 && pathParts[1] === "company" && !isNaN(parseInt(pathParts[2]))) {
        companyIdFromUrl = pathParts[2];
    } else {
        companyIdFromUrl = getCookie("companyId") || "N/A";
    }

    const welcomeHeader = document.getElementById("welcome-message");
    if (welcomeHeader) {
        welcomeHeader.innerHTML = `${companyName}'s Dashboard`;
    }

    const registerProductButton = document.getElementById("registerProductButton");
    if (registerProductButton) {
        registerProductButton.href = `/company/${companyIdFromUrl}/product`;
    }

    const addContactButton = document.getElementById("addContactButton");
    if (addContactButton) {
         addContactButton.href = `/company/${companyIdFromUrl}/contact`;
    }

    const displayProductsButton = document.getElementById("displayProductsButton");
    if (displayProductsButton) {
        displayProductsButton.href = `/company/${companyIdFromUrl}/products`;
    }

    const displayContactsButton = document.getElementById("displayContactsButton");
    if (displayContactsButton) {
        displayContactsButton.href = `/company/${companyIdFromUrl}/contacts`;
    }

    console.log(`Company Page loaded. Company ID: ${companyIdFromUrl}, Company Name: ${companyName}`);
});