class LoadingScreen {
    constructor() {
        this.overlay = document.getElementById('loadingOverlay');
    }

    show(message = 'Loading...') {
        if (this.overlay) {
            const textElement = this.overlay.querySelector('.loading-text');
            if (textElement) {
                textElement.textContent = message;
            }
            this.overlay.classList.remove('hidden');
            document.body.style.overflow = 'hidden';
        }
    }

    hide() {
        if (this.overlay) {
            this.overlay.classList.add('hidden');
            document.body.style.overflow = '';
        }
    }
}

// Initialize loading screen
const loadingScreen = new LoadingScreen();

// Hide loading screen when page is fully loaded
window.addEventListener('load', () => {
    loadingScreen.hide();
});

// Export for use in other scripts
window.loadingScreen = loadingScreen;