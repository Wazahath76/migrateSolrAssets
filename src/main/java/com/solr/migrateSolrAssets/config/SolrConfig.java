package com.solr.migrateSolrAssets.config;

import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.SolrClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration
public class SolrConfig {

    @Value("${solr.source.url}")
    private String sourceUrl;

    @Value("${solr.target.url}")
    private String targetUrl;

    @Bean(name = "sourceSolrClient")
    public SolrClient sourceSolrClient() {
        return new HttpSolrClient.Builder(sourceUrl).build();
    }

    @Bean(name = "targetSolrClient")
    public SolrClient targetSolrClient() {
        return new HttpSolrClient.Builder(targetUrl).build();
    }
}




