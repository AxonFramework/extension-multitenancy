package org.axonframework.extensions.multitenancy;

import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;

import java.util.function.Supplier;

/**
 * Wrapper around transaction manager that adds the current tenant to the transaction context. Used in certain cases to
 * determine the tenant of the current transaction.
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public class TenantWrappedTransactionManager implements TransactionManager {

    private final TransactionManager delegate;
    private final TenantDescriptor tenantDescriptor;
    private static final ThreadLocal<TenantDescriptor> threadLocal = new ThreadLocal<>();

    public TenantWrappedTransactionManager(TransactionManager delegate,
                                           TenantDescriptor tenantDescriptor) {
        this.delegate = delegate;
        this.tenantDescriptor = tenantDescriptor;
    }

    @Override
    public Transaction startTransaction() {
        threadLocal.set(tenantDescriptor);
        return delegate.startTransaction();
    }

    @Override
    public void executeInTransaction(Runnable task) {
        threadLocal.set(tenantDescriptor);
        delegate.executeInTransaction(task);
    }

    @Override
    public <T> T fetchInTransaction(Supplier<T> supplier) {
        threadLocal.set(tenantDescriptor);
        return delegate.fetchInTransaction(supplier);
    }

    public static TenantDescriptor getCurrentTenant() {
        return threadLocal.get();
    }
}
