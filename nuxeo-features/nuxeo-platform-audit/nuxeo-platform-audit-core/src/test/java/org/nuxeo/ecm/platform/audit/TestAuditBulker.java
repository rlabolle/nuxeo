/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
 *     bdelbosc
 *
 */

package org.nuxeo.ecm.platform.audit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.platform.audit.api.AuditQueryBuilder;
import org.nuxeo.ecm.platform.audit.api.AuditStorage;
import org.nuxeo.ecm.platform.audit.api.ExtendedInfo;
import org.nuxeo.ecm.platform.audit.api.FilterMapEntry;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.ecm.platform.audit.impl.LogEntryImpl;
import org.nuxeo.ecm.platform.audit.service.AuditBackend;
import org.nuxeo.ecm.platform.audit.service.DefaultAuditBulker;
import org.nuxeo.ecm.platform.audit.service.extension.AuditBulkerDescriptor;
import sun.rmi.runtime.Log;

/**
 * @since 10.2
 */
public class TestAuditBulker {
    final org.apache.commons.logging.Log log = LogFactory.getLog(TestAuditBulker.class);

    @Test
    public void testDefaultAuditBulker() throws InterruptedException {
        int bulkSize = 1000;
        int bulkTimeout = 1000;
        long nbEntriesPerThread = 10234;
        int concurrency = 8;

        AuditBulkerDescriptor config = new AuditBulkerDescriptor();
        AuditBackend backend = new MockAuditBackend();
        config.size = bulkSize;
        config.timeout = bulkTimeout;
        DefaultAuditBulker bulker = new DefaultAuditBulker(backend, config);
        bulker.onApplicationStarted();
        assertEquals(bulkSize, bulker.getBulkSize());
        assertEquals(bulkTimeout, bulker.getBulkTimeout());

        LogEntry entry = new LogEntryImpl();

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        Runnable writer = () -> {
            for (int i = 0; i < nbEntriesPerThread; i++) {
                bulker.offer(entry);
            }
        };
        for (int i = 0; i < concurrency; i++) {
            executor.submit(writer);
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));
        log.warn("End of writers");

        long nbEntries = concurrency * nbEntriesPerThread;
        assertTrue(nbEntries >  backend.getEventsCount(null).longValue());
        bulker.await(20, TimeUnit.SECONDS);
        assertEquals(nbEntries, backend.getEventsCount(null).longValue());
    }

    protected class MockAuditBackend implements AuditBackend {

        AtomicLong count = new AtomicLong(0);

        @Override
        public void addLogEntries(List<LogEntry> list) {
            count.addAndGet(list.size());
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NuxeoException(e);
            }
        }

        @Override
        public Long getEventsCount(String s) {
            return count.get();
        }

        // ------------------------------------------
        // dummy impl not used by bulker

        @Override
        public int getApplicationStartedOrder() {
            return 0;
        }

        @Override
        public void onApplicationStarted() {

        }

        @Override
        public void restore(AuditStorage auditStorage, int batchSize, int keepAlive) {

        }

        @Override
        public long syncLogCreationEntries(String s, String s1, Boolean aBoolean) {
            return 0;
        }

        @Override
        public Set<String> getAuditableEventNames() {
            return null;
        }

        @Override
        public LogEntry newLogEntry() {
            return null;
        }

        @Override
        public ExtendedInfo newExtendedInfo(Serializable serializable) {
            return null;
        }

        @Override
        public void logEvent(Event event) {

        }

        @Override
        public void logEvents(EventBundle eventBundle) {

        }

        @Override
        public boolean await(long l, TimeUnit timeUnit) throws InterruptedException {
            return false;
        }

        @Override
        public LogEntry buildEntryFromEvent(Event event) {
            return null;
        }

        @Override
        public List<LogEntry> getLogEntriesFor(String s, Map<String, FilterMapEntry> map, boolean b) {
            return null;
        }

        @Override
        public LogEntry getLogEntryByID(long l) {
            return null;
        }

        @Override
        public List<LogEntry> queryLogs(AuditQueryBuilder auditQueryBuilder) {
            return null;
        }

        @Override
        public List<LogEntry> queryLogsByPage(String[] strings, Date date, String[] strings1, String s, int i, int i1) {
            return null;
        }

        @Override
        public List<?> nativeQuery(String s, Map<String, Object> map, int i, int i1) {
            return null;
        }

        @Override
        public long getLatestLogId(String s, String... strings) {
            return 0;
        }

        @Override
        public List<LogEntry> getLogEntriesAfter(long l, int i, String s, String... strings) {
            return null;
        }
    }
}
