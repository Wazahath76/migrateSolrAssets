package com.solr.migrateSolrAssets.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.*;
import java.util.*;

@Service
public class CheckpointService {

    @Value("${solr.checkpoint-file:checkpoint.log}")
    private String checkpointFile;

    @Value("${solr.skipped-file:skipped_assets.log}")
    private String skippedFile;

    /** Read last processed row for a given asset type */
    public synchronized int readCheckpoint(int assetType) {
        File file = new File(checkpointFile);
        if (!file.exists()) return 0;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            return br.lines()
                    .filter(line -> line.startsWith(assetType + ":"))
                    .map(line -> Integer.parseInt(line.split(":")[1]))
                    .findFirst()
                    .orElse(0);
        } catch (Exception e) {
            System.err.println("Failed to read checkpoint: " + e.getMessage());
            return 0;
        }
    }

    /** Save last processed row for a given asset type */
    public synchronized void saveCheckpoint(int assetType, int lastProcessedRow) {
        try {
            Map<Integer, Integer> checkpoints = new HashMap<>();
            File file = new File(checkpointFile);

            if (file.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    br.lines().forEach(line -> {
                        String[] parts = line.split(":");
                        checkpoints.put(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                    });
                }
            }

            checkpoints.put(assetType, lastProcessedRow);

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
                for (var entry : checkpoints.entrySet()) {
                    bw.write(entry.getKey() + ":" + entry.getValue());
                    bw.newLine();
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to save checkpoint: " + e.getMessage());
        }
    }

    /** Log skipped asset with reason */
    public synchronized void logSkippedAsset(int assetType, String assetId, String reason) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(skippedFile, true))) {
            String logEntry = String.format("%s | assetType=%d | id=%s | reason=%s",
                    new Date(), assetType, assetId, reason);
            bw.write(logEntry);
            bw.newLine();
        } catch (IOException e) {
            System.err.println("Failed to log skipped asset: " + e.getMessage());
        }
    }
}
