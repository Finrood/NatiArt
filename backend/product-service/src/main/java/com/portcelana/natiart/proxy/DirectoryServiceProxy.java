package com.portcelana.natiart.proxy;

import com.portcelana.natiart.dto.UserDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@Service
public class DirectoryServiceProxy {
    private final static String SERVICE_NAME = "DIRECTORY-SERVICE";

    @Value("${nati.proxy.directory.baseUrl}")
    private String DIRECTORY_SERICE_URL;

    private final RestTemplate restTemplate;

    public DirectoryServiceProxy(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    public UserDto getCurrentUser() {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        final HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        try {
            final ResponseEntity<UserDto> response = restTemplate.exchange(
                    DIRECTORY_SERICE_URL + "/users/current",
                    HttpMethod.POST,
                    requestEntity,
                    UserDto.class);
            return response.getBody();
        } catch (HttpClientErrorException | HttpServerErrorException error) {
            if (error.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new IllegalArgumentException(error.getMessage());
            } else {
                throw new HttpServerErrorException(error.getStatusCode(), error.getStatusText());
            }
        }
    }
}
