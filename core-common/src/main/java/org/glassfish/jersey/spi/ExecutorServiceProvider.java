/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.jersey.spi;

import java.util.concurrent.ExecutorService;

/**
 * An extension contract for providing pluggable executor service providers to be used by
 * Jersey client or server runtime whenever a specific executor service is needed to execute a Jersey runtime processing task.
 * <p>
 * This mechanism allows Jersey to run in environments that have specific thread management and provisioning requirements,
 * such as application servers, cloud environments etc.
 * Dedicated Jersey extension modules or applications running in such environment may provide a custom
 * implementation of the {@code ExecutorServiceProvider} interface to customize the default
 * Jersey runtime thread management & provisioning strategy in order to comply with the threading requirements,
 * models and policies specific to each particular environment.
 * </p>
 * <p>
 * TODO describe the role of qualifier annotations
 * </p>
 *
 * @author Marek Potociar (marek.potociar at oracle.com)
 * @see org.glassfish.jersey.spi.ScheduledExecutorServiceProvider
 * @since 2.18
 */
@Contract
public interface ExecutorServiceProvider {

    /**
     * Get an executor service to be used by Jersey client or server runtime to execute specific tasks.
     * <p>
     * This method is called only once at either Jersey client or server application runtime initialization.
     * </p>
     * <p>
     * The provided executor service will be shut down by invoking its
     * {@link java.util.concurrent.ExecutorService#shutdown() shutdown()} and/or
     * {@link java.util.concurrent.ExecutorService#shutdownNow() shutdownNow()} method
     * as soon as Jersey application runtime no longer requires its service.
     * This typically happens in one of the following situations:
     * <ul>
     * <li>Jersey client instance is closed (client runtime is shut down).</li>
     * <li>Jersey container running a server-side Jersey application is shut down.</li>
     * <li>Jersey server-side application is un-deployed.</li>
     * </ul>
     * </p>
     *
     * @return an executor service. Must not return {@code null}.
     */
    public ExecutorService getExecutorService();

}
