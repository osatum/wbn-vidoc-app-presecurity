package com.osatum.wbn.infrastructure.elasticsearch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ElasticsearchIndexRecreator {
    private final ElasticsearchOperations elasticsearchOperations;


    public void recreateIndex(Class<?> documentClass) {
        IndexOperations indexOps = elasticsearchOperations.indexOps(documentClass);

        if (indexOps.exists()) {
            indexOps.delete();
        }
        indexOps.createWithMapping();
    }
}
