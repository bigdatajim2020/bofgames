package com.bof.games.web.rest;

import com.bof.games.BofgamesApp;
import com.bof.games.domain.Cart;
import com.bof.games.repository.CartRepository;
import com.bof.games.repository.search.CartSearchRepository;
import com.bof.games.web.rest.errors.ExceptionTranslator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Validator;

import javax.persistence.EntityManager;
import java.util.Collections;
import java.util.List;

import static com.bof.games.web.rest.TestUtil.createFormattingConversionService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.queryStringQuery;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the {@link CartResource} REST controller.
 */
@SpringBootTest(classes = BofgamesApp.class)
public class CartResourceIT {

    private static final Boolean DEFAULT_EXPIRED = false;
    private static final Boolean UPDATED_EXPIRED = true;

    private static final Boolean DEFAULT_ORDERED = false;
    private static final Boolean UPDATED_ORDERED = true;

    @Autowired
    private CartRepository cartRepository;

    /**
     * This repository is mocked in the com.bof.games.repository.search test package.
     *
     * @see com.bof.games.repository.search.CartSearchRepositoryMockConfiguration
     */
    @Autowired
    private CartSearchRepository mockCartSearchRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    @Autowired
    private Validator validator;

    private MockMvc restCartMockMvc;

    private Cart cart;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final CartResource cartResource = new CartResource(cartRepository, mockCartSearchRepository);
        this.restCartMockMvc = MockMvcBuilders.standaloneSetup(cartResource)
            .setCustomArgumentResolvers(pageableArgumentResolver)
            .setControllerAdvice(exceptionTranslator)
            .setConversionService(createFormattingConversionService())
            .setMessageConverters(jacksonMessageConverter)
            .setValidator(validator).build();
    }

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Cart createEntity(EntityManager em) {
        Cart cart = new Cart()
            .expired(DEFAULT_EXPIRED)
            .ordered(DEFAULT_ORDERED);
        return cart;
    }
    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Cart createUpdatedEntity(EntityManager em) {
        Cart cart = new Cart()
            .expired(UPDATED_EXPIRED)
            .ordered(UPDATED_ORDERED);
        return cart;
    }

    @BeforeEach
    public void initTest() {
        cart = createEntity(em);
    }

    @Test
    @Transactional
    public void createCart() throws Exception {
        int databaseSizeBeforeCreate = cartRepository.findAll().size();

        // Create the Cart
        restCartMockMvc.perform(post("/api/carts")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(cart)))
            .andExpect(status().isCreated());

        // Validate the Cart in the database
        List<Cart> cartList = cartRepository.findAll();
        assertThat(cartList).hasSize(databaseSizeBeforeCreate + 1);
        Cart testCart = cartList.get(cartList.size() - 1);
        assertThat(testCart.isExpired()).isEqualTo(DEFAULT_EXPIRED);
        assertThat(testCart.isOrdered()).isEqualTo(DEFAULT_ORDERED);

        // Validate the Cart in Elasticsearch
        verify(mockCartSearchRepository, times(1)).save(testCart);
    }

    @Test
    @Transactional
    public void createCartWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = cartRepository.findAll().size();

        // Create the Cart with an existing ID
        cart.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restCartMockMvc.perform(post("/api/carts")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(cart)))
            .andExpect(status().isBadRequest());

        // Validate the Cart in the database
        List<Cart> cartList = cartRepository.findAll();
        assertThat(cartList).hasSize(databaseSizeBeforeCreate);

        // Validate the Cart in Elasticsearch
        verify(mockCartSearchRepository, times(0)).save(cart);
    }


    @Test
    @Transactional
    public void getAllCarts() throws Exception {
        // Initialize the database
        cartRepository.saveAndFlush(cart);

        // Get all the cartList
        restCartMockMvc.perform(get("/api/carts?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(cart.getId().intValue())))
            .andExpect(jsonPath("$.[*].expired").value(hasItem(DEFAULT_EXPIRED.booleanValue())))
            .andExpect(jsonPath("$.[*].ordered").value(hasItem(DEFAULT_ORDERED.booleanValue())));
    }
    
    @Test
    @Transactional
    public void getCart() throws Exception {
        // Initialize the database
        cartRepository.saveAndFlush(cart);

        // Get the cart
        restCartMockMvc.perform(get("/api/carts/{id}", cart.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(cart.getId().intValue()))
            .andExpect(jsonPath("$.expired").value(DEFAULT_EXPIRED.booleanValue()))
            .andExpect(jsonPath("$.ordered").value(DEFAULT_ORDERED.booleanValue()));
    }

    @Test
    @Transactional
    public void getNonExistingCart() throws Exception {
        // Get the cart
        restCartMockMvc.perform(get("/api/carts/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateCart() throws Exception {
        // Initialize the database
        cartRepository.saveAndFlush(cart);

        int databaseSizeBeforeUpdate = cartRepository.findAll().size();

        // Update the cart
        Cart updatedCart = cartRepository.findById(cart.getId()).get();
        // Disconnect from session so that the updates on updatedCart are not directly saved in db
        em.detach(updatedCart);
        updatedCart
            .expired(UPDATED_EXPIRED)
            .ordered(UPDATED_ORDERED);

        restCartMockMvc.perform(put("/api/carts")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedCart)))
            .andExpect(status().isOk());

        // Validate the Cart in the database
        List<Cart> cartList = cartRepository.findAll();
        assertThat(cartList).hasSize(databaseSizeBeforeUpdate);
        Cart testCart = cartList.get(cartList.size() - 1);
        assertThat(testCart.isExpired()).isEqualTo(UPDATED_EXPIRED);
        assertThat(testCart.isOrdered()).isEqualTo(UPDATED_ORDERED);

        // Validate the Cart in Elasticsearch
        verify(mockCartSearchRepository, times(1)).save(testCart);
    }

    @Test
    @Transactional
    public void updateNonExistingCart() throws Exception {
        int databaseSizeBeforeUpdate = cartRepository.findAll().size();

        // Create the Cart

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restCartMockMvc.perform(put("/api/carts")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(cart)))
            .andExpect(status().isBadRequest());

        // Validate the Cart in the database
        List<Cart> cartList = cartRepository.findAll();
        assertThat(cartList).hasSize(databaseSizeBeforeUpdate);

        // Validate the Cart in Elasticsearch
        verify(mockCartSearchRepository, times(0)).save(cart);
    }

    @Test
    @Transactional
    public void deleteCart() throws Exception {
        // Initialize the database
        cartRepository.saveAndFlush(cart);

        int databaseSizeBeforeDelete = cartRepository.findAll().size();

        // Delete the cart
        restCartMockMvc.perform(delete("/api/carts/{id}", cart.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<Cart> cartList = cartRepository.findAll();
        assertThat(cartList).hasSize(databaseSizeBeforeDelete - 1);

        // Validate the Cart in Elasticsearch
        verify(mockCartSearchRepository, times(1)).deleteById(cart.getId());
    }

    @Test
    @Transactional
    public void searchCart() throws Exception {
        // Initialize the database
        cartRepository.saveAndFlush(cart);
        when(mockCartSearchRepository.search(queryStringQuery("id:" + cart.getId())))
            .thenReturn(Collections.singletonList(cart));
        // Search the cart
        restCartMockMvc.perform(get("/api/_search/carts?query=id:" + cart.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(cart.getId().intValue())))
            .andExpect(jsonPath("$.[*].expired").value(hasItem(DEFAULT_EXPIRED.booleanValue())))
            .andExpect(jsonPath("$.[*].ordered").value(hasItem(DEFAULT_ORDERED.booleanValue())));
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Cart.class);
        Cart cart1 = new Cart();
        cart1.setId(1L);
        Cart cart2 = new Cart();
        cart2.setId(cart1.getId());
        assertThat(cart1).isEqualTo(cart2);
        cart2.setId(2L);
        assertThat(cart1).isNotEqualTo(cart2);
        cart1.setId(null);
        assertThat(cart1).isNotEqualTo(cart2);
    }
}
