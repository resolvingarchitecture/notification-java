# Notification Service
Provides notifications of publishing events for subscribers.

## Subscriptions
To subscribe to EventMessages, send a SubscriptionRequest as a DocumentMessage to Service using OPERATION_SUBSCRIBE as operation.
SubscriptionRequest must specify EventMessage.Type and optionally a Filter.

## Filters
Filters available for each EventMessage.Type:

* EMAIL: Internal filtering automatic based on end user's owned DIDs.
* EXCEPTION: Internal filtering automatically; Client exceptions can be subscribed to by Clients (not yet implemented).
* ERROR: No filters supported
* STATUS_SERVICE: String representing full name of Service class, e.g. ra.i2p.I2PService
* STATUS_BUS: No filters supported
* TEXT: Can filter by name if provided.

## Roadmap
* Currently supports synchronous callback notifications; needs to support asynchronous updates by propagating events to registered service operations.
