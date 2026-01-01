# Axon Framework 5 Multitenancy Extension Migration Guide

This document provides comprehensive guidance for completing the migration of the multitenancy extension from Axon Framework 4 to Axon Framework 5.

## Current Status

**Branch:** `axon-5`
**Date:** 2025-12-31
**Build Status:** ✅ Compiling (15 source files)

### Commits Made

```
6c0a2d2 Add MultiTenantEventProcessorPredicate for configuration
f4d2272 Migrate MultiTenantEventProcessor to Axon Framework 5
9780463 Migrate MultiTenantEventStore to Axon Framework 5
a12bc86 Migrate MultiTenantQueryBus to Axon Framework 5
6991c07 Migrate core multitenancy components to Axon Framework 5
```

---

## What Has Been Completed

### 1. Core Foundation Layer (6 files)
**Location:** `multitenancy/src/main/java/org/axonframework/extensions/multitenancy/components/`

| File | Changes Made |
|------|-------------|
| `TenantDescriptor.java` | Updated copyright, @since to 5.0.0 |
| `TenantProvider.java` | Updated copyright, @since to 5.0.0 |
| `MultiTenantAwareComponent.java` | Updated copyright, @since to 5.0.0 |
| `TargetTenantResolver.java` | Changed `Message<?>` to `Message` (AF5 Message has no type params) |
| `NoSuchTenantException.java` | Updated copyright, @since to 5.0.0 |
| `TenantConnectPredicate.java` | Updated copyright, @since to 5.0.0 |

### 2. MultiTenantCommandBus (2 files)
**Location:** `components/commandhandeling/`

| File | Key Changes |
|------|-------------|
| `MultiTenantCommandBus.java` | Complete rewrite for AF5 API |
| `TenantCommandSegmentFactory.java` | Updated imports |

**API Changes Applied:**
- `dispatch(CommandMessage)` → `dispatch(CommandMessage, ProcessingContext)` returning `CompletableFuture<CommandResultMessage>`
- `subscribe(String, MessageHandler)` → `subscribe(QualifiedName, CommandHandler)` returning `this`
- Removed callback-based dispatch
- Added `describeTo(ComponentDescriptor)` for DescribableComponent
- Removed interceptor support (deferred)

### 3. MultiTenantQueryBus (2 files)
**Location:** `components/queryhandeling/`

| File | Key Changes |
|------|-------------|
| `MultiTenantQueryBus.java` | Complete rewrite for AF5 API |
| `TenantQuerySegmentFactory.java` | Updated imports |

**API Changes Applied:**
- `query()` returns `MessageStream<QueryResponseMessage>` instead of `CompletableFuture`
- Removed `scatterGather()` and `streamingQuery()` (replaced by MessageStream)
- Added subscription query methods with new signatures
- Added `emitUpdate()`, `completeSubscriptions()`, `completeSubscriptionsExceptionally()` (from QueryUpdateEmitter)
- `QueryMessage` no longer takes type parameters
- Removed interceptor support (deferred)

### 4. MultiTenantEventStore (2 files)
**Location:** `components/eventstore/`

| File | Key Changes |
|------|-------------|
| `MultiTenantEventStore.java` | Complete rewrite - aggregate-centric methods removed |
| `TenantEventSegmentFactory.java` | Updated imports |

**API Changes Applied:**
- `publish()` takes `ProcessingContext`, returns `CompletableFuture<Void>`
- `subscribe()` takes `BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>>`
- `open(StreamingCondition, ProcessingContext)` replaces `openStream(TrackingToken)`
- Added `transaction(ProcessingContext)` for EventStoreTransaction access
- **REMOVED:** `readEvents(aggregateIdentifier)`, `storeSnapshot()` - not in AF5
- Token operations throw `UnsupportedOperationException` (multi-tenant aggregation not meaningful)

### 5. MultiTenantEventProcessor (2 files)
**Location:** `components/eventhandeling/`

| File | Key Changes |
|------|-------------|
| `MultiTenantEventProcessor.java` | Updated for AF5 EventProcessor interface |
| `TenantEventProcessorSegmentFactory.java` | Updated imports |

