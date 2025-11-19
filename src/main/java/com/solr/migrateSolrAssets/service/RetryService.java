package com.solr.migrateSolrAssets.service;

import org.apache.solr.client.solrj.*;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;
import java.io.*;
import java.util.*;

@Service
public class RetryService {

    @Autowired @Qualifier("sourceSolrClient")
    private SolrClient sourceClient;

    @Autowired @Qualifier("targetSolrClient")
    private SolrClient targetClient;

    private static final String SKIPPED_LOG_FILE = "skipped_assets.log";

    public String retrySkipped(int assetType) throws Exception {
        File logFile = new File(SKIPPED_LOG_FILE);
        if (!logFile.exists()) return "No skipped_assets.log found.";

        List<String> failedIds = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("id=")) {
                    String id = line.split("id=")[1].split(" ")[0];
                    failedIds.add(id);
                }
            }
        }

        if (failedIds.isEmpty()) return "No skipped docs found.";

        List<SolrInputDocument> docs = new ArrayList<>();
        for (String id : failedIds) {
            SolrQuery query = new SolrQuery("id:" + id);
            QueryResponse response = sourceClient.query(query);
            if (!response.getResults().isEmpty()) {
                var doc = response.getResults().get(0);
                SolrInputDocument inputDoc = new SolrInputDocument();
                doc.forEach((f, v) -> {
                    if (!f.equals("_version_")) inputDoc.addField(f, v);
                });
                inputDoc.addField("solr_update_time_dt", new Date());
                docs.add(inputDoc);
            }
        }

        if (!docs.isEmpty()) {
            targetClient.add(docs);
            targetClient.commit();
        }

        return "Retried " + docs.size() + " skipped documents for asset_type=" + assetType;
    }
}

