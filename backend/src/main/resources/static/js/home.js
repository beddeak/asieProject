(() => {
    const directory = document.querySelector(".sidebar-directory");
    if (!directory) return;

    const compactScreen = window.matchMedia("(max-width: 760px)");
    const updateDirectory = () => {
        directory.open = !compactScreen.matches;
    };

    updateDirectory();
    compactScreen.addEventListener("change", updateDirectory);
})();
