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
 *     Funsho David
 */

package org.nuxeo.runtime.stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.lib.stream.computation.AbstractComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.computation.Topology;

/**
 * Abstract implementation of a topology processing its entries by batches.
 *
 * @since 10.2
 */
public abstract class BatchedProcessorTopology<T> implements StreamProcessorTopology {

    private static final Log log = LogFactory.getLog(BatchedProcessorTopology.class);

    public static final int DEFAULT_BATCH_SIZE = 10;

    public static final int DEFAULT_BATCH_THRESHOLD_MS = 200;

    public static final String BATCH_SIZE_OPT = "batchSize";

    public static final String BATCH_THRESHOLD_MS_OPT = "batchThresholdMs";

    protected String computationName;

    protected String streamName;

    public BatchedProcessorTopology(String computationName, String streamName) {
        this.computationName = computationName;
        this.streamName = streamName;
    }

    @Override
    public Topology getTopology(Map<String, String> options) {
        int batchSize = getOptionAsInteger(options, BATCH_SIZE_OPT, DEFAULT_BATCH_SIZE);
        int batchThresholdMs = getOptionAsInteger(options, BATCH_THRESHOLD_MS_OPT, DEFAULT_BATCH_THRESHOLD_MS);
        return Topology.builder()
                       .addComputation(() -> new BatchComputation(computationName, batchSize, batchThresholdMs),
                               Collections.singletonList("i1:" + streamName))
                       .build();
    }

    protected class BatchComputation extends AbstractComputation {

        protected final int batchSize;

        protected final int batchThresholdMs;

        protected final List<T> entries;

        public BatchComputation(String name, int batchSize, int batchThresholdMs) {
            super(name, 1, 0);
            this.batchSize = batchSize;
            this.batchThresholdMs = batchThresholdMs;
            entries = new ArrayList<>(batchSize);
        }

        @Override
        public void init(ComputationContext context) {
            log.debug(String.format("Starting computation: %s reading on: %s, batch size: %d, threshold: %dms",
                    computationName, streamName, batchSize, batchThresholdMs));
            context.setTimer("batch", System.currentTimeMillis() + batchThresholdMs);
        }

        @Override
        public void processTimer(ComputationContext context, String key, long timestamp) {
            processBatch(context, entries);
            entries.clear();
            context.setTimer("batch", System.currentTimeMillis() + batchThresholdMs);
        }

        @Override
        public void processRecord(ComputationContext context, String inputStreamName, Record record) {
            if (flushBatch(record)) {
                processBatch(context, entries);
                entries.clear();
            } else {
                entries.addAll(getEntriesFromRecord(record));
                if (entries.size() >= batchSize) {
                    processBatch(context, entries);
                    entries.clear();
                }
            }
        }

        @Override
        public void destroy() {
            log.debug(String.format("Destroy computation: %s, pending entries: %d", computationName, entries.size()));
        }

    }

    protected int getOptionAsInteger(Map<String, String> options, String option, int defaultValue) {
        String value = options.get(option);
        return value == null ? defaultValue : Integer.valueOf(value);
    }

    protected boolean flushBatch(Record record) {
        return false;
    }

    protected abstract List<T> getEntriesFromRecord(Record record);

    protected abstract void processBatch(ComputationContext context, List<T> entries);
}
