/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2015 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.jersey.process.internal;

import java.lang.annotation.Annotation;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Named;
import javax.inject.Qualifier;
import javax.inject.Singleton;

import org.glassfish.jersey.internal.LocalizationMessages;
import org.glassfish.jersey.internal.inject.Injections;
import org.glassfish.jersey.internal.inject.Providers;
import org.glassfish.jersey.internal.util.ReflectionHelper;
import org.glassfish.jersey.internal.util.collection.LazyValue;
import org.glassfish.jersey.internal.util.collection.Value;
import org.glassfish.jersey.internal.util.collection.Values;
import org.glassfish.jersey.spi.ExecutorServiceProvider;
import org.glassfish.jersey.spi.ScheduledExecutorServiceProvider;

import org.glassfish.hk2.api.DynamicConfiguration;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.binding.ScopedBindingBuilder;

/**
 * A utility class with a method for creating qualified executor injection bindings based on the registered
 * {@link org.glassfish.jersey.spi.ExecutorServiceProvider} and/or
 * {@link org.glassfish.jersey.spi.ScheduledExecutorServiceProvider} SPI implementations.
 *
 * @author Marek Potociar (marek.potociar at oracle.com)
 */
public final class ExecutorProviders {

    private static final Logger LOGGER = Logger.getLogger(ExecutorProviders.class.getName());

    private ExecutorProviders() {
        throw new AssertionError("Instantiation not allowed.");
    }

    /**
     * Create qualified {@link java.util.concurrent.ExecutorService} and {@link java.util.concurrent.ScheduledExecutorService}
     * injection bindings based on the registered providers implementing the
     * {@link org.glassfish.jersey.spi.ExecutorServiceProvider} and/or
     * {@link org.glassfish.jersey.spi.ScheduledExecutorServiceProvider} SPI.
     * <p>
     * This method supports creation of qualified injection bindings based on custom
     * {@link javax.inject.Qualifier qualifier annotations} attached to the registered provider implementation classes
     * as well as named injection bindings based on the {@link javax.inject.Named} qualifier annotation attached to the
     * registered provider implementation classes.
     * </p>
     *
     * @param locator application's HK2 service locator.
     */
    public static void createInjectionBindings(final ServiceLocator locator) {

        final Map<Class<? extends Annotation>, List<ExecutorServiceProvider>> executorProviderMap =
                getQualifierToProviderMap(locator, ExecutorServiceProvider.class);

        // for each bucket, create a new injection binding for the first provider in the list and discard the rest
        final DynamicConfiguration dc = Injections.getConfiguration(locator);

        for (Map.Entry<Class<? extends Annotation>, List<ExecutorServiceProvider>> qualifierToProviders
                : executorProviderMap.entrySet()) {
            final Class<? extends Annotation> qualifierAnnotationClass = qualifierToProviders.getKey();

            final Iterator<ExecutorServiceProvider> bucketProviderIterator = qualifierToProviders.getValue().iterator();
            final ExecutorServiceProvider executorProvider = bucketProviderIterator.next();
            if (LOGGER.isLoggable(Level.CONFIG)) {
                LOGGER.config(LocalizationMessages.USING_EXECUTOR_PROVIDER(
                        executorProvider.getClass().getName(), qualifierAnnotationClass.getName()));

                if (bucketProviderIterator.hasNext()) {
                    StringBuilder msg = new StringBuilder(bucketProviderIterator.next().getClass().getName());
                    while (bucketProviderIterator.hasNext()) {
                        msg.append(", ").append(bucketProviderIterator.next().getClass().getName());
                    }
                    LOGGER.config(LocalizationMessages.IGNORED_EXECUTOR_PROVIDERS(
                            msg.toString(), qualifierAnnotationClass.getName()));
                }
            }

            ScopedBindingBuilder<ExecutorService> bindingBuilder = Injections
                    .newFactoryBinder(new ExecutorServiceFactory(executorProvider))
                    .to(ExecutorService.class)
                    .in(Singleton.class);

            final Annotation qualifier = executorProvider.getClass().getAnnotation(qualifierAnnotationClass);
            if (qualifier instanceof Named) {
                Injections.addBinding(bindingBuilder.named(((Named) qualifier).value()), dc);
            } else {
                Injections.addBinding(bindingBuilder.qualifiedBy(qualifier), dc);
            }
        }

        final Map<Class<? extends Annotation>, List<ScheduledExecutorServiceProvider>> schedulerProviderMap =
                getQualifierToProviderMap(locator, ScheduledExecutorServiceProvider.class);

        for (Map.Entry<Class<? extends Annotation>, List<ScheduledExecutorServiceProvider>> qualifierToProviders
                : schedulerProviderMap.entrySet()) {
            final Class<? extends Annotation> qualifierAnnotationClass = qualifierToProviders.getKey();

            final Iterator<ScheduledExecutorServiceProvider> bucketProviderIterator = qualifierToProviders.getValue().iterator();
            final ScheduledExecutorServiceProvider executorProvider = bucketProviderIterator.next();
            if (LOGGER.isLoggable(Level.CONFIG)) {
                LOGGER.config(LocalizationMessages.USING_SCHEDULER_PROVIDER(
                        executorProvider.getClass().getName(), qualifierAnnotationClass.getName()));

                if (bucketProviderIterator.hasNext()) {
                    StringBuilder msg = new StringBuilder(bucketProviderIterator.next().getClass().getName());
                    while (bucketProviderIterator.hasNext()) {
                        msg.append(", ").append(bucketProviderIterator.next().getClass().getName());
                    }
                    LOGGER.config(LocalizationMessages.IGNORED_SCHEDULER_PROVIDERS(
                            msg.toString(), qualifierAnnotationClass.getName()));
                }
            }

            final ScopedBindingBuilder<ScheduledExecutorService> bindingBuilder =
                    Injections.newFactoryBinder(new ScheduledExecutorServiceFactory(executorProvider))
                            .in(Singleton.class)
                            .to(ScheduledExecutorService.class);

            if (!executorProviderMap.containsKey(qualifierAnnotationClass)) {
                // it is safe to register binding for ExecutorService too...
                bindingBuilder.to(ExecutorService.class);
            }

            final Annotation qualifier = executorProvider.getClass().getAnnotation(qualifierAnnotationClass);
            if (qualifier instanceof Named) {
                Injections.addBinding(bindingBuilder.named(((Named) qualifier).value()), dc);
            } else {
                Injections.addBinding(bindingBuilder.qualifiedBy(qualifier), dc);
            }
        }

        dc.commit();
    }

