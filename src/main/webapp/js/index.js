document.addEventListener('DOMContentLoaded', function() {
        const loginForm = document.getElementById('loginForm');

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

        if (loginForm) {
            loginForm.addEventListener('submit', function(event) {
                event.preventDefault();
                const formData = new FormData(loginForm);

                fetch('/login', {
                    method: 'POST',
                    body: formData
                })
                .then(response => {
                    if (response.ok) {
                        window.location.href = response.url;
                    } else {
                        return response.text().then(errorMessage => {
                            throw new Error(errorMessage);
                        });
                    }
                })
                .catch(error => {
                    console.error('Login failed:', error);
                    showToast('Login failed: ' + error.message, 'error');
                });
            });
        }
    });