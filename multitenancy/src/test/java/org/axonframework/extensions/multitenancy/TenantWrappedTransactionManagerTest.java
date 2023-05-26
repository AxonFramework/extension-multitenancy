/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.junit.jupiter.api.*;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Stefan Dragisic
 */
class TenantWrappedTransactionManagerTest {

    private TenantWrappedTransactionManager testSubject;
    private TransactionManager delegate;
    private final TenantDescriptor tenant1 = TenantDescriptor.tenantWithId("tenant1");

    @BeforeEach
    void setUp() {
        delegate = mock(TransactionManager.class);
        testSubject = new TenantWrappedTransactionManager(delegate, tenant1);
    }

    @Test
    public void testStartTransaction() {
        Transaction transactionMock = mock(Transaction.class);
        when(delegate.startTransaction()).thenReturn(transactionMock);

        testSubject.startTransaction();

        assertNull(TenantWrappedTransactionManager.getCurrentTenant());
        verify(delegate, times(1)).startTransaction();
    }

    @Test
    public void executeInTransaction() {
        doNothing().when(delegate).executeInTransaction(any());

        Runnable task = () -> assertEquals(tenant1, TenantWrappedTransactionManager.getCurrentTenant());
        testSubject.executeInTransaction(task);

        assertNull(TenantWrappedTransactionManager.getCurrentTenant());
        verify(delegate, times(1)).executeInTransaction(task);
    }

    @Test
    public void fetchInTransaction() {
        when(delegate.fetchInTransaction(any())).thenReturn("result");

        Supplier<String> supplier = () -> {
            assertEquals(tenant1, TenantWrappedTransactionManager.getCurrentTenant());
            return "string";
        };
        testSubject.fetchInTransaction(supplier);

        assertNull(TenantWrappedTransactionManager.getCurrentTenant());
        verify(delegate, times(1)).fetchInTransaction(supplier);
    }
}