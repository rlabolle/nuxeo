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

package org.nuxeo.ecm.automation.core.operations.services.bulk;

import java.util.UUID;

import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.BulkStatus;

/**
 * @since 10.2
 */
@Operation(id = GetBulkStatus.ID, category = Constants.CAT_SERVICES, label = "Get bulk status", description = "Get the status of a bulk action")
public class GetBulkStatus {

    public static final String ID = "Bulk.GetStatus";

    @Context
    protected BulkService bulkService;

    @Param(name = "id")
    protected String id;

    @OperationMethod
    public BulkStatus run() {
        return bulkService.getStatus(UUID.fromString(id));
    }

}
