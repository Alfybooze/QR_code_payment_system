class NotificationHandler {
    constructor() {
        this.stompClient = null;
        this.userId = document.querySelector('meta[name="user-id"]')?.content;
        this.username = document.querySelector('meta[name="username"]')?.content;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.connectWebSocket();
    }

    connectWebSocket() {
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            console.error('Max reconnection attempts reached');
            return;
        }

        const socket = new SockJS('/ws');
        this.stompClient = Stomp.over(socket);
        this.stompClient.debug = null; // Disable debug messages

        this.stompClient.connect({}, 
            frame => this.onConnect(frame),
            error => this.onError(error)
        );
    }

    onConnect(frame) {
        console.log('Connected to WebSocket');
        this.reconnectAttempts = 0;

        if (this.userId) {
            const currentUserId = parseInt(this.userId);
            
            // Subscribe to user-specific queue
            this.stompClient.subscribe(`/queue/notifications`, 
                message => {
                    const notification = JSON.parse(message.body);
                    console.log('Received user notification:', notification);
                    this.handleNotification(notification);
                }
            );

            // Subscribe to broadcast topic
            this.stompClient.subscribe('/topic/transactions', 
                message => {
                    const notification = JSON.parse(message.body);
                    console.log('Received broadcast:', notification);
                    // Only handle if relevant to current user
                    if (notification.senderId === currentUserId || 
                        notification.receiverId === currentUserId) {
                        this.handleTransaction(notification);
                    }
                }
            );
        }
    }

    handleNotification(notification) {
        const currentUserId = parseInt(this.userId);
        const receiverId = parseInt(notification.receiverId);
        const senderId = parseInt(notification.senderId);
        
        console.log('Processing notification:', {
            currentUserId,
            receiverId,
            senderId,
            type: notification.type
        });

        // Determine if current user is receiver or sender
        const isReceiver = currentUserId === receiverId;
        const isSender = currentUserId === senderId;

        if (!isReceiver && !isSender) {
            console.log('Notification not relevant to current user');
            return;
        }

        // Format amount with Naira symbol
        const formattedAmount = new Intl.NumberFormat('en-NG', {
            style: 'currency',
            currency: 'NGN'
        }).format(notification.amount);

        // Create appropriate message
        const message = isReceiver
            ? `${notification.senderName} sent ${formattedAmount} to you`
            : `You sent ${formattedAmount} to ${notification.receiverName}`;

        this.showNotification({
            ...notification,
            message,
            type: isReceiver ? 'INCOMING' : 'OUTGOING'
        });

        // Update UI if needed
        this.updateUI(notification);
    }

    showNotification(data) {
        let container = document.getElementById('notification-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'notification-container';
            container.className = 'notification-container';
            document.body.appendChild(container);
        }

        const notification = document.createElement('div');
        notification.className = 'transaction-notification';
        notification.innerHTML = `
            <div class="notification-content">
                <i class="fas fa-${data.type === 'INCOMING' ? 'download text-success' : 'upload text-primary'}"></i>
                <div class="notification-text">
                    <strong>${data.type === 'INCOMING' ? 'Payment Received' : 'Payment Sent'}</strong>
                    <p>${data.message}</p>
                    <small class="text-muted">${data.timestamp}</small>
                </div>
                <button class="notification-close">&times;</button>
            </div>
        `;

        // Add slide-in animation
        notification.style.animation = 'slideInDown 0.5s ease-out';
        container.insertBefore(notification, container.firstChild);

        const closeBtn = notification.querySelector('.notification-close');
        closeBtn.onclick = () => this.removeNotification(notification);

        // Auto remove after 5 seconds
        setTimeout(() => this.removeNotification(notification), 5000);

        this.playNotificationSound();
    }

    removeNotification(notification) {
        if (!notification.parentNode) return;
        notification.classList.add('removing');
        setTimeout(() => notification.remove(), 500);
    }

    updateUI(notification) {
        // Update balance
        this.updateBalance();
        // Update transaction list
        this.updateTransactionTable(notification);
    }

    updateBalance() {
        fetch('/user/balance')
            .then(response => response.json())
            .then(data => {
                const balanceElements = document.querySelectorAll('.Available_balance');
                balanceElements.forEach(element => {
                    element.textContent = data.formattedBalance;
                });
            })
            .catch(error => console.error('Error updating balance:', error));
    }

    onError(error) {
        console.error('WebSocket connection error:', error);
        this.reconnectAttempts++;
        setTimeout(() => this.connectWebSocket(), 5000);
    }

    disconnect() {
        if (this.stompClient?.connected) {
            this.stompClient.disconnect();
        }
    }
}

// Initialize notification handler
const notificationHandler = new NotificationHandler();

// Cleanup on page unload
window.addEventListener('beforeunload', () => {
    notificationHandler.disconnect();
});

export default notificationHandler;