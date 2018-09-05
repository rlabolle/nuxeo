/*
 * (C) Copyright 2015-2018 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Remi Cattiau
 */

package org.nuxeo.runtime.aws;

import com.amazonaws.auth.AWSCredentials;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;


@XObject("configuration")
public class AWSCredentialsDescriptor implements AWSCredentials {

    @XNode("accessKeyId")
    protected String accessKeyId;

    @XNode("secretAccessKey")
    protected String secretAccessKey;

    @Override
    public String getAWSAccessKeyId() {
        return accessKeyId;
    }

    @Override
    public String getAWSSecretKey() {
        return secretAccessKey;
    }

    public Boolean isValid() {
        return !StringUtils.isBlank(accessKeyId) && !StringUtils.isBlank(secretAccessKey);
    }
}
