package com.Sambhav.ecommerce.controller;

import com.Sambhav.ecommerce.dto.PaymentResponse;
import com.Sambhav.ecommerce.service.PaymentService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/orders/{orderId}")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse payForOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer orderId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        return paymentService.payForOrder(
                userDetails.getUsername(),
                orderId,
                idempotencyKey);
    }

    @GetMapping("/orders/{orderId}")
    public java.util.List<PaymentResponse> getPaymentHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer orderId) {

        return paymentService.getPaymentHistory(
                userDetails.getUsername(),
                orderId);
    }

}
/*
 * PaymentController — Brief Revision Notes
 * 
 * ## 1. What does PaymentController do?
 * 
 * `PaymentController` handles HTTP requests related to payments, delegating the
 * actual logic to `PaymentService`.
 * 
 * ## 2. Endpoints
 * 
 * - `POST /payments/orders/{orderId}`: Initiate a payment for an order.
 * - `GET /payments/orders/{orderId}`: Retrieve the payment history for an
 * order.
 * 
 * ## 3. Important concepts
 ** 
 * Authentication**
 * 
 * The controller uses `@AuthenticationPrincipal` to access the currently
 * logged-in user's details.
 ** 
 * Idempotency-Key**
 * 
 * The controller expects an `Idempotency-Key` header for each payment request.
 ** 
 * Order ownership**
 * 
 * The controller ensures that the requested order belongs to the current user
 * before processing any payment or history request.
 * 
 * ### Remember
 ** 
 * Validate the idempotency key → check user authentication → verify order
 * ownership → delegate to PaymentService.**
 */

// //# E-Commerce Backend: Locking, Idempotency, Transactions & Race Conditions

// ## 1. First understand the real problem: Race conditions

// A race condition happens when two requests work with the same data at nearly
// the same time, and the final result depends on how their operations overlap.

// Imagine our e-commerce store has **5 phones in stock**.

// Two customers place orders at nearly the same time:

// - Customer A wants 4 phones.
// - Customer B wants 3 phones.

// The store has only 5 phones, so both orders cannot succeed.

// ### What can go wrong without proper protection?

// Both requests might read the stock before either request updates it:

// | Step | Request A | Request B |
// |---|---|---|
// | 1 | Reads stock = 5 | |
// | 2 | | Reads stock = 5 |
// | 3 | Checks that 4 ≤ 5 | |
// | 4 | | Checks that 3 ≤ 5 |
// | 5 | Deducts 4 | |
// | 6 | | Deducts 3 using its earlier decision |

// The application has now allowed orders for 7 phones even though only 5
// existed.

// This is a race condition.

// **Important:** Simply checking the stock before placing an order is not
// enough. Another request can change the stock immediately after that check.

// Our project needs to prevent this.

// ---

// ## 2. Database transactions: `@Transactional`

// ### What is a transaction?

// A transaction is a group of database operations that should succeed together
// or be rolled back together.

// Imagine checkout needs to:

// 1. Deduct product stock.
// 2. Create an order.
// 3. Save order items.
// 4. Clear the cart.

// Suppose the stock deduction succeeds, but saving the order fails.

// Without a transaction, we could lose stock without having a completed order.

// With a transaction, if the failure causes the transaction to roll back, the
// database changes made within that transaction are undone.

// ### In our project

// Our checkout method uses:

// @Transactional
// public OrderResponse checkout(String email) {
// // Check the cart
// // Deduct stock
// // Create order and order items
// // Clear the cart
// }

// Think of `@Transactional` as saying:

// "Treat the database work inside this method as one unit."

// Spring manages starting the transaction and committing or rolling it back
// when the method finishes, according to the transaction rules.

// ### Interview answer

// **Q: Why did you use `@Transactional` in checkout?**

// I use it to keep stock changes, order creation, order items, and cart
// clearing consistent. If a database operation fails and the transaction rolls
// back, earlier database changes in the transaction are rolled back too,
// instead of leaving an incomplete checkout.

// ### One important limitation

// A database transaction does not automatically undo an action performed by an
// external payment provider. We will cover that important difference later.

// ---

// ## 3. Pessimistic locking: `PESSIMISTIC_WRITE`

// ### What does locking mean?

// Locking controls how multiple requests work with the same database record at
// the same time.

// A simple analogy is a room with one key. If one person has the key, another
// person must wait until the key is released.

// A database lock works in a similar way, although the database controls access
// rather than a person.

// ### How our project uses it

// Our `OrderRepository` contains:

