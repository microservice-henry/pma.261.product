package store.product;

import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository repository;

    @CachePut(value = "products", key = "#result.id()")
    public ProductResponse create(ProductRequest request) {
        Product product = new Product(null, request.name(), request.price(), request.unit());
        return ProductResponse.from(repository.save(product));
    }

    public List<ProductResponse> list(String name) {
        List<Product> products = (name == null || name.isBlank())
                ? repository.findAll()
                : repository.findByNameContainingIgnoreCase(name);
        return products.stream().map(ProductResponse::from).toList();
    }

    @Cacheable(value = "products", key = "#id")
    public ProductResponse get(UUID id) {
        return repository.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    @CacheEvict(value = "products", key = "#id")
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        repository.deleteById(id);
    }
}
