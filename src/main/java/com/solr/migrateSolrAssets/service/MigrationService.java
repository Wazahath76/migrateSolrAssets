package com.solr.migrateSolrAssets.service;

import org.apache.solr.client.solrj.*;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class MigrationService {

    @Autowired @Qualifier("sourceSolrClient")
    private SolrClient sourceClient;

    @Autowired @Qualifier("targetSolrClient")
    private SolrClient targetClient;

    @Autowired
    private CheckpointService checkpointService;

    @Value("${solr.batch-size}")
    private int batchSize;

    @Value("${solr.threads}")
    private int threads;

    private static final String SKIPPED_LOG_FILE = "skipped_assets.log";

    public String migrate(int assetType, int startRow, int endRow) throws Exception {
        int checkpoint = checkpointService.readCheckpoint(assetType);
        int currentStart = Math.max(startRow, checkpoint);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int start = currentStart; start < endRow; start += batchSize) {
            final int batchStart = start;
            futures.add(executor.submit(() -> processBatch(assetType, batchStart)));
        }

        for (Future<?> f : futures) f.get();
        executor.shutdown();
        targetClient.commit();

        return "Migration completed for asset_type: " + assetType;
    }

    private void processBatch(int assetType, int start) {
        try {
            SolrQuery query = new SolrQuery("asset_type:" + assetType);
            query.setStart(start);
            query.setRows(batchSize);

            QueryResponse response = sourceClient.query(query);
            var docs = response.getResults();

            if (docs.isEmpty()) return;

            List<SolrInputDocument> inputDocs = new ArrayList<>();
            List<String> skippedDocs = new ArrayList<>();

            for (var doc : docs) {
                try {
                    SolrInputDocument inputDoc = new SolrInputDocument();
                    doc.forEach((field, value) -> {
                        if (!field.equals("_version_"))
                            inputDoc.addField(field, value);
                    });
                    inputDoc.addField("solr_update_time_dt", new Date());
                    inputDocs.add(inputDoc);
                } catch (Exception e) {
                    String id = String.valueOf(doc.getFieldValue("id"));
                    skippedDocs.add("id=" + id + " reason=" + e.getMessage());
                }
            }

            // Add to target Solr
            try {
                targetClient.add(inputDocs);
            } catch (Exception e) {
                logSkippedDocs(docs.stream()
                        .map(d -> "id=" + d.getFieldValue("id") + " reason=BatchAddFail")
                        .collect(Collectors.toList()));
                throw e;
            }

            checkpointService.saveCheckpoint(assetType, start + batchSize);

            if (!skippedDocs.isEmpty()) logSkippedDocs(skippedDocs);

            System.out.println("✔️ Migrated batch | asset_type=" + assetType +
                    " | start=" + start +
                    " | migrated=" + inputDocs.size() +
                    " | skipped=" + skippedDocs.size());

        } catch (Exception e) {
            System.err.println("Error in batch start=" + start + " : " + e.getMessage());
        }
    }

    public void copyDocumentById(String id) throws Exception {
        // Step 1: Fetch document by ID from source core
        SolrQuery query = new SolrQuery("id:" + id);
        QueryResponse response = sourceClient.query(query);

        if (response.getResults().isEmpty()) {
            throw new RuntimeException("Document with ID " + id + " not found in source core");
        }

        var solrDoc = response.getResults().get(0);

        // Step 2: Convert SolrDocument → SolrInputDocument
        SolrInputDocument inputDoc = new SolrInputDocument();
        for (String fieldName : solrDoc.getFieldNames()) {
            if (!fieldName.equals("_version_")) {
                inputDoc.addField(fieldName, solrDoc.getFieldValue(fieldName));
            }
        }

        // Step 3: Add to target core
        targetClient.add(inputDoc);
        targetClient.commit();

        System.out.println("Successfully copied document ID " + id + " from source → target core");
    }

    public void deleteAssets(String queryString) {
        try {
            System.out.println("Deleting assets from target core with query: " + queryString);
            targetClient.deleteByQuery(queryString);
            targetClient.commit();
            System.out.println("Deleted assets where " + queryString);
        } catch (Exception e) {
            System.err.println("Failed to delete assets: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }


    private synchronized void logSkippedDocs(List<String> skippedDocs) {
        if (skippedDocs.isEmpty()) return;
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(SKIPPED_LOG_FILE, true))) {
            for (String entry : skippedDocs) {
                bw.write(entry);
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Failed to log skipped docs: " + e.getMessage());
        }
    }
}
