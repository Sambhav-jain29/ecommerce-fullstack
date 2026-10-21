package com.Sambhav.ecommerce.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import com.Sambhav.ecommerce.dto.CreateProductRequest;
import com.Sambhav.ecommerce.dto.UpdateProductRequest;
import com.Sambhav.ecommerce.model.Product;
import com.Sambhav.ecommerce.service.ProductService;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.domain.Sort;
import jakarta.validation.Valid;;

@RestController
@RequestMapping("/products") // we are using the common prefix for all the endpoints in this controller. All
                             // the endpoints will start with /products.
@Validated // tells Spring to validate the request parameters and request body based on the
           // validation annotations in the model classes.
public class ProductController {
    private ProductService productService;

    public ProductController(ProductService productService) { // constructor injection: Spring will automatically inject
                                                              // an instance of ProductService into this
        // controller when it is created.
        this.productService = productService;

    }

    // @GetMapping // no need to specify the path here because we are using the
    // common prefix for
    // // all the endpoints in this controller
    // public List<Product> getProducts() { // URL= GET /products
    // return productService.getProducts();
    // }

    @GetMapping
    public Object getProducts(
            @RequestParam(required = false) String name,

            @RequestParam(required = false) @Positive(message = "categoryId must be greater than 0") Integer categoryId,

            @RequestParam(required = false) @PositiveOrZero(message = "minPrice cannot be negative") Double minPrice,

            @RequestParam(required = false) @PositiveOrZero(message = "maxPrice cannot be negative") Double maxPrice,

            @PageableDefault(size = 10) Pageable pageable) {

        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "minPrice cannot be greater than maxPrice");
        }
        // Validate maximum page size
        if (pageable.getPageSize() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size cannot exceed 100");
        }

        // Validate sorting fields
        for (Sort.Order order : pageable.getSort()) {
            String field = order.getProperty();

            if (!field.equals("id")
                    && !field.equals("name")
                    && !field.equals("price")) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid sort field: " + field);
            }
        }

        return productService.filterProducts(
                name, categoryId, minPrice, maxPrice, pageable);
    }

    @GetMapping("/{id}") // only mention the path variable here because we are using the common prefix
                         // for all the endpoints in this controller
    public ResponseEntity<Product> getProduct(@PathVariable int id) { // @PathVariable tells Spring to extract the value
                                                                      // of the id path
        // variable from the URL and pass it as an argument to the
        // getProduct method.
        // URL= GET /products/id
        // ResponseEntity= The method returns an HTTP response whose body can contain a
        // Product.ResponseEntity lets you control the HTTP response around that data.

        Product product = productService.getProductById(id);
        // if (product != null) { no need for this null check because we are throwing an
        // exception in the service layer if the product is not found.
        // exception will be handled by the GlobalExceptionHandler and a 404 response
        // will be returned.

        return ResponseEntity.ok(product); // 200

        // }
        // return ResponseEntity.notFound().build(); // 404

    }

    // @GetMapping //no (), no path needed here
    // public String getProductById(@RequestParam int id) { // @RequestParam is used
    // for query parameters to extract value.
    // // URL= GET /products?id=id
    // return "Product " + id;
    // }

    // @PostMapping
    // public ResponseEntity<Product> createProduct(@Valid @RequestBody Product
    // product) { // @RequestBody tells Spring to
    // // extract the product
    // // data
    // // from the request body and pass it as an argument to
    // // the createProduct method.
    // // Jackson is commonly used to convert between JSON and Java objects.
    // // URL= POST /products
    // // @Valid tells Spring:"When you receive this Product, check its validation
    // // rules."

    // Product createdProduct = productService.createProduct(product);
    // return ResponseEntity.status(201).body(createdProduct);//201 Created status
    // code indicates that the request has been fulfilled and has resulted in the
    // creation of a new resource.

    // }

    @PostMapping
    public ResponseEntity<Product> createProduct(
            @Valid @RequestBody CreateProductRequest request) {

        Product createdProduct = productService.createProduct(request);

        return ResponseEntity.status(201).body(createdProduct);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Product> updateProduct(
            @PathVariable int id,
            @Valid @RequestBody UpdateProductRequest request) {// DTO is a small class used to carry only the data we
                                                               // need.
        // For PATCH, UpdateProductRequest carries only the fields we want to change,
        // instead of sending the complete Product object.

        Product product = productService.updateProduct(id, request);
        // if (product != null) {

        return ResponseEntity.ok(product);

        // }
        // return ResponseEntity.notFound().build();

    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable int id) {
        // boolean deleted =
        productService.deleteProduct(id);

        // if (deleted) {

        return ResponseEntity.noContent().build();// http status code 204 No Content, indicating that the
                                                  // request was successful but there is no content to return in
                                                  // the response body.

        // }
        // return ResponseEntity.notFound().build();
    }
}
/*
 * # Product Search, Filtering, Sorting, and Pagination — Revision Notes
 * 
 * ## 1. Why do we need these features?
 * 
 * Imagine our e-commerce website has 10,000 products.
 * 
 * If the frontend requests every product at once, the response can become very
 * large. Loading the page may take longer, and the backend has to send more
 * data than the user needs.
 * 
 * We solve this using four features:
 * 
 * 1. **Search** — find products by a word or name.
 * 2. **Filtering** — show only products that match certain conditions.
 * 3. **Sorting** — arrange products in a particular order.
 * 4. **Pagination** — return products in small groups instead of all at once.
 * 
 * These features help the user find products quickly and make the application
 * more efficient.
 * 
 * ---
 * 
 * ## 2. Product Search
 * 
 * ### What does search mean?
 * 
 * Search finds products that match the word entered by the user.
 ** 
 * Example:** The user searches for `phone`.
 * 
 * The backend might return:
 * - Samsung Phone
 * - iPhone
 * - OnePlus Phone
 * 
 * ### How does it work?
 * 
 * 1. The user enters a search word in React.
 * 2. React sends the word to the backend through an API request.
 * 3. The controller receives the search word.
 * 4. The service handles the request.
 * 5. The repository searches the database.
 * 6. The backend returns matching products.
 * 
 * Example API:
 * 
 * `GET /api/products?search=phone`
 * 
 * Here, `search=phone` is a query parameter. It passes extra information
 * through the URL.
 * 
 * ### Important point
 * 
 * Search does not mean downloading all products and searching through them in
 * React. Normally, the backend asks the database to find the matching products.
 * 
 * ---
 * 
 * ## 3. Product Filtering
 * 
 * ### What does filtering mean?
 * 
 * Filtering means showing only products that satisfy a condition.
 ** 
 * Example:** A user wants:
 * - Products priced between ₹500 and ₹2,000.
 * - Only products in the Electronics category.
 * - Only products currently in stock.
 * 
 * Instead of displaying every product, we display only products that match the
 * selected conditions.
 * 
 * Example API:
 * 
 * `GET /api/products?category=Electronics&minPrice=500&maxPrice=2000`
 * 
 * We can also combine search and filtering:
 * 
 * `GET /api/products?search=phone&minPrice=5000&maxPrice=30000`
 * 
 * This asks for products matching `phone` within the specified price range.
 * 
 * ### Search vs. filtering
 * 
 * - Search: "Find products matching the word phone."
 * - Filtering: "Show only products in this price range."
 * 
 * We can use both together.
 * 
 * ---
 * 
 * ## 4. Product Sorting
 * 
 * ### What does sorting mean?
 * 
 * Sorting means arranging products in a particular order.
 * 
 * Examples:
 * - Price: low to high.
 * - Price: high to low.
 * - Newest products first.
 * - Product name: A to Z.
 * 
 * Example API:
 * 
 * `GET /api/products?sortBy=price&direction=asc`
 * 
 * Here:
 * - `sortBy=price` means sort using the price field.
 * - `direction=asc` means ascending order, from low to high.
 * 
 * For descending order:
 * 
 * `GET /api/products?sortBy=price&direction=desc`
 * 
 * ### Important security and correctness point
 * 
 * The backend should allow only supported sorting fields, such as `price`,
 * `name`, or `createdAt`.
 * 
 * We should not blindly accept any field name from the user. The backend must
 * validate the requested sorting options.
 * 
 * ---
 * 
 * ## 5. Pagination
 * 
 * ### What does pagination mean?
 * 
 * Pagination means splitting a large list into smaller pages.
 * 
 * Suppose the database contains 100 products, and we want to display 10
 * products per page.
 * 
 * - Page 1: products 1–10.
 * - Page 2: products 11–20.
 * - Page 3: products 21–30.
 * 
 * Instead of sending all 100 products, the backend sends only the requested
 * page.
 * 
 * Example API:
 * 
 * `GET /api/products?page=0&size=10`
 * 
 * In Spring Data, the page number normally starts at **0**, not 1.
 * 
 * - `page=0`: first page.
 * - `page=1`: second page.
 * - `size=10`: up to 10 products per page.
 * 
 * ### Why is pagination useful?
 * 
 * 1. Smaller responses.
 * 2. Less data transferred over the network.
 * 3. Less data to display at once in React.
 * 4. Better handling of large product lists.
 * 
 * Pagination does not automatically make every database query fast, but it
 * helps avoid returning an unnecessarily large number of records.
 * 
 * ---
 * 
 * ## 6. How these four features work together
 * 
 * Example:
 * 
 * `GET
 * /api/products?search=phone&category=Electronics&minPrice=5000&maxPrice=30000&
 * sortBy=price&direction=asc&page=0&size=10`
 * 
 * This request means:
 * 
 * 1. Find products matching `phone`.
 * 2. Keep products in the Electronics category.
 * 3. Keep prices between ₹5,000 and ₹30,000.
 * 4. Arrange them from lowest price to highest.
 * 5. Return the first page, containing up to 10 products.
 * 
 * The database query should apply the relevant conditions and ordering before
 * returning the requested page.
 * 
 * ---
 * 
 * ## 7. How Spring Boot supports these features
 * 
 * In a typical Spring Boot application, the work is divided between different
 * layers.
 * 
 * ### Controller
 * 
 * Receives the request and its query parameters.
 * 
 * Example responsibility: read `page`, `size`, `search`, and sorting options.
 * 
 * ### Service
 * 
 * Handles the application rules.
 * 
 * Example responsibility: check whether the sorting field is allowed and
 * coordinate the product search.
 * 
 * ### Repository
 * 
 * Communicates with the database.
 * 
 * Spring Data JPA supports pagination and sorting through `Pageable`,
 * `PageRequest`, and `Sort`.
 * 
 * A repository can also define queries for searching and filtering.
 * 
 * ### Database
 * 
 * Finds the matching rows and applies ordering and pagination as supported by
 * the query.
 ** 
 * Remember:** The controller receives the request; the service handles the
 * rules; the repository retrieves the data.
 * 
 * ---
 * 
 * ## 8. Important Spring Data terms
 * 
 * - `Pageable`: describes which page we need, how many records it should
 * contain, and how they should be sorted.
 * - `PageRequest`: commonly used to create a `Pageable` object.
 * - `Sort`: describes the requested sorting order.
 * - `Page<T>`: contains the records for the requested page plus information
 * such as the total number of pages and total matching records.
 * - `List<T>`: contains a list of records but does not, by itself, provide
 * pagination information.
 * 
 * For example, `Page<Product>` can tell React both which products to display
 * and how many pages exist.
 * 
 * ---
 * 
 * ## 9. Important checks before releasing the API
 * 
 * - Validate that the page number is not negative.
 * - Set a reasonable maximum page size so a user cannot request an enormous
 * number of records.
 * - Allow only valid sorting fields and directions.
 * - Check that search and filter combinations return the correct products.
 * - Handle an empty result correctly.
 * - Use a consistent sort order when paginating. Sorting by a unique field as a
 * secondary sort can help prevent products with equal prices from moving
 * unpredictably between pages.
 * - Make sure the API returns only the products the current user is allowed to
 * see, if access restrictions apply.
 * 
 * ---
 * 
 * ## 10. Interview questions and short answers
 ** 
 * Q1. What is the difference between search and filtering?**
 * 
 * Search finds records matching a search term. Filtering restricts the results
 * using conditions such as category, price, or stock availability.
 ** 
 * Q2. Why do we use pagination?**
 * 
 * To avoid returning a huge number of records in a single response, especially
 * when a database contains many products.
 ** 
 * Q3. What is `Pageable` in Spring Data?**
 * 
 * It represents the requested page, page size, and sorting information.
 ** 
 * Q4. What is the difference between `Page` and `List`?**
 * 
 * A `List` contains records. A `Page` also provides pagination details, such as
 * the total number of matching records and pages.
 ** 
 * Q5. Can we combine search, filtering, sorting, and pagination?**
 * 
 * Yes. We can apply the search term and filters, sort the matching products,
 * and then return the requested page.
 ** 
 * Q6. Should React fetch every product and perform all these operations
 * itself?**
 * 
 * Usually not for a large catalogue. The backend should query the database and
 * return the matching page so React receives only the data it needs.
 * 
 * ---
 * 
 * ## 11. Our project checklist
 * 
 * Before moving to React, verify whether our existing backend already supports:
 * 
 * - [ ] Product search.
 * - [ ] Category and price filtering, as applicable to our product model.
 * - [ ] Sorting by supported fields.
 * - [ ] Pagination with a sensible maximum page size.
 * - [ ] Combining these features in one request.
 * - [ ] Correct empty-result and invalid-parameter handling.
 * - [ ] Tests for the important combinations.
 ** 
 * Revision summary:** Search finds products; filtering narrows the results;
 * sorting arranges them; pagination returns a small section of the results.
 * 
 * Our goal is to verify the existing controller, service, repository, and tests
 * first. We should implement only what is missing rather than rebuilding
 * functionality that already works.
 */