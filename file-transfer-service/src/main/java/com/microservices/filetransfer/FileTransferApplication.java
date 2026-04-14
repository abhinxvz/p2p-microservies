package com.microservices.filetransfer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class FileTransferApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileTransferApplication.class, args);
    }
}