**API Changes Applied:**
- `getName()` → `name()`
- `shutDown()` → `shutdown()`
- `start()` and `shutdown()` now return `CompletableFuture<Void>`
- Removed `@StartHandler` and `@ShutdownHandler` lifecycle annotations
- Added `describeTo(ComponentDescriptor)`
- Removed interceptor support (deferred)

### 6. Configuration (1 file)
**Location:** `configuration/`

| File | Status |
|------|--------|
| `MultiTenantEventProcessorPredicate.java` | ✅ Migrated (simple predicate, no changes needed) |

---

## What Still Needs To Be Done

### Priority 1: Tests
**Location to create:** `multitenancy/src/test/java/`

All migrated components need unit tests:
- `TenantDescriptorTest`
- `MultiTenantCommandBusTest`
- `MultiTenantQueryBusTest`
- `MultiTenantEventStoreTest`
- `MultiTenantEventProcessorTest`

Use the existing AF4 tests in `pending_migration/` as reference but update for AF5 APIs.

### Priority 2: Interceptor Support
**Files to create/update:**
- `MultiTenantDispatchInterceptorSupport.java` (in pending_migration/)
- `MultiTenantHandlerInterceptorSupport.java` (in pending_migration/)

These were removed to simplify initial migration. Need to be reimplemented with AF5 interceptor patterns:

**AF5 Interceptor Signatures:**
```java
// MessageDispatchInterceptor
MessageStream<?> interceptOnDispatch(M message, ProcessingContext context, MessageDispatchInterceptorChain<M> chain);

// MessageHandlerInterceptor
MessageStream<?> interceptOnHandle(M message, ProcessingContext context, MessageHandlerInterceptorChain<M> chain);
```

### Priority 3: Spring Boot Autoconfigure Module
**Location:** `multitenancy-spring-boot-autoconfigure/`

This module needs complete review and update for:
- Spring Boot 3.x
- AF5 configuration APIs
- Jakarta namespace (javax → jakarta)

Key files to migrate:
- `MultiTenancyAutoConfiguration.java`
- `MultiTenancyAxonServerAutoConfiguration.java`
- `AxonServerTenantProvider.java`
- `MultiTenantDataSourceManager.java`

### Priority 4: Dead Letter Queue Components
**Files in pending_migration/deadletterqueue/:**
- `MultiTenantDeadLetterQueue.java`
- `MultiTenantDeadLetterProcessor.java`
- `MultiTenantDeadLetterQueueFactory.java`

Check if AF5 has DLQ support and update accordingly.

### Priority 5: Event Scheduler
**Files in pending_migration/scheduling/:**
- `MultiTenantEventScheduler.java`
- `TenantEventSchedulerSegmentFactory.java`

Check AF5 EventScheduler API and update.

---

## Deferred Changes and Reasons

### 1. MultiTenantEventProcessingModule
**Reason:** Extends AF4's `EventProcessingModule` which is completely redesigned in AF5.
**Location:** `pending_migration/configuration/MultiTenantEventProcessingModule.java`
**Recommendation:** Redesign as part of Spring Boot autoconfigure module instead of trying to port the module-based approach.

### 2. MultiTenantStreamableMessageSourceProvider
**Reason:** Depends on AF4's `Configuration` class and `TrackedEventMessage<?>` patterns.
**Location:** `pending_migration/configuration/MultiTenantStreamableMessageSourceProvider.java`
**Recommendation:** May need complete redesign for AF5's streaming architecture.

### 3. TenantWrappedTransactionManager
**Reason:** Uses ThreadLocal patterns that may conflict with AF5's async-native approach.
**Location:** `pending_migration/TenantWrappedTransactionManager.java`
**Recommendation:** Review AF5's transaction handling approach first.

### 4. Interceptor Support Interfaces
**Reason:** AF5 interceptors have completely different signatures (return MessageStream, take chain parameter).
**Location:** `pending_migration/MultiTenantDispatchInterceptorSupport.java`, `pending_migration/MultiTenantHandlerInterceptorSupport.java`
**Recommendation:** Implement after core components are tested and working.

---

## Critical AF5 API Changes Reference

### 1. Message Interface
```java
// AF4
public interface Message<T> {
    T getPayload();
    MetaData getMetaData();
}

// AF5
public interface Message {  // No type parameter!
    Object payload();       // Method renamed
    Metadata metadata();    // Type renamed, returns Map<String, String> only
}
```
**Impact:** All `Message<?>` become `Message`. Metadata values must be strings.

