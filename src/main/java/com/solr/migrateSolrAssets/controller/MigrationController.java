package com.solr.migrateSolrAssets.controller;

import com.solr.migrateSolrAssets.model.MigrationRequest;
import com.solr.migrateSolrAssets.service.MigrationService;
import com.solr.migrateSolrAssets.service.RetryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/migrate")
public class MigrationController {

    @Autowired
    private MigrationService migrationService;

    @Autowired
    private RetryService retryService;

    // Existing: Batch migrate assets by type and row range
    @PostMapping("/batch")
    public ResponseEntity<String> migrate(@RequestBody MigrationRequest request) {
        try {
            String result = migrationService.migrate(
                    request.getAssetType(),
                    request.getStartRow(),
                    request.getEndRow()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Migration failed: " + e.getMessage());
        }

        /*
        http://localhost:8080/migrate/batch

        {
            "assetType": 1,
             "startRow": 0,
            "endRow": 500
        }
         */
    }

    //Existing: Retry migration for skipped assets
    @PostMapping("/retry-skipped")
    public ResponseEntity<String> retrySkipped(@RequestBody RetryRequest request) {
        try {
            String result = retryService.retrySkipped(request.getAssetType());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Retry failed: " + e.getMessage());
        }

        /*
        http://localhost:8080/migrate/retry-skipped
        {
            "assetType": 1
        }
         */
    }

    //New: Copy single document by ID (source → target core)
    @GetMapping("/id/{id}")
    public ResponseEntity<String> copyById(@PathVariable String id) {
        try {
            migrationService.copyDocumentById(id);
            return ResponseEntity.ok("Document copied successfully: " + id);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to copy document: " + e.getMessage());
        }

        /*
        Example

        http://localhost:8080/migrate/id/0-9-244
         */
    }

    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteAssets(@RequestParam String query) {
        try {
            migrationService.deleteAssets(query);
            return ResponseEntity.ok("Deleted assets matching query: " + query);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete assets: " + e.getMessage());
        }

        /*
        http://localhost:8080/migrate/delete?query=<solr_query>

        http://localhost:8080/migrate/delete?query=id:0-8-homepage
        http://localhost:8080/migrate/delete?query=asset_type:1
        http://localhost:8080/migrate/delete?query=asset_type:(0 1 101)
        http://localhost:8080/migrate/delete?query=asset_type:0 AND kids_safe:true
         */

    }


    // Inner class for retry request
    public static class RetryRequest {
        private int assetType;
        public int getAssetType() { return assetType; }
        public void setAssetType(int assetType) { this.assetType = assetType; }
    }
}
