# Axon Framework 5 Multitenancy Extension Migration Specification

## Overview
This document contains the specifications for migrating the multitenancy extension from Axon Framework 4 to Axon Framework 5.

## Key API Changes in Axon Framework 5

### 1. CommandBus Interface
**Location:** `messaging/src/main/java/org/axonframework/messaging/commandhandling/CommandBus.java`

```java
// AF5 CommandBus signature
public interface CommandBus extends CommandHandlerRegistry<CommandBus>, DescribableComponent {
    CompletableFuture<CommandResultMessage> dispatch(@Nonnull CommandMessage command,
                                                     @Nullable ProcessingContext processingContext);
}
```

**Key differences from AF4:**
- Single `dispatch` method instead of two (no callback variant)
- Returns `CompletableFuture<CommandResultMessage>` instead of void
- Takes `ProcessingContext` (nullable) instead of using `UnitOfWork`
- `subscribe` now uses `QualifiedName` instead of `String`

### 2. ProcessingContext (replaces UnitOfWork)
**Location:** `messaging/src/main/java/org/axonframework/messaging/core/unitofwork/ProcessingContext.java`

- Extends `ProcessingLifecycle`, `ApplicationContext`, `Context`
- Provides mutable resource management via `ResourceKey<T>`
- Methods: `putResource`, `computeResourceIfAbsent`, `removeResource`, etc.

### 3. Interceptors
**Location:** `messaging/src/main/java/org/axonframework/messaging/core/`

**MessageDispatchInterceptor:**
```java
MessageStream<?> interceptOnDispatch(@Nonnull M message,
                                     @Nullable ProcessingContext context,
                                     @Nonnull MessageDispatchInterceptorChain<M> interceptorChain);
```

**MessageHandlerInterceptor:**
```java
MessageStream<?> interceptOnHandle(@Nonnull M message,
                                   @Nonnull ProcessingContext context,
                                   @Nonnull MessageHandlerInterceptorChain<M> interceptorChain);
```

### 4. CommandHandlerRegistry
**Location:** `messaging/src/main/java/org/axonframework/messaging/commandhandling/CommandHandlerRegistry.java`

```java
S subscribe(@Nonnull QualifiedName name, @Nonnull CommandHandler commandHandler);
```

Uses `QualifiedName` instead of `String` for command names.

### 5. Message Types
- `org.axonframework.messaging.core.Message` (was `org.axonframework.messaging.Message`)
- `Metadata` is now `Map<String, String>` (only string values)

### 6. Package Changes
- `org.axonframework.commandhandling` → `org.axonframework.messaging.commandhandling`
- `org.axonframework.messaging.Message` → `org.axonframework.messaging.core.Message`
- `javax.annotation` → `jakarta.annotation`

## Core Components to Migrate

### Phase 1: Foundation Layer (Almost Portable)

1. **TenantDescriptor** - No changes needed, just update copyright year
2. **TenantProvider** - Update `Registration` import if needed
3. **MultiTenantAwareComponent** - Update `Registration` import if needed
4. **TargetTenantResolver** - Update `Message` import: `org.axonframework.messaging.core.Message`
5. **NoSuchTenantException** - No changes needed
6. **TenantConnectPredicate** - No changes needed

### Phase 2: MultiTenantCommandBus

**Current AF4 Implementation:**
- Implements `CommandBus`, `MultiTenantAwareComponent`, interceptor support interfaces
- Uses `dispatch(CommandMessage<C> command)` and `dispatch(CommandMessage<C> command, CommandCallback<C,R> callback)`
- Uses `subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler)`

**Required AF5 Changes:**
1. Single `dispatch(CommandMessage, ProcessingContext)` returning `CompletableFuture<CommandResultMessage>`
2. `subscribe(QualifiedName, CommandHandler)` - fluent, returns `this`
3. Update all imports to AF5 packages
4. Update interceptor support for new signatures

### Tenant Resolution Pattern

The tenant can be stored/retrieved from:
1. Message metadata: `message.metadata().get("tenantId")`
2. ProcessingContext resources: `context.getResource(TENANT_KEY)`

Recommended: Use message metadata as primary, with ProcessingContext as fallback.

## Directory Structure (Target)

```
multitenancy/src/main/java/org/axonframework/extensions/multitenancy/
├── components/
│   ├── TenantDescriptor.java
│   ├── TenantProvider.java
│   ├── MultiTenantAwareComponent.java
│   ├── TargetTenantResolver.java
│   ├── NoSuchTenantException.java
│   ├── TenantConnectPredicate.java
│   └── commandhandling/
│       ├── MultiTenantCommandBus.java
│       └── TenantCommandSegmentFactory.java
```

## Build Configuration

Update `pom.xml`:
- Java 21 minimum
- Axon Framework 5.x dependency
- Spring Boot 3.x (for autoconfigure module)
- Jakarta annotations

## Testing Strategy

1. Unit tests for each component
2. Use JUnit 5 + Mockito
3. Test tenant registration, resolution, dispatch routing
4. Test interceptor propagation to tenant segments