### 2. ProcessingContext (replaces UnitOfWork)
```java
// AF4
CurrentUnitOfWork.get().getMessage()

// AF5
ProcessingContext context;  // Passed as parameter, not accessed statically
Message.fromContext(context);  // Get message from context
```
**Impact:** All static UnitOfWork access must be replaced with ProcessingContext parameters.

### 3. CommandBus
```java
// AF4
void dispatch(CommandMessage<?> command);
void dispatch(CommandMessage<?> command, CommandCallback<?,?> callback);
Registration subscribe(String commandName, MessageHandler<?> handler);

// AF5
CompletableFuture<CommandResultMessage> dispatch(CommandMessage command, ProcessingContext context);
CommandBus subscribe(QualifiedName name, CommandHandler handler);  // Fluent
```

### 4. QueryBus
```java
// AF4
CompletableFuture<QueryResponseMessage<R>> query(QueryMessage<Q,R> query);
Stream<QueryResponseMessage<R>> scatterGather(QueryMessage<Q,R> query, long timeout, TimeUnit unit);

// AF5
MessageStream<QueryResponseMessage> query(QueryMessage query, ProcessingContext context);
// scatterGather removed - use MessageStream instead
```

### 5. EventStore
```java
// AF4
void publish(List<? extends EventMessage<?>> events);
DomainEventStream readEvents(String aggregateIdentifier);
void storeSnapshot(DomainEventMessage<?> snapshot);
BlockingStream<TrackedEventMessage<?>> openStream(TrackingToken token);

// AF5
CompletableFuture<Void> publish(ProcessingContext context, List<EventMessage> events);
// readEvents REMOVED - no aggregate-centric methods
// storeSnapshot REMOVED
MessageStream<EventMessage> open(StreamingCondition condition, ProcessingContext context);
EventStoreTransaction transaction(ProcessingContext context);  // NEW
```

### 6. EventProcessor
```java
// AF4
String getName();
void start();
void shutDown();
@StartHandler void start();  // Lifecycle annotation

// AF5
String name();
CompletableFuture<Void> start();
CompletableFuture<Void> shutdown();  // Note: different spelling
// No lifecycle annotations
```

### 7. Interceptors
```java
// AF4
BiFunction<Integer, CommandMessage<?>, CommandMessage<?>> registerDispatchInterceptor(...)

// AF5
MessageStream<?> interceptOnDispatch(M message, ProcessingContext context, MessageDispatchInterceptorChain<M> chain);
MessageStream<?> interceptOnHandle(M message, ProcessingContext context, MessageHandlerInterceptorChain<M> chain);
```

### 8. Package Changes
| AF4 Package | AF5 Package |
|-------------|-------------|
| `org.axonframework.commandhandling` | `org.axonframework.messaging.commandhandling` |
| `org.axonframework.queryhandling` | `org.axonframework.messaging.queryhandling` |
| `org.axonframework.eventhandling` | `org.axonframework.messaging.eventhandling` |
| `org.axonframework.eventhandling.EventProcessor` | `org.axonframework.messaging.eventhandling.processing.EventProcessor` |
| `org.axonframework.eventsourcing.eventstore` | `org.axonframework.eventsourcing.eventstore` (same) |
| `org.axonframework.messaging.Message` | `org.axonframework.messaging.core.Message` |
| `javax.annotation.*` | `jakarta.annotation.*` |

---

## Wisdom for Tricky Migrations

### 1. Generic Type Parameters Are Gone
AF5 removed type parameters from `Message`, `CommandMessage`, `QueryMessage`, `EventMessage`. This simplifies signatures but means you lose compile-time type safety. Update all `<?>` wildcards.

### 2. Everything Returns CompletableFuture or MessageStream
AF5 is async-native. Methods that were `void` now return `CompletableFuture<Void>`. Methods that returned single values now return `MessageStream`. Handle these properly in multi-tenant wrappers.

### 3. No More Static ThreadLocal Access
AF4's `CurrentUnitOfWork.get()` pattern is gone. AF5 passes `ProcessingContext` as a parameter. Multi-tenant resolution must work with the context parameter, not static access.

