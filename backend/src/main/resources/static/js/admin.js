document.addEventListener("submit", (event) => {
    const message = event.target.dataset.confirm;
    if (message && !window.confirm(message)) {
        event.preventDefault();
    }
});

const departmentForm = document.getElementById("department-form");
function revealDepartmentForm() {
    if (departmentForm && window.location.hash === "#department-form") {
        departmentForm.open = true;
    }
}
window.addEventListener("hashchange", revealDepartmentForm);
revealDepartmentForm();
