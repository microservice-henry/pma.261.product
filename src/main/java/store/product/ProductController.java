package store.product;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(
            @RequestHeader("id-account") String idAccount,
            @Valid @RequestBody ProductRequest request
    ) {
        return service.create(request);
    }

    @GetMapping
    public List<ProductResponse> list(
            @RequestHeader("id-account") String idAccount,
            @RequestParam(required = false) String name
    ) {
        return service.list(name);
    }

    @GetMapping("/{id}")
    public ProductResponse get(
            @RequestHeader("id-account") String idAccount,
            @PathVariable UUID id
    ) {
        return service.get(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader("id-account") String idAccount,
            @PathVariable UUID id
    ) {
        service.delete(id);
    }
}
