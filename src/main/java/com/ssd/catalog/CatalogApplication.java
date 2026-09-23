package com.ssd.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the product catalog service.
 *
 * <p>Sits in the root package so component scanning picks up every feature
 * package beneath it (brand, category, product, ...). Code is organised by
 * feature rather than by layer: see each package's {@code package-info.java}.
 */
@SpringBootApplication
public class CatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