### 4. Fluent Builder Pattern for Registration
AF5 subscribe methods return `this` for fluent chaining instead of returning `Registration`. Unsubscription is handled differently.

### 5. DescribableComponent Is Required
All infrastructure components must implement `describeTo(ComponentDescriptor)`. This is used for introspection and monitoring.

### 6. Aggregate-Centric Methods Removed from EventStore
AF5's Dynamic Consistency Boundary (DCB) pattern means no more `readEvents(aggregateId)` or `storeSnapshot()`. Event sourcing works differently. The `MultiTenantEventStore` had to remove these methods entirely.

### 7. Configuration System Completely Different
AF4's `Configuration`, `Configurer`, `EventProcessingModule` are gone. AF5 uses `ApplicationConfigurer`, `MessagingConfigurer`, etc. The `MultiTenantEventProcessingModule` cannot be directly ported.

---

## Build and Test Commands

```bash
# Working directory
cd /Users/theoem/Development/AxonFramework/extensions/extension-multitenancy

# Compile core module
mvn compile -pl multitenancy -DskipTests

# Run tests (when available)
mvn test -pl multitenancy

# Install AF5 dependencies (run from main AF repo if needed)
cd /Users/theoem/Development/AxonFramework
mvn install -DskipTests -pl messaging,common,eventsourcing -am

# Full build
cd /Users/theoem/Development/AxonFramework/extensions/extension-multitenancy
mvn clean install -DskipTests
```

---

## Files Reference

### Currently in Source (15 files)
```
multitenancy/src/main/java/org/axonframework/extensions/multitenancy/
├── components/
│   ├── MultiTenantAwareComponent.java
│   ├── NoSuchTenantException.java
│   ├── TargetTenantResolver.java
│   ├── TenantConnectPredicate.java
│   ├── TenantDescriptor.java
│   ├── TenantProvider.java
│   ├── commandhandeling/
│   │   ├── MultiTenantCommandBus.java
│   │   └── TenantCommandSegmentFactory.java
│   ├── queryhandeling/
│   │   ├── MultiTenantQueryBus.java
│   │   └── TenantQuerySegmentFactory.java
│   ├── eventstore/
│   │   ├── MultiTenantEventStore.java
│   │   └── TenantEventSegmentFactory.java
│   └── eventhandeling/
│       ├── MultiTenantEventProcessor.java
│       └── TenantEventProcessorSegmentFactory.java
└── configuration/
    └── MultiTenantEventProcessorPredicate.java
```

### Pending Migration (reference only)
```
pending_migration/
├── MultiTenantDispatchInterceptorSupport.java
├── MultiTenantHandlerInterceptorSupport.java
├── TenantEventProcessorControlSegmentFactory.java
├── TenantWrappedTransactionManager.java
├── configuration/
│   ├── MultiTenantEventProcessingModule.java
│   ├── MultiTenantEventProcessorPredicate.java
│   └── MultiTenantStreamableMessageSourceProvider.java
├── deadletterqueue/
│   ├── MultiTenantDeadLetterProcessor.java
│   ├── MultiTenantDeadLetterQueue.java
│   └── MultiTenantDeadLetterQueueFactory.java
├── eventhandeling/
│   ├── MultiTenantEventProcessor.java
│   └── TenantEventProcessorSegmentFactory.java
├── eventstore/
│   ├── MultiTenantEventStore.java
│   ├── MultiTenantSubscribableMessageSource.java
│   └── TenantEventSegmentFactory.java
├── queryhandeling/
│   ├── MultiTenantQueryBus.java
│   ├── MultiTenantQueryUpdateEmitter.java
│   ├── TenantQuerySegmentFactory.java
│   └── TenantQueryUpdateEmitterSegmentFactory.java
└── scheduling/
    ├── MultiTenantEventScheduler.java
    └── TenantEventSchedulerSegmentFactory.java
```

---

## Contact / Resources

- **Axon Framework 5 Docs:** https://docs.axoniq.io/
- **AF5 Source:** `/Users/theoem/Development/AxonFramework/`
- **Original AF4 Extension Docs:** https://docs.axoniq.io/multitenancy-extension-reference/
