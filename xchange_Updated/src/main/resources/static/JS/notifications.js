class NotificationHandler {
    constructor() {
        this.stompClient = null;
        this.userId = document.querySelector('meta[name="user-id"]')?.content;
        this.username = document.querySelector('meta[name="username"]')?.content;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.notificationQueue = [];
        this.isProcessingNotification = false;
        this.connectWebSocket();
        this.createNotificationContainer();
    }

 createNotificationContainer() {
    let container = document.getElementById('notification-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'notification-container';
        document.body.appendChild(container);
        console.log('Notification container created');
    }
}

    connectWebSocket() {
        if (!this.userId) {
            console.error('User ID not found in meta tags');
            return;
        }

        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            console.error('Max reconnection attempts reached');
            return;
        }

        try {
            const socket = new SockJS('/ws');
            this.stompClient = Stomp.over(socket);
            this.stompClient.debug = null; // Disable debug messages

            this.stompClient.connect({}, 
                frame => this.onConnect(frame),
                error => this.onError(error)
            );
        } catch (error) {
            console.error('WebSocket connection failed:', error);
            this.scheduleReconnect();
        }
    }

    onConnect(frame) {
        console.log('Connected to WebSocket');
        this.reconnectAttempts = 0;

        // Subscribe to user-specific queue
        const userQueue = `/user/${this.userId}/queue/notifications`;
        console.log('Subscribing to:', userQueue);
        
        this.stompClient.subscribe(userQueue, message => {
            try {
                const notification = JSON.parse(message.body);
                console.log('Received notification:', notification);
                this.handleTransaction(notification);
            } catch (error) {
                console.error('Error processing notification:', error);
            }
        });
    }

    handleTransaction(notification) {
        try {
            console.log('Processing transaction:', notification);
            const currentUserId = parseInt(this.userId);
            const receiverId = parseInt(notification.receiverId);
            const senderId = parseInt(notification.senderId);

            // Only process notifications relevant to current user
            if (currentUserId !== receiverId && currentUserId !== senderId) {
                console.log('Transaction not relevant to current user');
                return;
            }

            const isReceiver = currentUserId === receiverId;
            const notificationData = {
                type: isReceiver ? 'INCOMING' : 'OUTGOING',
                message: isReceiver 
                    ? `Received ₦${this.formatAmount(notification.amount)} from ${notification.senderName}`
                    : `Sent ₦${this.formatAmount(notification.amount)} to ${notification.receiverName}`,
                amount: notification.amount,
                timestamp: new Date().toLocaleString('en-US', {
                    month: 'short',
                    day: 'numeric',
                    year: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit',
                    hour12: true
                })
            };

            this.showNotification(notificationData);
            this.updateBalance();
            this.updateTransactionTable();

        } catch (error) {
            console.error('Error handling transaction:', error);
        }
    }

 // Update the showNotification method
showNotification(data) {
    const container = document.getElementById('notification-container');
    if (!container) {
        console.error('Notification container not found');
        return;
    }

    const notification = document.createElement('div');
    notification.className = `transaction-notification ${data.type.toLowerCase()}`;
    
    // Add notification content
    notification.innerHTML = `
        <div class="notification-content">
            <i class="fas fa-${data.type === 'INCOMING' ? 'arrow-down' : 'arrow-up'}" 
               style="color: ${data.type === 'INCOMING' ? '#48bb78' : '#4299e1'}">
            </i>
            <div class="notification-text">
                <strong>${data.type === 'INCOMING' ? 'Payment Received' : 'Payment Sent'}</strong>
                <p>${data.message}</p>
                <small>${data.timestamp}</small>
            </div>
            <button class="notification-close" aria-label="Close notification">&times;</button>
        </div>
    `;

    // Add to container
    container.appendChild(notification);
    
    // Trigger entrance animation
    requestAnimationFrame(() => {
        container.classList.add('notification-enter');
    });

    // Setup close button
    const closeBtn = notification.querySelector('.notification-close');
    closeBtn.addEventListener('click', () => {
        notification.classList.add('closing');
        setTimeout(() => {
            notification.remove();
            if (container.children.length === 0) {
                container.classList.remove('notification-enter');
            }
        }, 500); // Match animation duration
    });

    // Auto dismiss after 5 seconds
    setTimeout(() => {
        if (notification.parentElement) { // Check if notification still exists
            notification.classList.add('closing');
            setTimeout(() => {
                notification.remove();
                if (container.children.length === 0) {
                    container.classList.remove('notification-enter');
                }
            }, 500);
        }
    }, 5000);
}
checkNotificationSystem() {
    console.log('Notification system status:');
    console.log('Container exists:', !!document.getElementById('notification-container'));
    console.log('WebSocket connected:', this.stompClient?.connected);
    console.log('User ID:', this.userId);
    console.log('Username:', this.username);
}

    async requestNotificationPermission() {
        if (!('Notification' in window)) return 'denied';
        
        if (Notification.permission !== 'granted') {
            const permission = await Notification.requestPermission();
            return permission;
        }
        return Notification.permission;
    }

    showBrowserNotification(data) {
        const notification = new Notification(
            `${data.type === 'INCOMING' ? 'Payment Received' : 'Payment Sent'}`, 
            {
                body: data.message,
                icon: '/images/logo.png',
                badge: '/images/badge.png',
                vibrate: [200, 100, 200]
            }
        );

        notification.onclick = () => {
            window.focus();
            notification.close();
        };
    }

    setupNotificationClose(notification) {
        const closeBtn = notification.querySelector('.notification-close');
        closeBtn.onclick = () => {
            notification.style.animation = 'slideOutRight 0.5s';
            setTimeout(() => notification.remove(), 500);
        };

        setTimeout(() => {
            if (notification.parentNode) {
                notification.style.animation = 'slideOutRight 0.5s';
                setTimeout(() => notification.remove(), 500);
            }
        }, 5000);
    }

    formatAmount(amount) {
        return new Intl.NumberFormat('en-NG', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }).format(amount);
    }

    playNotificationSound() {
        const audio = new Audio('/static/sounds/notification.mp3');
        audio.volume = 0.5;
        audio.play().catch(e => console.log('Audio play failed:', e));
    }

    updateBalance() {
        fetch('/user/balance')
            .then(response => response.json())
            .then(data => {
                document.querySelectorAll('.Available_balance').forEach(element => {
                    element.textContent = `₦${this.formatAmount(data.balance)}`;
                });
            })
            .catch(error => console.error('Error updating balance:', error));
    }

    updateTransactionTable() {
        if (window.fetchTransactions) {
            window.fetchTransactions();
        }
    }

    onError(error) {
        console.error('WebSocket connection error:', error);
        this.scheduleReconnect();
    }

    scheduleReconnect() {
        this.reconnectAttempts++;
        const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
        setTimeout(() => this.connectWebSocket(), delay);
    }

    disconnect() {
        if (this.stompClient?.connected) {
            this.stompClient.disconnect();
        }
    }
}

// Run after DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    setTimeout(() => {
        if (notificationHandler) {
            notificationHandler.checkNotificationSystem();
            
            // Test notification
            notificationHandler.showNotification({
                type: 'INCOMING',
                message: 'Test notification',
                amount: 1000,
                timestamp: new Date().toLocaleString()
            });
        }
    }, 1000);
});

// Initialize notification handler
const notificationHandler = new NotificationHandler();

// Cleanup on page unload
window.addEventListener('beforeunload', () => {
    notificationHandler.disconnect();
});

export default notificationHandler;