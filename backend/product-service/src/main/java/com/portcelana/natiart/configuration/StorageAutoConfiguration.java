package com.portcelana.natiart.configuration;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.portcelana.natiart.storage.Storage;
import com.portcelana.natiart.storage.StorageService;
import com.portcelana.natiart.storage.StorageServiceImpl;

@Configuration
public class StorageAutoConfiguration {
    @Bean
    public StorageService storageServiceImplAutoConfiguration(List<Storage> storages) {
        return new StorageServiceImpl(storages);
    }
}
