package ra.notification;

import ra.common.*;
import ra.common.messaging.EventMessage;
import ra.common.messaging.MessageProducer;
import ra.common.network.ControlCommand;
import ra.common.notification.Subscription;
import ra.common.route.Route;
import ra.common.service.BaseService;
import ra.common.service.ServiceStatus;
import ra.common.service.ServiceStatusObserver;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Provides notifications of publishing events for subscribers.
 *
 * TODO: Replace callbacks with service calls to improve scalability and thread contention
 */
public class NotificationService extends BaseService {

    private static final Logger LOG = Logger.getLogger(NotificationService.class.getName());

    /**
     * To subscribe to EventMessages, send a SubscriptionRequest as a DocumentMessage to Service using
     * OPERATION_SUBSCRIBE as operation. SubscriptionRequest must specify EventMessage.Type and optionally a Filter.
     *
     * Filters available for each EventMessage.Type:
     *
     * EMAIL: Internal filtering automatic based on end user's owned DIDs.
     * EXCEPTION: Internal filtering automatically; Client exceptions can be subscribed to by Clients (not yet implemented).
     * ERROR: No filters supported
     * STATUS_SERVICE: String representing full name of Service class, e.g. ra.i2p.I2PService
     * STATUS_BUS: No filters supported
     * STATUS_CLIENT: No filters supported
     * STATUS_DID: Identity hash
     * TEXT: Can filter by name if provided. For I2P messages, the name is the sender's base64 encoded key.
     *
     */
    public static final String OPERATION_SUBSCRIBE = "SUBSCRIBE";
    public static final String OPERATION_UNSUBSCRIBE = "UNSUBSCRIBE";
    /**
     * To publish an EventMessage, ensure the Envelope contains one.
     */
    public static final String OPERATION_PUBLISH = "PUBLISH";

    private ExecutorService pool = Executors.newFixedThreadPool(4);

    // Type, Filter, Subscription Id, Subscription
    private Map<String, Map<String, Map<String, Subscription>>> subscriptions;

    public NotificationService() {
        super();
    }

    public NotificationService(MessageProducer producer, ServiceStatusObserver observer) {
        super(producer, observer);
    }

    @Override
    public void handleDocument(Envelope e) {
        Route r = e.getRoute();
        String operation = r.getOperation();
        switch(operation) {
            case OPERATION_SUBSCRIBE:{subscribe(e);break;}
            case OPERATION_UNSUBSCRIBE:{unsubscribe(e);break;}
            default: deadLetter(e);
        }
    }

    @Override
    public void handleEvent(Envelope e) {
        Route r = e.getRoute();
        String operation = r.getOperation();
        switch(operation) {
            case OPERATION_PUBLISH:{publish(e);break;}
            default: deadLetter(e);
        }
    }

    private void subscribe(Envelope e) {
        LOG.info("Received subscribe request...");
        String eventMessageType = (String)e.getValue("EventMessageType");
        LOG.info("Subscription for type: " + eventMessageType);
        Map<String, Map<String,Subscription>> s = subscriptions.get(eventMessageType);
        String filter = (String)e.getValue("Filter");
        String clientId = (String)e.getValue("ClientId");
        String service = (String)e.getValue("Service");
        String operation = (String)e.getValue("Operation");
        if (filter == null) {
            LOG.info("With no filters.");
            Subscription sub = new Subscription(EventMessage.Type.valueOf(eventMessageType), filter, service, operation);
            sub.setClientId(clientId);
            s.get("|").put(sub.getId(), sub);
        } else {
            LOG.info("With filter: " + filter);
            if (s.get(filter) == null)
                s.put(filter, new HashMap<>());
            Subscription sub = new Subscription(EventMessage.Type.valueOf(eventMessageType), filter, service, operation);
            sub.setClientId(clientId);
            s.get(filter).put(sub.getId(), sub);
        }
        LOG.info("Subscription added.");
    }

