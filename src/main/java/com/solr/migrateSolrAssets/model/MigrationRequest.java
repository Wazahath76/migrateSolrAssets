package com.solr.migrateSolrAssets.model;

import lombok.Data;

@Data
public class MigrationRequest {
    private int assetType;
    private int startRow;
    private int endRow;
}

