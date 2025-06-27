// Transaction History JavaScript
document.addEventListener('DOMContentLoaded', function() {
    // Global variables
    let currentTransactions = initialTransactionsData || [];
    let stompClient = null;
    let socket = null;

    // DOM elements
    const transactionList = document.getElementById('transaction-list');
    const loadingState = document.getElementById('loading-state');
    const emptyState = document.getElementById('empty-state');
    const emptyStateMessage = document.getElementById('empty-state-message');
    const sidebar = document.getElementById('sidebar');
    const overlay = document.getElementById('overlay');
    const sidebarToggle = document.getElementById('sidebar-toggle');
    const logoutModal = document.getElementById('logoutModal');

    // Filter elements
    const yearFilter = document.getElementById('year-filter');
    const monthFilter = document.getElementById('month-filter');
    const typeFilter = document.getElementById('type-filter');
    const applyFiltersBtn = document.getElementById('apply-filters');
    const clearFiltersBtn = document.getElementById('clear-filters');
    const exportBtn = document.getElementById('export-btn');

    // Summary elements
    const totalSentEl = document.getElementById('total-sent');
    const totalReceivedEl = document.getElementById('total-received');
    const netBalanceEl = document.getElementById('net-balance');
    const transactionCountEl = document.getElementById('transaction-count');

    // Initialize
    init();

    function init() {
        setupEventListeners();
        connectWebSocket();
        updateSummaryFromCurrentData();
    }

    function setupEventListeners() {
        // Sidebar toggle
        if (sidebarToggle) {
            sidebarToggle.addEventListener('click', toggleSidebar);
        }

        // Close sidebar
        const closeSidebarBtn = document.querySelector('.close-sidebar');
        if (closeSidebarBtn) {
            closeSidebarBtn.addEventListener('click', closeSidebar);
        }

        // Overlay click
        if (overlay) {
            overlay.addEventListener('click', closeSidebar);
        }

        // Filter buttons
        if (applyFiltersBtn) {
            applyFiltersBtn.addEventListener('click', applyFilters);
        }

        if (clearFiltersBtn) {
            clearFiltersBtn.addEventListener('click', clearFilters);
        }

        // Export button
        if (exportBtn) {
            exportBtn.addEventListener('click', exportTransactions);
        }

        // Logout functionality
        const logoutBtn = document.querySelector('.logout');
        if (logoutBtn) {
            logoutBtn.addEventListener('click', showLogoutModal);
        }

        const cancelLogout = document.getElementById('cancelLogout');
        const confirmLogout = document.getElementById('confirmLogout');

        if (cancelLogout) {
            cancelLogout.addEventListener('click', hideLogoutModal);
        }

        if (confirmLogout) {
            confirmLogout.addEventListener('click', performLogout);
        }

        // Close modal when clicking outside
        window.addEventListener('click', function(event) {
            if (event.target === logoutModal) {
                hideLogoutModal();
            }
        });

        // Filter change handlers - these will navigate to new pages
        [yearFilter, monthFilter].forEach(filter => {
            if (filter) {
                filter.addEventListener('change', handleFilterChange);
            }
        });

        // Client-side filters for immediate feedback
        if (typeFilter) {
            typeFilter.addEventListener('change', applyClientSideFilters);
        }
    }

    function connectWebSocket() {
        try {
            socket = new SockJS('/ws');
            stompClient = Stomp.over(socket);
            
            stompClient.connect({}, function(frame) {
                console.log('Connected to WebSocket: ' + frame);
                
                // Subscribe to user-specific transaction updates
                if (currentUserId) {
                    stompClient.subscribe(`/user/${currentUserId}/transactions`, function(message) {
                        const transaction = JSON.parse(message.body);
                        handleNewTransaction(transaction);
                    });
                }
            }, function(error) {
                console.error('WebSocket connection error:', error);
                // Retry connection after 5 seconds
                setTimeout(connectWebSocket, 5000);
            });
        } catch (error) {
            console.error('Failed to establish WebSocket connection:', error);
        }
    }

    function handleNewTransaction(transaction) {
        // Check if transaction is valid
        if (!transaction || !transaction.id) {
            console.error('Invalid transaction data received');
            return;
        }

        // Show notification for new transaction
        const isReceived = transaction.buyer && transaction.buyer.id === currentUserId;
        const amount = parseFloat(transaction.amount) || 0;
        const message = `${isReceived ? 'Received' : 'Sent'} ${formatCurrency(amount)}`;
        
        showNotification(message, 'success');
        
        // Add transaction to current list if not already present
        const existingIndex = currentTransactions.findIndex(t => t.id === transaction.id);
        if (existingIndex === -1) {
            currentTransactions.unshift(transaction); // Add to beginning
            updateSummaryFromCurrentData();
        }
        
        // Refresh the page to show updated data after a short delay
        setTimeout(() => {
            window.location.reload();
        }, 2000);
    }

    function handleFilterChange() {
        // Get current filter values
        const year = yearFilter?.value || '';
        const month = monthFilter?.value || '';
        
        // Build URL with filters
        const params = new URLSearchParams();
        if (year) params.set('year', year);
        if (month) params.set('month', month);
        
        // Navigate to filtered page
        const newUrl = `/transactions${params.toString() ? '?' + params.toString() : ''}`;
        window.location.href = newUrl;
    }

    function applyFilters() {
        // This function triggers server-side filtering by navigating to a new URL
        handleFilterChange();
    }

    function applyClientSideFilters() {
        const type = typeFilter?.value || '';
        
        // Get all transaction items from the DOM
        const transactionItems = document.querySelectorAll('.transaction-item');
        let visibleCount = 0;
        
        transactionItems.forEach(item => {
            let show = true;
            
            // Type filter - check data attribute or class
            if (type) {
                const transactionType = item.getAttribute('data-transaction-type') || 
                    (item.classList.contains('received') ? 'received' : 'sent');
                if (transactionType !== type) show = false;
            }
            
            item.style.display = show ? 'flex' : 'none';
            if (show) visibleCount++;
        });
        
        // Show/hide empty state based on visible transactions
        if (visibleCount === 0) {
            showEmptyState();
            updateEmptyStateMessage();
        } else {
            hideEmptyState();
        }
        
        // Update summary for visible transactions only
        updateSummaryFromVisibleTransactions();
    }

    function updateEmptyStateMessage() {
        if (!emptyStateMessage) return;
        
        const activeFilters = [];
        if (typeFilter?.value) activeFilters.push(`Type: ${typeFilter.value}`);
        
        if (activeFilters.length > 0) {
            emptyStateMessage.textContent = `No transactions match the selected filters: ${activeFilters.join(', ')}`;
        } else {
            emptyStateMessage.textContent = 'No transactions found for the selected period.';
        }
    }

    function clearFilters() {
        // Reset all filters
        if (yearFilter) yearFilter.value = '';
        if (monthFilter) monthFilter.value = '';
        if (typeFilter) typeFilter.value = '';
        
        // Navigate to unfiltered page
        window.location.href = '/transactions';
    }

    function updateSummaryFromCurrentData() {
        if (!currentUserId || currentTransactions.length === 0) {
            updateSummaryDisplay(0, 0, 0);
            return;
        }
        
        let totalSent = 0;
        let totalReceived = 0;
        let transactionCount = 0;

        currentTransactions.forEach(transaction => {
            if (!transaction || !transaction.amount) return;
            
            const amount = parseFloat(transaction.amount) || 0;
            const isReceived = transaction.buyer && transaction.buyer.id === currentUserId;

            // Only count completed transactions in summary
            if (transaction.status === 'COMPLETED') {
                transactionCount++;
                if (isReceived) {
                    totalReceived += amount;
                } else {
                    totalSent += amount;
                }
            }
        });

        updateSummaryDisplay(totalSent, totalReceived, transactionCount);
    }

    function updateSummaryFromVisibleTransactions() {
        const visibleItems = document.querySelectorAll('.transaction-item:not([style*="display: none"])');
        let totalSent = 0;
        let totalReceived = 0;
        let transactionCount = 0;

        visibleItems.forEach(item => {
            const amountEl = item.querySelector('.transaction-amount');
            const statusEl = item.querySelector('.transaction-status');
            
            if (!amountEl || !statusEl) return;
            
            const amountText = amountEl.textContent || '';
            const statusText = statusEl.textContent || '';
            const isReceived = amountText.startsWith('+');
            
            // Only count completed transactions
            if (statusText.toLowerCase().includes('completed')) {
                transactionCount++;
                
                // Extract numeric value from formatted amount
                const numericAmount = parseFloat(amountText.replace(/[^\d.-]/g, '')) || 0;
                
                if (isReceived) {
                    totalReceived += numericAmount;
                } else {
                    totalSent += numericAmount;
                }
            }
        });

        updateSummaryDisplay(totalSent, totalReceived, transactionCount);
    }

    function updateSummaryDisplay(totalSent, totalReceived, transactionCount) {
        const netBalance = totalReceived - totalSent;

        // Update summary elements with null checks - all amounts in NGN
        if (totalSentEl) {
            totalSentEl.textContent = formatCurrency(totalSent);
            totalSentEl.className = 'summary-value negative';
        }

        if (totalReceivedEl) {
            totalReceivedEl.textContent = formatCurrency(totalReceived);
            totalReceivedEl.className = 'summary-value positive';
        }

        if (netBalanceEl) {
            netBalanceEl.textContent = formatCurrency(Math.abs(netBalance));
            netBalanceEl.className = `summary-value ${netBalance >= 0 ? 'positive' : 'negative'}`;
        }

        if (transactionCountEl) {
            transactionCountEl.textContent = transactionCount.toLocaleString();
        }
    }

    function showLoading(show) {
        if (loadingState) {
            loadingState.style.display = show ? 'flex' : 'none';
        }
    }

    function showEmptyState() {
        if (emptyState) {
            emptyState.style.display = 'flex';
        }
        
        if (transactionList) {
            transactionList.style.display = 'none';
        }
    }

    function hideEmptyState() {
        if (emptyState) {
            emptyState.style.display = 'none';
        }
        
        if (transactionList) {
            transactionList.style.display = 'block';
        }
    }

    function exportTransactions() {
        // Get visible transactions for export
        const visibleItems = document.querySelectorAll('.transaction-item:not([style*="display: none"])');
        
        if (visibleItems.length === 0) {
            showNotification('No transactions to export', 'warning');
            return;
        }

        try {
            const csvContent = generateCSVFromDOM(visibleItems);
            const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
            const link = document.createElement('a');
            
            if (link.download !== undefined) {
                const url = URL.createObjectURL(blob);
                link.setAttribute('href', url);
                link.setAttribute('download', `transactions_${new Date().toISOString().split('T')[0]}.csv`);
                link.style.visibility = 'hidden';
                document.body.appendChild(link);
                link.click();
                document.body.removeChild(link);
                URL.revokeObjectURL(url); // Clean up
                
                showNotification('Transactions exported successfully!', 'success');
            }
        } catch (error) {
            console.error('Export error:', error);
            showNotification('Failed to export transactions', 'error');
        }
    }

    function generateCSVFromDOM(transactionItems) {
        const headers = ['Date', 'Type', 'Amount (NGN)', 'From/To', 'Status', 'Description', 'Transaction ID'];
        const csvRows = [headers.join(',')];

        transactionItems.forEach(item => {
            try {
                const isReceived = item.classList.contains('received') || 
                    item.querySelector('.transaction-icon.received');
                
                const userEl = item.querySelector('.transaction-user');
                const amountEl = item.querySelector('.transaction-amount');
                const dateEl = item.querySelector('.transaction-date');
                const statusEl = item.querySelector('.transaction-status');
                const descriptionEl = item.querySelector('.transaction-description');
                const idEl = item.querySelector('.transaction-id');
                
                const userText = userEl ? userEl.textContent.trim() : '';
                const amountText = amountEl ? amountEl.textContent.trim() : '';
                const dateText = dateEl ? dateEl.textContent.trim() : '';
                const statusText = statusEl ? statusEl.textContent.trim() : '';
                const descriptionText = descriptionEl ? descriptionEl.textContent.trim() : '';
                const idText = idEl ? idEl.textContent.replace('ID: ', '').trim() : '';
                
                const row = [
                    `"${dateText}"`,
                    isReceived ? 'Received' : 'Sent',
                    `"${amountText}"`,
                    `"${userText}"`,
                    `"${statusText}"`,
                    `"${descriptionText}"`,
                    `"${idText}"`
                ];
                csvRows.push(row.join(','));
            } catch (error) {
                console.error('Error processing transaction item:', error);
            }
        });

        return csvRows.join('\n');
    }

    // Utility functions
    function formatCurrency(amount) {
        if (isNaN(amount) || amount === null || amount === undefined) {
            amount = 0;
        }
        
        // All amounts are in NGN
        return `₦${amount.toLocaleString('en-US', { 
            minimumFractionDigits: 2, 
            maximumFractionDigits: 2 
        })}`;
    }

    function formatDate(date) {
        if (!date || !(date instanceof Date)) {
            return 'Invalid Date';
        }
        
        return date.toLocaleDateString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    function formatStatus(status) {
        if (!status || typeof status !== 'string') return 'Unknown';
        return status.charAt(0).toUpperCase() + status.slice(1).toLowerCase();
    }

    function showNotification(message, type = 'info') {
        // Create notification element
        const notification = document.createElement('div');
        notification.className = `notification notification-${type}`;
        
        const iconClass = type === 'success' ? 'fa-check-circle' : 
                         type === 'warning' ? 'fa-exclamation-triangle' : 
                         type === 'error' ? 'fa-exclamation-circle' : 'fa-info-circle';
        
        notification.innerHTML = `
            <i class="fas ${iconClass}"></i>
            <span>${message}</span>
        `;

        document.body.appendChild(notification);

        // Show notification
        setTimeout(() => notification.classList.add('show'), 100);

        // Hide notification after 3 seconds
        setTimeout(() => {
            notification.classList.remove('show');
            setTimeout(() => {
                if (notification.parentNode) {
                    document.body.removeChild(notification);
                }
            }, 300);
        }, 3000);
    }

    // Sidebar functions
    function toggleSidebar() {
        sidebar?.classList.toggle('active');
        overlay?.classList.toggle('active');
    }

    function closeSidebar() {
        sidebar?.classList.remove('active');
        overlay?.classList.remove('active');
    }

    // Logout functions
    function showLogoutModal() {
        if (logoutModal) {
            logoutModal.style.display = 'flex';
        }
    }

    function hideLogoutModal() {
        if (logoutModal) {
            logoutModal.style.display = 'none';
        }
    }

    function performLogout() {
        try {
            // Create form and submit
            const form = document.createElement('form');
            form.method = 'POST';
            form.action = '/logout';
            
            // Add CSRF token if available
            const csrfToken = document.querySelector('meta[name="_csrf"]');
            const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
            
            if (csrfToken && csrfHeader) {
                const csrfInput = document.createElement('input');
                csrfInput.type = 'hidden';
                csrfInput.name = '_token';
                csrfInput.value = csrfToken.getAttribute('content');
                form.appendChild(csrfInput);
            }
            
            document.body.appendChild(form);
            form.submit();
        } catch (error) {
            console.error('Logout error:', error);
            // Fallback - redirect to login
            window.location.href = '/login';
        }
    }

    // Modal functions for transaction details
    function showTransactionModal(transaction) {
        if (!transaction) {
            showNotification('Transaction details not available', 'error');
            return;
        }

        const modal = document.createElement('div');
        modal.className = 'modal transaction-modal';
        modal.style.display = 'flex';
        modal.innerHTML = createTransactionModalContent(transaction);
        
        document.body.appendChild(modal);
        
        // Close modal events
        const closeBtn = modal.querySelector('.close-modal');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => {
                modal.style.display = 'none';
                if (modal.parentNode) {
                    document.body.removeChild(modal);
                }
            });
        }
        
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                modal.style.display = 'none';
                if (modal.parentNode) {
                    document.body.removeChild(modal);
                }
            }
        });
    }

    function createTransactionModalContent(transaction) {
        if (!transaction || !currentUserId) {
            return '<div class="modal-content"><p>Transaction details not available</p></div>';
        }

        const isReceived = transaction.buyer && transaction.buyer.id === currentUserId;
        const otherUser = isReceived ? transaction.seller : transaction.buyer;
        const amount = parseFloat(transaction.amount) || 0;
        
        return `
            <div class="modal-content transaction-modal-content">
                <div class="modal-header">
                    <h3>Transaction Details</h3>
                    <button class="close-modal">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <div class="modal-body">
                    <div class="transaction-detail-row">
                        <label>Transaction ID:</label>
                        <span>${transaction.id || 'N/A'}</span>
                    </div>
                    <div class="transaction-detail-row">
                        <label>Type:</label>
                        <span class="${isReceived ? 'positive' : 'negative'}">${isReceived ? 'Received' : 'Sent'}</span>
                    </div>
                    <div class="transaction-detail-row">
                        <label>Amount:</label>
                        <span class="${isReceived ? 'positive' : 'negative'}">${formatCurrency(amount)}</span>
                    </div>
                    <div class="transaction-detail-row">
                        <label>${isReceived ? 'From' : 'To'}:</label>
                        <span>${otherUser ? (otherUser.username || otherUser.email || 'Unknown') : 'Unknown'}</span>
                    </div>
                    <div class="transaction-detail-row">
                        <label>Date:</label>
                        <span>${transaction.timestamp ? formatDate(new Date(transaction.timestamp)) : 'N/A'}</span>
                    </div>
                    <div class="transaction-detail-row">
                        <label>Status:</label>
                        <span class="status-${(transaction.status || '').toLowerCase()}">${formatStatus(transaction.status)}</span>
                    </div>
                    ${transaction.description ? `
                    <div class="transaction-detail-row">
                        <label>Description:</label>
                        <span>${transaction.description}</span>
                    </div>
                    ` : ''}
                </div>
            </div>
        `;
    }

    // Global functions for button actions
    window.viewTransactionDetails = function(transactionId) {
        const transaction = currentTransactions.find(t => t && t.id === transactionId);
        if (transaction) {
            showTransactionModal(transaction);
        } else {
            showNotification('Transaction not found', 'error');
        }
    };

    window.shareTransaction = function(transactionId) {
        const transaction = currentTransactions.find(t => t && t.id === transactionId);
        if (!transaction) {
            showNotification('Transaction not found', 'error');
            return;
        }

        const isReceived = transaction.buyer && transaction.buyer.id === currentUserId;
        const amount = parseFloat(transaction.amount) || 0;
        
        if (navigator.share) {
            navigator.share({
                title: 'Xchange Transaction',
                text: `Transaction ${isReceived ? 'received' : 'sent'}: ${formatCurrency(amount)}`,
                url: window.location.href
            }).catch(error => {
                console.error('Error sharing:', error);
                fallbackShare(transactionId);
            });
        } else {
            fallbackShare(transactionId);
        }
    };

    function fallbackShare(transactionId) {
        // Fallback: copy to clipboard
        const url = `${window.location.origin}/transaction/${transactionId}`;
        if (navigator.clipboard) {
            navigator.clipboard.writeText(url).then(() => {
                showNotification('Transaction link copied to clipboard!', 'success');
            }).catch(() => {
                showNotification('Unable to copy link', 'error');
            });
        } else {
            showNotification('Sharing not supported on this device', 'warning');
        }
    }

    // Handle filter changes from URL parameters (browser back/forward)
    window.addEventListener('popstate', function(event) {
        // Reload the page to get fresh data from backend
        window.location.reload();
    });

    // Cleanup on page unload
    window.addEventListener('beforeunload', function() {
        if (stompClient && stompClient.connected) {
            stompClient.disconnect();
        }
    });
});