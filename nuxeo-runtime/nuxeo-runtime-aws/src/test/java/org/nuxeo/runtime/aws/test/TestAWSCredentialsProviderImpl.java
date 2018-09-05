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
package org.nuxeo.runtime.aws.test;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import org.junit.Assert;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.junit.Test;
import com.google.inject.Inject;
import org.junit.runner.RunWith;
import org.nuxeo.runtime.aws.NuxeoAWSCredentialsProvider;

@RunWith(FeaturesRunner.class)
@Features(RuntimeFeature.class)
@Deploy({ "nuxeo-aws-core", "nuxeo-aws-core:OSGI-INF/mock-creds-contrib.xml" })
public class TestAWSCredentialsProviderImpl {

    @Inject
    NuxeoAWSCredentialsProvider provider;

    @Test
    public void testCredentials() {
        Assert.assertNotEquals(DefaultAWSCredentialsProviderChain.class, provider.getProvider().getClass());
    }
}