    private void unsubscribe(Envelope e) {
        LOG.info("Received unsubscribe request...");
        String subId = (String)e.getValue("Subscription.Id");
        String eventMessageType = (String)e.getValue("EventMessageType");
        Map<String, Map<String,Subscription>> s = subscriptions.get(eventMessageType);
        String filter = (String)e.getValue("Filter");
        if(filter == null) {
            s.get("|").remove(subId);
        } else {
            s.get(filter).remove(subId);
        }
        LOG.info("Subscription removed.");
    }

    private void publish(final Envelope e) {
        LOG.info("Received publish request...");
        EventMessage m = (EventMessage)e.getMessage();
        LOG.info("For type: "+m.getType());
        Map<String, Map<String,Subscription>> s = subscriptions.get(m.getType());
        if(s == null || s.size() == 0) {
            LOG.info("No subscriptions for type: "+m.getType());
            return;
        }
        final Map<String,Subscription> subs = s.get("|");
        if(subs == null || subs.size() == 0) {
            LOG.info("No subscriptions without filters.");
        } else {
            LOG.info("Notify all "+subs.size()+" unfiltered subscriptions.");
            for(final Subscription sub: subs.values()) {
                if(sub.getClientId()!=null) {
                    e.setClient(sub.getClientId());
                    e.setCommandPath(ControlCommand.Notify.name());
                }
                e.addRoute(sub.getService(), sub.getOperation());
                e.ratchet();
                send(e);
            }
        }
        if(m.getName()!=null) {
            LOG.info("With name to filter on: " + m.getName());
            final Map<String, Subscription> filteredSubs = s.get(m.getName());
            if (filteredSubs == null || filteredSubs.size() == 0) {
                LOG.info("No subscriptions for filter: " + m.getName());
            } else {
                LOG.info("Notify all " + filteredSubs.size() + " filtered subscriptions.");
                for (final Subscription sub : filteredSubs.values()) {
                    if(sub.getClientId()!=null) {
                        e.setClient(sub.getClientId());
                        e.setCommandPath(ControlCommand.Notify.name());
                    }
                    e.addRoute(sub.getService(), sub.getOperation());
                    e.ratchet();
                    send(e);
                }
            }
        }
    }

    private Map<String, Map<String,Subscription>> buildNewMap() {
        Map<String,Subscription> l = new HashMap<>();
        Map<String, Map<String,Subscription>> m = new HashMap<>();
        m.put("|",l);
        return m;
    }

    @Override
    public boolean start(Properties properties) {
        super.start(properties);
        LOG.info("Starting...");
        updateStatus(ServiceStatus.STARTING);

        subscriptions = new HashMap<>();
        // For each EventMessage.Type, set a HashMap<String,Subscription>
        // and add a null filtered list for Subscriptions with no filters.

        subscriptions.put(EventMessage.Type.EXCEPTION.name(), buildNewMap());
        subscriptions.put(EventMessage.Type.ERROR.name(), buildNewMap());
        subscriptions.put(EventMessage.Type.BUS_STATUS.name(), buildNewMap());
        subscriptions.put(EventMessage.Type.PEER_STATUS.name(), buildNewMap());
        subscriptions.put(EventMessage.Type.NETWORK_STATE_UPDATE.name(), buildNewMap());
        subscriptions.put(EventMessage.Type.SERVICE_STATUS.name(), buildNewMap());

        updateStatus(ServiceStatus.RUNNING);
        LOG.info("Started.");
        return true;
    }

    @Override
    public boolean shutdown() {
        super.shutdown();
        LOG.info("Shutting down....");
        updateStatus(ServiceStatus.SHUTTING_DOWN);

        updateStatus(ServiceStatus.SHUTDOWN);
        LOG.info("Shutdown");
        return true;
    }

    @Override
    public boolean gracefulShutdown() {
        // TODO:
        return shutdown();
    }
}
