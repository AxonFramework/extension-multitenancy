/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
