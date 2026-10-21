
package com.Sambhav.ecommerce.dto;

public class AuthResponse {

    private Integer id;
    private String name;
    private String email;
    private String role;
    private String token;

    public AuthResponse(
            Integer id,
            String name,
            String email,
            String role,
            String token) {

        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.token = token;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public String getToken() {
        return token;
    }
}

// # JWT, Session, SecurityContext and Stateless Authentication

// Imagine our e-commerce website is a shopping mall. When a customer enters the
// mall, they show their identity at the entrance. After checking it, the
// security guard gives them a temporary entry pass. They use this pass to
// access the areas they are allowed to enter instead of proving their identity
// from scratch every time.

// **A session** works like a record that the mall keeps about a visitor. When
// the visitor logs in, the server creates a session and remembers that the
// visitor is authenticated. The browser receives a session ID, usually through
// a cookie, and sends it with future requests. The server checks that ID to
// find the visitor's session and remember who they are. For example, if a
// customer logs in to a shopping website using sessions, the server remembers
// their login through that session until it expires or is invalidated.

// **A JWT (JSON Web Token)** works more like a signed entry pass that the
// visitor carries. In our project, when a user logs in with their email and
// password, Spring Boot verifies their credentials and generates a JWT. This
// token contains information called *claims*, such as the user's email, role
// (`CUSTOMER` or `ADMIN`), issue time and expiration time. Our token expires
// after one hour. The client sends it with future requests using the HTTP
// header `Authorization: Bearer <token>`. The server checks the token's
// signature to verify that it has not been tampered with and checks whether it
// has expired. A signed JWT is not automatically encrypted, so someone who
// obtains it may be able to read its contents. We must never put passwords or
// other secrets inside it.

// **We chose JWT-based, stateless authentication instead of session-based
// authentication** because our project has a React frontend and a Spring Boot
// REST API, and we want the API to authenticate each request using a token
// rather than depend on a server-side login session. In `SecurityConfig`, we
// used `SessionCreationPolicy.STATELESS`. This tells Spring Security not to
// rely on an HTTP session to remember the user's authentication between
// requests. Each protected request must carry its own valid JWT. Stateless
// authentication can make it easier to scale an API across multiple servers,
// although JWT does not automatically make an application more secure or
// faster. Sessions are also a valid choice, and they can be simpler when a
// traditional server-rendered website needs login state managed by the server.

// After our JWT filter validates a token, it places the user's authentication
// information into Spring Security's **SecurityContext**. Think of this as a
// temporary note that tells the security guard who is making the current
// request. The `SecurityContextHolder` is how our code accesses the current
// security context, the `SecurityContext` holds the information, and the
// `Authentication` object represents the user's identity, authorities and
// authentication status. For example, the context might say that
// `storeadmin@example.com` is authenticated and has the authority `ROLE_ADMIN`.
// This information is used while processing the current request; it is not a
// replacement for a session that remembers the login across requests.

// Finally, **authentication and authorization are different**. Authentication
// answers, “Who are you?” Authorization answers, “What are you allowed to do?”
// In our project, a customer with a valid JWT can view products, but cannot
// create them because product creation requires the `ADMIN` role. The customer
// receives `403 Forbidden`: their identity was accepted, but they lack
// permission. A request without a valid token receives `401 Unauthorized` when
// it needs authentication. Our registration endpoint assigns the `CUSTOMER`
// role by default, so a user cannot simply register themselves as an admin.
// Remember that a stolen, unexpired bearer token can be used by someone else,
// and a role change in the database may not affect an already-issued JWT until
// it expires unless we add a role-rechecking or revocation mechanism. These are
// important security considerations for a real application.
