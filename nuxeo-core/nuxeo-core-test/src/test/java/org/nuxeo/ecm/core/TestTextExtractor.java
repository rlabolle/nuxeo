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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core;

import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.storage.FulltextExtractorWork.TextExtractor;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * Test the document text finder used by the fulltext extractor.
 */
@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestTextExtractor {

    @Inject
    protected CoreSession session;

    @Test
    public void test() throws Exception {
        DocumentModel doc = session.createDocumentModel("File");
        doc.addFacet("HasRelatedText");
        doc.setPropertyValue("dc:title", "title");
        doc.setPropertyValue("uid:uid", "uid");

        Map<String, String> rt1 = new HashMap<>();
        rt1.put("relatedtextid", "id1");
        rt1.put("relatedtext", "text1");
        Map<String, String> rt2 = new HashMap<>();
        rt2.put("relatedtextid", "id2");
        rt2.put("relatedtext", "text2");
        List<Map<String, String>> rtr = Arrays.asList(rt1, rt2);
        doc.setPropertyValue("relatedtext:relatedtextresources", (Serializable) rtr);

        Blob blob1 = Blobs.createBlob("blob1");
        Blob blob2 = Blobs.createBlob("blob1");
        List<Map<String, Blob>> files = Arrays.asList(singletonMap("file", blob1), singletonMap("file", blob2));
        doc.setPropertyValue("files:files", (Serializable) files);

        String text;
        Set<String> paths;

        paths = null; // all paths
        text = new TextExtractor().findText(doc, paths);
        assertText(text, "title", "uid", "id1", "text1", "id2", "text2");

        paths = new HashSet<>(Arrays.asList("dc:title"));
        text = new TextExtractor().findText(doc, paths);
        assertText(text, "title");

        paths = new HashSet<>(Arrays.asList("uid:uid")); // schema without prefix
        text = new TextExtractor().findText(doc, paths);
        assertText(text, "uid");

        paths = new HashSet<>(Arrays.asList("dc:title", "uid:uid"));
        text = new TextExtractor().findText(doc, paths);
        assertText(text, "title", "uid");

        paths = new HashSet<>(Arrays.asList("relatedtext:relatedtextresources/*/relatedtext"));
        text = new TextExtractor().findText(doc, paths);
        assertText(text, "text1", "text2");
    }

    protected static void assertText(String text, String... strings) {
        Set<String> expected = new HashSet<>(Arrays.asList(strings));
        Set<String> actual = new HashSet<>(Arrays.asList(text.split("[ \n]")));
        assertEquals(expected, actual);
    }
}
