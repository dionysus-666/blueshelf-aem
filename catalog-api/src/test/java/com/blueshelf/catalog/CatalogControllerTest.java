package com.blueshelf.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CatalogController.class)
@Import(CatalogRepository.class)
class CatalogControllerTest {
    @Autowired MockMvc mvc;

    @Test void listsTvs() throws Exception {
        mvc.perform(get("/api/products?category=tvs&size=3"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.items", hasSize(3)))
           .andExpect(jsonPath("$.total", is(6)))
           .andExpect(jsonPath("$.items[0].category", is("tvs")));
    }
    @Test void getsBySku() throws Exception {
        mvc.perform(get("/api/products/BS1001")).andExpect(status().isOk()).andExpect(jsonPath("$.brand", is("Samsung")));
        mvc.perform(get("/api/products/NOPE")).andExpect(status().isNotFound());
    }
}
