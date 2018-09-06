/*
 * (C) Copyright 2006-2018 Nuxeo (http://nuxeo.com/) and others.
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
 *     Stephane Lacoin
 */
package org.nuxeo.ecm.core.storage;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.impl.ArrayProperty;
import org.nuxeo.ecm.core.api.model.impl.ComplexProperty;
import org.nuxeo.ecm.core.api.model.impl.ListProperty;
import org.nuxeo.ecm.core.api.model.impl.primitives.StringProperty;
import org.nuxeo.ecm.core.api.repository.FulltextConfiguration;
import org.nuxeo.ecm.core.convert.api.ConversionException;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.model.Repository;
import org.nuxeo.ecm.core.repository.RepositoryService;
import org.nuxeo.ecm.core.utils.BlobsExtractor;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.runtime.api.Framework;

/**
 * Work task that does fulltext extraction from the string properties and the blobs of the given document, saving them
 * into the fulltext table.
 *
 * @since 5.7 for the original implementation
 * @since 10.3 the extraction and update are done in the same Work
 */
public class FulltextExtractorWork extends AbstractWork {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(FulltextExtractorWork.class);

    public static final String SYSPROP_FULLTEXT_SIMPLE = "fulltextSimple";

    public static final String SYSPROP_FULLTEXT_BINARY = "fulltextBinary";

    public static final String SYSPROP_FULLTEXT_JOBID = "fulltextJobId";

    public static final String FULLTEXT_DEFAULT_INDEX = "default";

    protected static final String CATEGORY = "fulltextExtractor";

    protected static final String TITLE = "Fulltext Extractor";

    protected static final String ANY2TEXT_CONVERTER = "any2text";

    /** If true, update the simple text from the document. */
    protected final boolean updateSimpleText;

    /** If true, update the binary text from the document. */
    protected final boolean updateBinaryText;

