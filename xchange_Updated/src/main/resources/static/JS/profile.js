// Profile Modal JavaScript Functions - Global scope for onclick access
function showUserProfileModal() {
    const modal = document.getElementById('userProfileModal');
    modal.style.display = 'flex';
    
    // Trigger reflow to ensure display change is applied
    modal.offsetHeight;
    
    // Add show class for animation
    modal.classList.add('show');
    
    // Prevent body scroll
    document.body.style.overflow = 'hidden';
}

function hideUserProfileModal() {
    const modal = document.getElementById('userProfileModal');
    const modalContent = modal.querySelector('.modal-content');
    
    // Start closing animation
    modalContent.style.transform = 'scale(0.7)';
    modalContent.style.opacity = '0';
    modal.style.opacity = '0';
    
    // Wait for animation to complete before hiding
    setTimeout(() => {
        modal.classList.remove('show');
        modal.style.display = 'none';
        document.body.style.overflow = 'auto';
        
        // Reset styles for next opening
        modalContent.style.transform = '';
        modalContent.style.opacity = '';
        modal.style.opacity = '';
    }, 400);
}

// Initialize event listeners when DOM is loaded
document.addEventListener('DOMContentLoaded', function() {
    const userProfileModal = document.getElementById('userProfileModal');
    
    if (userProfileModal) {
        // Close modal when clicking outside
        userProfileModal.addEventListener('click', function(e) {
            if (e.target === this) {
                hideUserProfileModal();
            }
        });
    }
    
    // Close modal with Escape key
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape') {
            const modal = document.getElementById('userProfileModal');
            if (modal && modal.classList.contains('show')) {
                hideUserProfileModal();
            }
        }
    });
});