    private static <T extends ExecutorServiceProvider> Map<Class<? extends Annotation>, List<T>> getQualifierToProviderMap(
            final ServiceLocator locator,
            final Class<T> providerClass) {

        // get all ExecutorServiceProvider registrations and create iterator with custom providers in the front
        final Set<T> customExecutorProviders =
                Providers.getCustomProviders(locator, providerClass);
        final Set<T> defaultExecutorProviders =
                Providers.getProviders(locator, providerClass);
        defaultExecutorProviders.removeAll(customExecutorProviders);

        final List<T> executorProviders = new LinkedList<T>(customExecutorProviders);
        executorProviders.addAll(defaultExecutorProviders);
        final Iterator<T> providersIterator = executorProviders.iterator();

        // iterate over providers and map them by Qualifier annotations (custom ones will be added to the buckets first)
        final Map<Class<? extends Annotation>, List<T>> executorProviderMap =
                new HashMap<Class<? extends Annotation>, List<T>>();

        while (providersIterator.hasNext()) {
            final T provider = providersIterator.next();

            for (Class<? extends Annotation> qualifier
                    : ReflectionHelper.getAnnotationTypes(provider.getClass(), Qualifier.class)) {

                List<T> providersForQualifier;
                if (!executorProviderMap.containsKey(qualifier)) {
                    providersForQualifier = new LinkedList<T>();
                    executorProviderMap.put(qualifier, providersForQualifier);
                } else {
                    providersForQualifier = executorProviderMap.get(qualifier);
                }

                providersForQualifier.add(provider);
            }
        }

        return executorProviderMap;

    }

    private static class ExecutorServiceFactory implements Factory<ExecutorService> {

        private final ExecutorServiceProvider executorProvider;
        private final LazyValue<ExecutorService> executorValue;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private ExecutorServiceFactory(ExecutorServiceProvider executorServiceProvider) {
            executorProvider = executorServiceProvider;
            executorValue = Values.lazy(new Value<ExecutorService>() {
                @Override
                public ExecutorService get() {
                    return executorProvider.getExecutorService();
                }
            });
        }

        @Override
        public ExecutorService provide() {
            if (closed.get()) {
                throw new IllegalStateException(LocalizationMessages.REQUEST_EXECUTOR_FACTORY_CLOSED(this.getClass().getName()));
            }
            return executorValue.get();
        }

        @Override
        public void dispose(final ExecutorService instance) {
            AccessController.doPrivileged(new PrivilegedAction() {

                @Override
                public Object run() {
                    instance.shutdownNow();
                    return null;
                }
            });
        }

        /**
         * Close the executor factory and release any associated executor service resources.
         */
        public final void close() {
            if (closed.compareAndSet(false, true) && executorValue.isInitialized()) {
                executorValue.get().shutdownNow();
            }
        }
    }

    private static class ScheduledExecutorServiceFactory implements Factory<ScheduledExecutorService> {

        private final ScheduledExecutorServiceProvider executorProvider;
        private final LazyValue<ScheduledExecutorService> executorValue;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private ScheduledExecutorServiceFactory(ScheduledExecutorServiceProvider executorServiceProvider) {
            executorProvider = executorServiceProvider;
            executorValue = Values.lazy(new Value<ScheduledExecutorService>() {
                @Override
                public ScheduledExecutorService get() {
                    return executorProvider.getExecutorService();
                }
            });
        }

        @Override
        public ScheduledExecutorService provide() {
            if (closed.get()) {
                throw new IllegalStateException(LocalizationMessages.REQUEST_EXECUTOR_FACTORY_CLOSED(this.getClass().getName()));
            }
            return executorValue.get();
        }

        @Override
        public void dispose(final ScheduledExecutorService instance) {
            AccessController.doPrivileged(new PrivilegedAction() {

                @Override
                public Object run() {
                    instance.shutdownNow();
                    return null;
                }
            });
        }

        /**
         * Close the executor factory and release any associated executor service resources.
         */
        public final void close() {
            if (closed.compareAndSet(false, true) && executorValue.isInitialized()) {
                executorValue.get().shutdownNow();
            }
        }
    }

}
