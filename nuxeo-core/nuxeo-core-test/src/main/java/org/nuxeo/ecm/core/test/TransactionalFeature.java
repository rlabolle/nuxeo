/*
 * (C) Copyright 2006-2017 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Stephane Lacoin
 *     Kevin Leturc <kleturc@nuxeo.com>
 */
package org.nuxeo.ecm.core.test;

import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.core.test.annotations.TransactionalConfig;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.HotDeployer.ActionHandler;

/**
 * @deprecated since 10.2, use {@link org.nuxeo.runtime.test.runner.TransactionalFeature} instead
 */
@Deprecated
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TransactionalFeature extends org.nuxeo.runtime.test.runner.TransactionalFeature {

    /**
     * @deprecated since 10.2, use {@link org.nuxeo.runtime.test.runner.TransactionalFeature.Waiter} instead
     */
    @Deprecated
    @FunctionalInterface
    public interface Waiter extends org.nuxeo.runtime.test.runner.TransactionalFeature.Waiter {

    }

    /**
     * @deprecated since 10.2, use
     *             {@link org.nuxeo.runtime.test.runner.TransactionalFeature#addWaiter(org.nuxeo.runtime.test.runner.TransactionalFeature.Waiter)}
     *             instead
     */
    @Deprecated
    public void addWaiter(Waiter waiter) {
        super.addWaiter(waiter);
    }

    @Override
    public void initialize(FeaturesRunner runner) {
        super.initialize(runner);
        autoStartTransaction = runner.getConfig(TransactionalConfig.class).autoStart();
    }

    /**
     * Handler used to commit transaction before next action and start a new one after next action if
     * {@link TransactionalConfig#autoStart()} is true. This is because framework is about to be reloaded, then a new
     * transaction manager will be installed.
     *
     * @since 9.3
     * @deprecated since 10.2, use {@link org.nuxeo.runtime.test.runner.TransactionalFeature} and
     *             {@link org.nuxeo.runtime.test.runner.TransactionalFeature.TransactionalDeployer}
     */
    @Deprecated
    public class TransactionalDeployer extends ActionHandler {

        @Override
        public void exec(String action, String... args) throws Exception {
            commitOrRollbackTransactionAfter();
            next.exec(action, args);
            startTransactionBefore();
        }

    }

}
