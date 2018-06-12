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
package org.nuxeo.ecm.core.bulk;

/**
 * API to manage Bulk Action Framework.
 *
 * @since 10.2
 */
public interface BulkService {

    /**
     * Submits the input {@link BulkCommand} to the Bulk Action Framework system and get the initial status containing
     * the bulk action id.
     *
     * @param command the command to run
     * @return the initial bulk status
     */
    BulkStatus runAction(BulkCommand command);

    /**
     * Returns the bulk action status of given bulk action id.
     *
     * @return the bulk action status of given bulk action id
     */
    BulkStatus getStatus(String bulkActionId);
}
