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
 *       Kevin Leturc <kleturc@nuxeo.com>
 */
package org.nuxeo.ecm.core.bulk;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.nuxeo.ecm.core.bulk.BulkComponent.BULK_KV_STORE_NAME;
import static org.nuxeo.ecm.core.bulk.BulkComponent.BULK_LOG_MANAGER_NAME;
import static org.nuxeo.ecm.core.bulk.BulkStatus.State.SCHEDULED;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.bulk.BulkStatus.State;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.log.LogAppender;
import org.nuxeo.lib.stream.log.LogManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;
import org.nuxeo.runtime.stream.StreamService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Basic implementation of {@link BulkService}.
 *
 * @since 10.2
 */
public class BulkServiceImpl implements BulkService {

    private static final Log log = LogFactory.getLog(BulkServiceImpl.class);

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected static final String SET_STREAM_NAME = "documentSet";

    protected static final String COMMAND = ":command";

    protected static final String CREATION_DATE = ":creationDate";

    protected static final String STATE = ":state";

    protected static final String SCROLLED_DOCUMENT_COUNT = ":count";

    @Override
    public BulkStatus runAction(BulkCommand command) {
        if (log.isDebugEnabled()) {
            log.debug("Run action with command=" + command);
        }
        // check command
        if (isEmpty(command.getRepository())) {
            throw new IllegalArgumentException("Missing mandatory values");
        }
        // create the action id and status
        String bulkActionId = UUID.randomUUID().toString();

        BulkStatus status = new BulkStatus();
        status.setId(bulkActionId);
        status.setState(SCHEDULED);
        status.setCreationInstant(Instant.now());
        status.setCommand(command);

        try {
            byte[] commandAsBytes = OBJECT_MAPPER.writeValueAsBytes(command);

            // store the bulk command and status in the key/value store
            KeyValueStore keyValueStore = getKvStore();
            keyValueStore.put(bulkActionId + STATE, status.getState().toString());
            keyValueStore.put(bulkActionId + CREATION_DATE, status.getCreationInstant().toString());
            keyValueStore.put(bulkActionId + COMMAND, commandAsBytes);

            // send it to nuxeo-stream
            LogManager logManager = Framework.getService(StreamService.class).getLogManager(BULK_LOG_MANAGER_NAME);
            LogAppender<Record> logAppender = logManager.getAppender(SET_STREAM_NAME);
            logAppender.append(bulkActionId, new Record(bulkActionId, commandAsBytes));
        } catch (JsonProcessingException e) {
            throw new NuxeoException("Unable to serialize the bulk command=" + command, e);
        }
        return status;
    }

    @Override
    public BulkStatus getStatus(String bulkActionId) {
        BulkStatus status = new BulkStatus();
        status.setId(bulkActionId);

        // retrieve values from KeyValueStore
        KeyValueStore keyValueStore = getKvStore();
        String state = keyValueStore.getString(bulkActionId + STATE);
        status.setState(State.valueOf(state));

        String creationDate = keyValueStore.getString(bulkActionId + CREATION_DATE);
        status.setCreationInstant(Instant.parse(creationDate));

        String commandAsString = keyValueStore.getString(bulkActionId + COMMAND);
        try {
            BulkCommand command = OBJECT_MAPPER.readValue(commandAsString, BulkCommand.class);
            status.setCommand(command);
        } catch (IOException e) {
            throw new NuxeoException("Unable to deserialize the bulk command=" + commandAsString, e);
        }

        Long scrolledDocumentCount = keyValueStore.getLong(bulkActionId + SCROLLED_DOCUMENT_COUNT);
        status.setCount(scrolledDocumentCount);

        return status;
    }

    public KeyValueStore getKvStore() {
        return Framework.getService(KeyValueService.class).getKeyValueStore(BULK_KV_STORE_NAME);
    }

}
