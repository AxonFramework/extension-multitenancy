# Axon Framework - Multitenancy Extension

Axon Framework is a framework for building evolutionary, event-driven microservice systems,
based on the principles of Domain Driven Design, Command-Query Responsibility Segregation (CQRS) and Event Sourcing.

As such it provides you the necessary building blocks to follow these principles.
Building blocks like Aggregate factories and Repositories, Command, Event and Query Buses and an Event Store.
The framework provides sensible defaults for all of these components out of the box.

This set up helps you create a well structured application without having to bother with the infrastructure.
The main focus can thus become your business functionality.

#Introduction

Axon Framework Multitenancy Extension provides your application ability to serve multiple tenants (event-stores) at once.
Multi-tenancy is important in cloud computing and this extension will provide ability to connect to tenants dynamically, physical separate tenants data, scale tenants independently...

### Requirements

Currently, following requirements needs to be meet for extension to work:
- Use **Spring Framework** together with **Axon Framework 4.6+**
- Use **Axon Server EE 4.6+** or Axon Cloud as event store
- This is not hard requirement but if you wish to enable multitenancy on projection side, only SQL databases are supported out-of-the box


### Configuration

Minimal configuration is needed to get extension up and running.


#### Static tenants configuration

If you know list of contexts that you want your application to connect in advanced configure them coma separated in `application.properties` via following properties:
`axon.axonserver.contexts=tenant-context-1,tenant-context-2,tenant-context-3`

#### Dynamic tenants configuration

If you don't know tenants in advance and you plan to create them in runtime, you can define a predicate which will tell application to which contexts to connect to in runtime:

    @Bean
    public TenantConnectPredicate tenantFilterPredicate() {
        return context -> context.tenantId().startsWith("tenant-");
    }

Note that in this case you need to remove `axon.axonserver.contexts` property.

#### Route message to specific tenant

By default, to route message to specific tenant you need to tag initial message that enters your system with metadata .
This is done with meta-data helper, and you need to add tenant name to metadata with key `TenantConfiguration.TENANT_CORRELATION_KEY`.

`message.andMetaData(Collections.singletonMap(TENANT_CORRELATION_KEY, "tenant-context-1")`

Metadata needs to be added only to initial message that enters your system. Any message that is produced by consequence of initial message will have this metadata copied automatically using to `CorrelationProvider`.

#### Routing message to specific tenant - custom resolver

If you wish to disable default meta-data based routing define following property:

`axon.multi-tenancy.use-metadata-helper=false`

And define custom tenant resolver bean. For example following imaginary bean can use message payload to route message to specific tenant:

    @Bean
    public TargetTenantResolver<Message<?>> customTargetTenantResolver() {
        return (message, tenants) ->
                TenantDescriptor.tenantWithId(
                        message.getPayload().getField("tenantName")
                );
    }

First lambda parameter `message` represents message to be routed, while second parameter `tenants` represents list of currently registered tenants, if you wish to use is to route only to one of connected tenants.


#### Multi-tenant projections

If you wish to use distinct database to store projections and token store for each tenant, configure following bean:

    @Bean
    public Function<TenantDescriptor, DataSourceProperties> tenantDataSourceResolver() {
        return tenant -> {
            DataSourceProperties properties = new DataSourceProperties();
            properties.setUrl("jdbc:postgresql://localhost:5432/"+tenant.tenantId());
            properties.setDriverClassName("org.postgresql.Driver");
            properties.setUsername("postgres");
            properties.setPassword("postgres");
            return properties;
        };
    }

Note that this works by using JPA multi-tenancy support, that means only SQL Databases are supported out of the box.
If you wish to implement multi-tenancy for a different type of databases (e.g. NoSQL) make sure that your projection database supports multi-tenancy.
While in transaction you may find out which tenant owns transaction by calling:` TenantWrappedTransactionManager.getCurrentTenant()`.

For more hints how to enable multi-tenancy for NoSQL databases check on how JPA SQL version is [implemented](MultiTenantDataSourceManager.java)

Important: In this case Liquibase or Flyway will not be able to initialise schemas for dynamic data sources. Any datasource that you use needs to have pre-initialized schema.

#### Query Update Emitter

In order to correctly resolve right query update emitter inject update emitter in following style:

    @EventHandler
    public void on(Event event, QueryUpdateEmitter queryUpdateEmitter) {
      //queryUpdateEmitter will route updates to same tenant as event will be
      ...
    }


#### Resetting projections

Resetting projections works a bit different because you have instanced of each event processor group for each tenant.

Reset specific tenant event processor group:

    TrackingEventProcessor trackingEventProcessor = configuration.eventProcessingConfiguration()
                                                                     .eventProcessor("com.demo.query-ep@tenant-context-1",
                                                                                     TrackingEventProcessor.class)
                                                                     .get();

Name of each event processor is: {even processor name}@{tenant name}

Access all tenant event processors by retrieving `MultiTenantEventProcessor` only.
`MultiTenantEventProcessor` acts as a proxy Event Processor that references all tenant event processors.

### Supported multi-tenant components

Currently, supported multi-tenants components are as follows:

- <span style="color:green">MultiTenantCommandBus</span>
- <span style="color:green">MultiTenantEventProcessor</span>
- <span style="color:green">MultiTenantEventStore</span>
- <span style="color:green">MultiTenantQueryBus</span>
- <span style="color:green">MultiTenantQueryUpdateEmitter</span>
- <span style="color:green">MultiTenantEventProcessorControlService</span>
- <span style="color:green">MultiTenantDataSourceManager</span>

Not yet supported multi-tenants components are:

- <span style="color:red">MultitenantDeadlineManager</span>
- <span style="color:red">MultitenantEventScheduler</span>


### Disable extension

By default, extension is automatically enabled.
If you wish to disable extension without removing extension use following property.

`axon.multi-tenancy.enabled=false`


## Receiving help

Are you having trouble using the extension?
We'd like to help you out the best we can!
There are a couple of things to consider when you're traversing anything Axon:

* Checking the [reference guide](https://docs.axoniq.io/reference-guide/extensions/multitenancy) should be your first stop,
  as the majority of possible scenarios you might encounter when using Axon should be covered there.
* If the Reference Guide does not cover a specific topic you would've expected,
  we'd appreciate if you could file an [issue](https://github.com/AxonIQ/reference-guide/issues) about it for us.
* There is a [forum](https://discuss.axoniq.io/) to support you in the case the reference guide did not sufficiently answer your question.
  Axon Framework and Server developers will help out on a best effort basis.
  Know that any support from contributors on posted question is very much appreciated on the forum.
* Next to the forum we also monitor Stack Overflow for any questions which are tagged with `axon`.

## Feature requests and issue reporting

We use GitHub's [issue tracking system](https://github.com/AxonFramework/extension-multitenancy/issues) for new feature
request, extension enhancements and bugs.
Prior to filing an issue, please verify that it's not already reported by someone else.

When filing bugs:
* A description of your setup and what's happening helps us figuring out what the issue might be
* Do not forget to provide version you're using
* If possible, share a stack trace, using the Markdown semantic ```

When filing features:
* A description of the envisioned addition or enhancement should be provided
* (Pseudo-)Code snippets showing what it might look like help us understand your suggestion better
* If you have any thoughts on where to plug this into the framework, that would be very helpful too
* Lastly, we value contributions to the framework highly. So please provide a Pull Request as well!