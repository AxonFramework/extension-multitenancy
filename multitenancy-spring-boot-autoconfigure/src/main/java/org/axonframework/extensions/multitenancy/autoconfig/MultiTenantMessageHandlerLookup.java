/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.extensions.multitenancy.autoconfig;

import jakarta.annotation.Nonnull;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.annotation.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.OrderUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A multi-tenant aware version of the message handler lookup that registers
 * {@link MultiTenantMessageHandlerConfigurer} instead of the standard configurer.
 * <p>
 * This lookup implements {@link PriorityOrdered} with {@link Ordered#HIGHEST_PRECEDENCE}
 * to ensure it runs before the standard Spring extension's MessageHandlerLookup,
 * effectively replacing the standard event processor configuration with multi-tenant
 * aware processors.
 * <p>
 * The bean names used match the standard Spring extension's names, so the standard
 * lookup will skip registration when it sees these beans already exist.
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see MultiTenantMessageHandlerConfigurer
 */
public class MultiTenantMessageHandlerLookup implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    private static final Logger logger = LoggerFactory.getLogger(MultiTenantMessageHandlerLookup.class);

    public static List<String> messageHandlerBeans(Class<? extends Message> messageType,
                                                   ConfigurableListableBeanFactory registry) {
        List<String> found = new ArrayList<>();
        for (String beanName : registry.getBeanDefinitionNames()) {
            BeanDefinition bd = registry.getBeanDefinition(beanName);

            if (bd.isAutowireCandidate()) {
                if (bd.isSingleton() && !bd.isAbstract()) {
                    Class<?> beanType = registry.getType(beanName);
                    if (beanType != null && hasMessageHandler(messageType, beanType)) {
                        found.add(beanName);
                    }
                }
            }
        }
        return found;
    }

    private static boolean hasMessageHandler(Class<? extends Message> messageType, Class<?> beanType) {
        for (Method m : ReflectionUtils.methodsOf(beanType)) {
            Optional<Map<String, Object>> attr = AnnotationUtils.findAnnotationAttributes(m, MessageHandler.class);
            if (attr.isPresent() && messageType.isAssignableFrom((Class<?>) attr.get().get("messageType"))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void postProcessBeanFactory(@Nonnull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof BeanDefinitionRegistry)) {
            logger.warn("Given bean factory is not a BeanDefinitionRegistry. Cannot auto-configure multi-tenant message handlers");
            return;
        }

        for (MultiTenantMessageHandlerConfigurer.Type value : MultiTenantMessageHandlerConfigurer.Type.values()) {
            // Use the SAME bean name as the standard Spring extension to prevent it from registering
            String configurerBeanName = "MessageHandlerConfigurer$$Axon$$" + value.name();
            if (beanFactory.containsBeanDefinition(configurerBeanName)) {
                logger.debug("Message handler configurer [{}] already available. Skipping multi-tenant configuration", configurerBeanName);
                continue;
            }

            List<String> found = messageHandlerBeans(value.getMessageType(), beanFactory);
            if (!found.isEmpty()) {
                List<String> sortedFound = sortByOrder(found, beanFactory);
                logger.debug("Registering multi-tenant message handler configurer [{}] for {} handlers",
                           configurerBeanName, sortedFound.size());
                AbstractBeanDefinition beanDefinition =
                        BeanDefinitionBuilder.genericBeanDefinition(MultiTenantMessageHandlerConfigurer.class)
                                             .addConstructorArgValue(value.name())
                                             .addConstructorArgValue(sortedFound)
                                             .getBeanDefinition();
                ((BeanDefinitionRegistry) beanFactory).registerBeanDefinition(configurerBeanName, beanDefinition);
            }
        }
    }

    private List<String> sortByOrder(List<String> found, @Nonnull ConfigurableListableBeanFactory beanFactory) {
        return found.stream()
                    .collect(Collectors.toMap(
                            beanRef -> beanRef,
                            beanRef -> OrderUtils.getOrder(
                                    beanFactory.getType(beanRef) != null ? beanFactory.getType(beanRef) : Object.class,
                                    Ordered.LOWEST_PRECEDENCE
                            )
                    ))
                    .entrySet()
                    .stream()
                    .sorted(java.util.Map.Entry.comparingByValue())
                    .map(java.util.Map.Entry::getKey)
                    .collect(Collectors.toList());
    }

    @Override
    public void postProcessBeanDefinitionRegistry(@Nonnull BeanDefinitionRegistry registry) throws BeansException {
        // No action required.
    }

    @Override
    public int getOrder() {
        // Run before the standard Spring extension's MessageHandlerLookup
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