    public FulltextExtractorWork(String repositoryName, String docId, boolean updateSimpleText,
            boolean updateBinaryText) {
        super(); // random id, for unique job
        setDocument(repositoryName, docId);
        this.updateSimpleText = updateSimpleText;
        this.updateBinaryText = updateBinaryText;
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    @Override
    public int getRetryCount() {
        return 1;
    }

    @Override
    public void work() {
        openSystemSession();
        // if the runtime has shut down (normally because tests are finished)
        // this can happen, see NXP-4009
        if (session.getPrincipal() == null) {
            return;
        }

        setStatus("Extracting");
        setProgress(Progress.PROGRESS_0_PC);
        extractAndUpdate();
        setProgress(Progress.PROGRESS_100_PC);
        setStatus("Saving");
        session.save();
        setStatus("Done");
    }

    protected void extractAndUpdate() {
        // find which docs will receive the extracted text (there may be more than one if the original
        // doc was copied between the time it was saved and this listener being asynchronously executed)
        String query = String.format("SELECT * FROM Document WHERE ecm:fulltextJobId = '%s' AND ecm:isProxy = 0",
                docId);
        List<DocumentModel> docsToUpdate = session.query(query);
        // update all docs
        DocumentModel document = session.getDocument(new IdRef(docId));
        FulltextConfiguration fulltextConfiguration = getFulltextConfiguration();
        if (updateSimpleText) {
            updateSimpleText(document, docsToUpdate, fulltextConfiguration);
        }
        if (updateBinaryText) {
            updateBinaryText(document, docsToUpdate, fulltextConfiguration);
        }
        // reset job id
        for (DocumentModel doc : docsToUpdate) {
            session.setDocumentSystemProp(doc.getRef(), SYSPROP_FULLTEXT_JOBID, null);
        }
    }

    protected FulltextConfiguration getFulltextConfiguration() {
        RepositoryService repositoryService = Framework.getService(RepositoryService.class);
        Repository repository = repositoryService.getRepository(repositoryName);
        return repository.getFulltextConfiguration();
    }

    protected void updateSimpleText(DocumentModel document, List<DocumentModel> docsToUpdate,
            FulltextConfiguration fulltextConfiguration) {
        if (fulltextConfiguration.fulltextSearchDisabled) {
            // if fulltext search is disabled, we don't extract simple text at all
            return;
        }
        int fullTextFieldSizeLimit = fulltextConfiguration.fulltextFieldSizeLimit;
        for (String indexName : fulltextConfiguration.indexNames) {
            Set<String> paths;
            if (fulltextConfiguration.indexesAllSimple.contains(indexName)) {
                // index all string fields, minus excluded ones
                // TODO XXX excluded ones...
                paths = null;
            } else {
                // index configured fields
                paths = fulltextConfiguration.propPathsByIndexSimple.get(indexName);
            }
            // get text from string properties
            String text = new TextExtractor().findText(document, paths);
            text = limitStringSize(text, fullTextFieldSizeLimit);
            String property = getFulltextPropertyName(SYSPROP_FULLTEXT_SIMPLE, indexName);
            for (DocumentModel doc : docsToUpdate) {
                session.setDocumentSystemProp(doc.getRef(), property, text);
            }
        }
    }

    protected void updateBinaryText(DocumentModel document, List<DocumentModel> docsToUpdate,
            FulltextConfiguration fulltextConfiguration) {
        // we extract binary text even if fulltext search is disabled,
        // because it is still used to inject into external indexers like Elasticsearch
        BlobsExtractor blobsExtractor = new BlobsExtractor();
        Map<Blob, String> blobsText = new IdentityHashMap<>();
        for (String indexName : fulltextConfiguration.indexNames) {
            if (!fulltextConfiguration.indexesAllBinary.contains(indexName)
                    && fulltextConfiguration.propPathsByIndexBinary.get(indexName) == null) {
                // nothing to do: index not configured for blob
                continue;
            }
            // get original text from all blobs
            blobsExtractor.setExtractorProperties(fulltextConfiguration.propPathsByIndexBinary.get(indexName),
                    fulltextConfiguration.propPathsExcludedByIndexBinary.get(indexName),
                    fulltextConfiguration.indexesAllBinary.contains(indexName));
            List<String> strings = new ArrayList<>();
            for (Blob blob : blobsExtractor.getBlobs(document)) {
                String string = blobsText.computeIfAbsent(blob, this::blobToText);
                strings.add(string);
            }
            String text = String.join("\n\n", strings);
            text = limitStringSize(text, fulltextConfiguration.fulltextFieldSizeLimit);
            String property = getFulltextPropertyName(SYSPROP_FULLTEXT_BINARY, indexName);
            for (DocumentModel doc : docsToUpdate) {
                session.setDocumentSystemProp(doc.getRef(), property, text);
            }
        }
    }

    /**
     * Converts the blob to text by calling a converter.
     */
    protected String blobToText(Blob blob) {
        try {
            ConversionService conversionService = Framework.getService(ConversionService.class);
            if (conversionService == null) {
                log.debug("No ConversionService available");
                return "";
            }
            BlobHolder blobHolder = conversionService.convert(ANY2TEXT_CONVERTER, new SimpleBlobHolder(blob), null);
            if (blobHolder == null) {
                return "";
            }
            Blob resultBlob = blobHolder.getBlob();
            if (resultBlob == null) {
                return "";
            }
            String string = resultBlob.getString();
            // strip '\0 chars from text
            if (string.indexOf('\0') >= 0) {
                string = string.replace("\0", " ");
            }
            return string;
        } catch (ConversionException | IOException e) {
            String msg = "Could not extract fulltext of file '" + blob.getFilename() + "' for document: " + docId + ": "
                    + e;
            log.warn(msg);
            log.debug(msg, e);
            return "";
        }
    }

    @SuppressWarnings("boxing")
    protected String limitStringSize(String string, int maxSize) {
        if (maxSize != 0 && string.length() > maxSize) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Fulltext extract of length: %s for document: %s truncated to length: %s",
                        string.length(), docId, maxSize));
            }
            string = string.substring(0, maxSize);
        }
        return string;
    }

    protected String getFulltextPropertyName(String name, String indexName) {
        if (!FULLTEXT_DEFAULT_INDEX.equals(indexName)) {
            name += '_' + indexName;
        }
        return name;
    }

    /**
     * Finds the text in a document (string properties).
     * <p>
     * This class is not thread-safe.
     *
     * @since 10.3
     */
    public static class TextExtractor {

        protected DocumentModel document;

        // paths for which we extract fulltext, or null for all
        protected Set<String> paths;

        // collected strings
        protected List<String> strings;

        /**
         * Finds text from the document for a given set of paths.
         * <p>
         * Paths must be specified with a schema prefix in all cases (normalized).
         * <p>
         * The returned string is a concatenation of all the text at the specified paths, separated by a newline.
         *
         * @param document the document
         * @param paths the paths, or {@code null} for all paths
         * @return a string with all the document's text
         */
        public String findText(DocumentModel document, Set<String> paths) {
            this.document = document;
            this.paths = paths;
            strings = new ArrayList<>();
            for (String schema : document.getSchemas()) {
                for (Property property : document.getPropertyObjects(schema)) {
                    String path = property.getField().getName().getPrefixedName();
                    if (!path.contains(":")) {
                        // add schema name as prefix if the schema doesn't have a prefix
                        path = property.getSchema().getName() + ":" + path;
                    }
                    findText(property, path);
                }
            }
            return String.join("\n", strings);
        }

        protected void findText(Property property, String path) {
            if (property instanceof StringProperty) {
                if (paths == null || paths.contains(path)) {
                    Serializable value = property.getValue();
                    if (value instanceof String) {
                        strings.add((String) value);
                    }
                }
            } else if (property instanceof ArrayProperty) {
                if (paths == null || paths.contains(path)) {
                    Serializable value = property.getValue();
                    if (value instanceof Object[]) {
                        for (Object v : (Object[]) value) {
                            if (v instanceof String) {
                                strings.add((String) v);
                            }
                        }
                    }
                }
            } else if (property instanceof ComplexProperty) {
                for (Property p : ((ComplexProperty) property).getChildren()) {
                    String pp = p.getField().getName().getPrefixedName();
                    findText(p, path + '/' + pp);
                }
            } else if (property instanceof ListProperty) {
                for (Property p : (ListProperty) property) {
                    findText(p, path + "/*");
                }
            }
        }
    }

}