// @Lock(LockModeType.PESSIMISTIC_WRITE)
// @Query("SELECT o FROM Order o WHERE o.id = :orderId")
// Optional<Order> findByIdForUpdate(Integer orderId);

// And our service uses it inside a transaction:

// @Transactional
// public OrderResponse updateOrderStatus(
// Integer orderId,
// UpdateOrderStatusRequest request) {

// Order order = orderRepository.findByIdForUpdate(orderId)
// .orElseThrow(/* order not found */);

// // Validate the requested status change
// // Restore stock if cancellation is allowed
// // Update the order status
// }

// The `PESSIMISTIC_WRITE` lock asks the database to lock the selected order row
// for writing. With a database and transaction configuration that support this
// lock, another transaction trying to obtain a conflicting lock on that row
// must wait.

// ### Real example: Two cancellation requests

// Suppose Order 42 is `PLACED` and its checkout deducted 2 phones from stock.

// Two cancellation requests arrive together.

// **Without locking:**

// 1. Both requests read `PLACED`.
// 2. Both decide cancellation is allowed.
// 3. Both restore 2 phones.
// 4. The stock increases by 4 instead of 2.

// **With locking:**

// 1. Request A locks Order 42.
// 2. Request B has to wait for the conflicting lock.
// 3. Request A restores 2 phones, changes the order to `CANCELLED`, and
// commits.
// 4. Request B proceeds and reads the updated order status.
// 5. The service rejects the second cancellation because `CANCELLED` cannot
// transition to `CANCELLED`.

// The stock is restored only once.

// ### Why do we need `@Transactional` with the lock?

// The lock must remain active while we check the status and perform the
// corresponding changes.

// If the transaction ended immediately after reading the order, the lock would
// not protect the later update.

// ### Interview answer

// **Q: Why did you choose pessimistic locking?**

// For critical order-status changes, I wanted the database to prevent two
// concurrent transactions from modifying the same order at the same time.
// Locking the order row allows me to check its latest status and make the
// corresponding inventory change safely within one transaction.

// ---

// ## 4. Optimistic locking versus pessimistic locking

// These are two ways to protect shared data from conflicting updates.

// ### Pessimistic locking

// The application locks a record before making a change.

// Example: Our order service uses `PESSIMISTIC_WRITE` while updating an order
// status.

// Think: "I am updating this record now, so conflicting updates must wait."

// ### Optimistic locking

// The application lets requests work normally but checks whether the record
// changed before saving.

// In JPA, this can be implemented using `@Version`.

// For example:

// @Version
// private Long version;

// Imagine two requests read a product with version 5.

// - Request A updates it successfully, and the version becomes 6.
// - Request B tries to save based on version 5.
// - The database detects that the record has changed, so Request B cannot
// silently overwrite Request A's change.

// Think: "I will proceed, but the database must tell me if somebody changed the
// data first."

// ### When would you use each?

// - **Pessimistic locking:** Useful when conflicting operations must be
// carefully coordinated, such as order cancellation and payment processing.
// - **Optimistic locking:** Useful when conflicts are less common, and you want
// to avoid locking records for the whole operation.

// Neither approach is always better. The right choice depends on the operation
// and expected conflicts.

// ---

// ## 5. Atomic stock updates

// Our project also uses a different, important protection in
// `ProductRepository`:

// @Modifying
// @Query("""
// UPDATE Product p
// SET p.stockQuantity = p.stockQuantity - :quantity
// WHERE p.id = :productId
// AND p.stockQuantity >= :quantity
// """)
// int decrementStockIfAvailable(
// Integer productId,
// Integer quantity);

// Let's understand why this is powerful.

// ### What does "atomic" mean here?

// The database performs the stock check and deduction as part of the same
// update statement.

// It does not first ask Java to check the stock and then later issue an
// unrelated update.

// For example, if stock is 5:

// UPDATE Product
// SET stock_quantity = stock_quantity - 4
// WHERE id = 10
// AND stock_quantity >= 4;

// The database updates the row only if the product has enough stock.

// - If the update affects 1 row, the deduction succeeded.
// - If it affects 0 rows, the product was not found or the condition was not
// satisfied.

// Our service checks that result:

// int updatedRows =
// productRepository.decrementStockIfAvailable(
// product.getId(),
// cartItem.getQuantity());

// if (updatedRows == 0) {
// throw new ResponseStatusException(
// HttpStatus.CONFLICT,
// "Insufficient stock");
// }

// If two customers compete for the remaining stock, the conditional update
// prevents both from successfully deducting more stock than is available,
// assuming the database executes the updates under its normal transactional
// locking rules.

