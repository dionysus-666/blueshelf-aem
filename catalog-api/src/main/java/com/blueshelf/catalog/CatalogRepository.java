package com.blueshelf.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** In-memory catalog seeded from JSON. A real one is a DB / search index / PIM. */
@Repository
public class CatalogRepository {
    private final List<Product> products;
    private final List<Store> stores;

    public CatalogRepository(ObjectMapper mapper) {
        try {
            products = mapper.readValue(new ClassPathResource("products.json").getInputStream(), new TypeReference<>() {});
            stores = mapper.readValue(new ClassPathResource("stores.json").getInputStream(), new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    public List<Product> products() { return products; }
    public List<Store> stores() { return stores; }
}
