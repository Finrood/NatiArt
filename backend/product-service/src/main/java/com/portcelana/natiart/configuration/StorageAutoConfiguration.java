package com.portcelana.natiart.configuration;

import com.portcelana.natiart.storage.Storage;
import com.portcelana.natiart.storage.StorageService;
import com.portcelana.natiart.storage.StorageServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class StorageAutoConfiguration {
    @Bean
    public StorageService storageServiceImplAutoConfiguration(List<Storage> storages) {
        return new StorageServiceImpl(storages);
    }
}
