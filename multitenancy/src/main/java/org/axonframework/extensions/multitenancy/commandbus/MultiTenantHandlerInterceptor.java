package org.axonframework.extensions.multitenancy.commandbus;

import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.UnitOfWork;

/**
 * @author Stefan Dragisic
 */
public class MultiTenantHandlerInterceptor implements MessageHandlerInterceptor<Message<?>> {
    @Override
    public Object handle(UnitOfWork<? extends Message<?>> unitOfWork, InterceptorChain interceptorChain) throws Exception {
        //tennnat = reslove(unitOfWork.getMessage());
        //unitOfWork.getOrComputeResource("tenantDescripotor", value -> teenant);
//        unitOfWork.resources().putIfAbsent()
        return interceptorChain.proceed();
    }
}

//todo add dispatch