// **This is why our checkout performs an initial stock check and then uses the
// conditional database update.** The initial check gives us an early,
// understandable validation; the conditional update protects us against stock
// changing concurrently.

// ---

// ## 6. Idempotency: Preventing duplicate payments

// ### The problem

// Imagine a customer clicks "Pay" for an order costing ₹2,000.

// The payment request reaches the server, but the response gets lost because of
// a network problem.

// The customer does not know whether payment succeeded, so they click "Pay"
// again.

// Without protection, the application might charge them twice.

// This can happen because of:

// - Double-clicks.
// - Network retries.
// - Browser resending a request.
// - A timeout after the server has processed the payment.

// ### What is idempotency?

// Idempotency means that repeating the same operation with the same identity
// does not create an additional effect.

// For payments, we use an **idempotency key** to recognise repeated attempts
// that represent the same logical request.

// Example:

// Idempotency-Key: checkout-payment-42-attempt-1

// The server looks for an existing payment with that key.

// In our project:

// var existingPayment =
// paymentRepository.findByIdempotencyKey(idempotencyKey);

// if (existingPayment.isPresent()) {
// Payment payment = existingPayment.get();

// if (!payment.getOrder().getId().equals(orderId)) {
// throw new ResponseStatusException(
// HttpStatus.CONFLICT,
// "Idempotency-Key was already used for another order");
// }

// return toResponse(payment);
// }

// If the existing payment belongs to the same order, the service returns that
// payment instead of processing another payment.

// If the key was already used for another order, the service rejects the
// request with `409 Conflict`.

// ### Real example

// The first request uses:

// Idempotency-Key: payment-42

// The server processes the payment and saves the result.

// The customer retries using the same key:

// Idempotency-Key: payment-42

// The server returns the existing payment result instead of creating another
// payment.

// However, if the customer starts a genuinely new payment attempt after a
// failed attempt, our application needs a clearly defined retry policy. Reusing
// the old key returns the old attempt in our current implementation; a new
// attempt would normally need a new key.

// ### Interview answer

// **Q: Why did you implement idempotency?**

// Payment requests can be retried when the client does not receive a response,
// even when the first request was processed successfully. I use an idempotency
// key to identify the logical payment request and return the existing result on
// retries, rather than processing the same request again.

// ---

// ## 7. Why the database must enforce key uniqueness

// There is an important detail that a strong backend developer should
// understand.

// Our service looks up a payment by idempotency key. But consider two requests
// that arrive at almost exactly the same time.

// 1. Both requests check whether the key exists.
// 2. Neither finds it.
// 3. Both attempt to insert a payment using that key.

// The application-level lookup alone cannot prevent this race.

// The database should have a **unique constraint** on the idempotency key. That
// makes the database reject a second row with the same key, even when
// concurrent requests both passed the earlier lookup.

// Our current files still need to be checked to confirm that this constraint
// exists.

// Also, when the same key is used concurrently for two different orders, those
// requests may lock different order rows. Locking each order therefore does not
// replace the unique constraint on the key.

// ---

// ## 8. How locking and idempotency work together

// These concepts solve different problems.

// | Concept | Problem it solves | Project example |
// |---|---|---|
// | Transaction | Keeps related database work together | Checkout and
// order-status updates |
// | Pessimistic lock | Coordinates conflicting requests for the same record |
// Payment versus cancellation |
// | Idempotency key | Recognises a repeated logical payment request | Retrying
// a payment after a timeout |
// | Unique database constraint | Stops duplicate keys even under concurrent
// requests | Two requests insert the same key |
// | Atomic stock update | Checks and deducts stock in one database operation |
// Preventing overselling |

// ### Example: Payment and cancellation arrive together

// Suppose Order 42 is `PLACED`.

// A payment request and a cancellation request arrive together.

// Both services lock the same order row.

// **Case A: Payment gets the lock first**

// 1. The payment service checks that the order is `PLACED`.
// 2. Payment succeeds.
// 3. The order changes to `CONFIRMED`.
// 4. The transaction commits.
// 5. The cancellation request proceeds and sees `CONFIRMED`.
// 6. Cancellation is rejected because our current business rules do not allow
// cancelling a confirmed order without refunds.

// **Case B: Cancellation gets the lock first**

// 1. The order service locks the order.
// 2. Cancellation is allowed from `PLACED`.
// 3. Stock is restored and the order changes to `CANCELLED`.
// 4. The transaction commits.
// 5. The payment request proceeds and sees `CANCELLED`.
// 6. Payment is rejected because only `PLACED` orders can start a new payment
// in our current implementation.

// This is the reason both services need compatible locking and status checks.

// ---

