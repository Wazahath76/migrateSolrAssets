package com.solr.migrateSolrAssets.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MigrationRequest {
    private int assetType;
    private int startRow;
    private int endRow;
}

