package ra.notification;

import ra.common.messaging.EventMessage;
import ra.common.notification.Subscription;

/**
 * Request a subscription to future publications providing an optional filter.
 */
public class SubscriptionRequest {

    private EventMessage.Type type;
    private String filter;
    private Subscription subscription;

    public SubscriptionRequest(EventMessage.Type type, Subscription subscription) {
        this.type = type;
        this.subscription = subscription;
    }

    public SubscriptionRequest(EventMessage.Type type, String filter, Subscription subscription) {
        this.type = type;
        this.filter = filter;
        this.subscription = subscription;
    }

    public EventMessage.Type getType() {
        return type;
    }

    public String getFilter() {
        return filter;
    }

    public Subscription getSubscription() {
        return subscription;
    }
}