// ## 9. Order status transitions: A state machine

// A state machine means an object can move only through allowed states.

// Our current order rules are:

// PLACED -> CONFIRMED
// PLACED -> CANCELLED
// CONFIRMED -> SHIPPED
// SHIPPED -> DELIVERED

// `DELIVERED` and `CANCELLED` are terminal states in the current design: they
// cannot transition further.

// Why is this useful?

// Without transition rules, an administrator or another request might move an
// order directly from `PLACED` to `DELIVERED`, or cancel an order after payment
// confirmation even though the application cannot process refunds.

// Our service checks whether a transition is allowed before applying it. The
// database lock then helps ensure that two concurrent requests cannot both
// validate against the same old status and perform conflicting changes.

// One remaining design point: our existing security test permits an
// administrator to mark an order `CONFIRMED` directly. If `CONFIRMED` means
// payment has succeeded, we should decide whether this administrative operation
// must also check for a successful payment before finalising the business
// rules.

// ---

// ## 10. An important limitation: The payment gateway is outside the database

// Our current `PaymentService` calls:

// PaymentResult result = paymentGateway.processPayment(
// savedPayment.getPaymentReference(),
// savedPayment.getAmount());

// The call happens while the method's database transaction is open.

// For the simulated payment gateway in our current project, this is manageable.

// But imagine a real payment provider successfully charges ₹2,000, and then our
// application crashes before the database transaction commits.

// The payment provider may have charged the customer even though our database
// did not save `SUCCESS` and `CONFIRMED`.

// A database rollback cannot undo an external charge.

// When we integrate a real payment provider later, we will need extra
// protection, such as:

// - Using the provider's idempotency support.
// - Saving and reconciling payment attempts.
// - Handling provider callbacks or webhooks.
// - Retrying safe database updates without charging the customer again.
// - Reconciling cases where the provider succeeded but the application did not
// record the result.

// We deliberately postponed this work until after the main backend and React
// integration. Understanding the limitation now is still valuable in an
// interview because it shows that you know what your current implementation
// does and does not guarantee.

// ---

// ## 11. Common interview questions

// **Q1. What is a race condition?**

// A race condition occurs when concurrent requests access or modify shared
// data, and their timing can lead to an incorrect result. For example, two
// checkout requests might both see the same stock before either deducts it.

// **Q2. Why isn't checking stock in Java enough?**

// Because another request can change the stock after the check. Our conditional
// database update checks availability and deducts stock as one operation.

// **Q3. Why use pessimistic locking for order status?**

// It makes conflicting transactions wait for the same order row, allowing the
// service to read the updated status before performing actions such as
// cancellation and stock restoration.

// **Q4. Is idempotency the same as locking?**

// No. Locking coordinates concurrent work on shared data. Idempotency
// identifies a repeated logical request so it does not create an additional
// effect.

// **Q5. Why do we need a unique constraint if we check the idempotency key
// first?**

// Two concurrent requests can both pass the lookup before either inserts a
// payment. The unique constraint is the final protection against duplicate key
// records.

// **Q6. What happens if payment succeeds but the database transaction fails?**

// The payment provider may have charged the customer while our database still
// shows the old state. Real payment integration needs provider-side idempotency
// and a way to reconcile or retry database updates safely.

// **Q7. What happens if stock deduction succeeds but order creation fails?**

// If both operations are part of the same database transaction, a rollback
// should undo the stock deduction and other transactional database changes.

// **Q8. Why do we validate order-status transitions?**

// To stop invalid business operations, such as delivering an order directly
// from `PLACED` or cancelling a confirmed order without refund handling.

// ---

// ## 12. How to explain your design to a recruiter

// A good explanation should connect the problem, the decision, and the result.

// You could say:

// "I focused on correctness under concurrent requests, especially around
// checkout and payment. For checkout, I use a conditional database stock update
// that deducts inventory only when sufficient stock remains. For order-status
// changes and payment processing, I use pessimistic locking so conflicting
// requests for the same order are coordinated. I also use idempotency keys to
// recognise retried payment requests, and explicit order-status rules to
// prevent invalid transitions. These controls solve different problems, so I
// use them together rather than relying on a single check."

// Then be ready to explain the remaining limitation: a real payment provider
// can succeed even if the local database transaction later fails, so production
// payment integration needs reconciliation and provider-side idempotency.

// **The best way to prepare:** understand one concrete example for each
// concept—overselling for atomic stock updates, two cancellation requests for
// locking, and a lost payment response for idempotency. Those examples make
// your explanation clear and show that you understand why the code exists, not
// just what its annotations are called.