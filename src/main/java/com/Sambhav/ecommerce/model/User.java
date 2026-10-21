package com.Sambhav.ecommerce.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role = "CUSTOMER";

    public User() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}



// //## Authentication, Authorization, and Password Hashing

// **Authentication** is the process of verifying who a user is, whereas
// **authorization** determines what an authenticated user is allowed to do. For
// example, in our e-commerce application, a customer can log in and view
// products, but only an admin should be allowed to create, update, or delete
// products. We need authorization to prevent users from accessing resources or
// performing actions they are not permitted to perform. During registration, we
// must never store passwords in plaintext because an attacker who gains access
// to the database could read them directly. Instead, we use a password encoder
// such as BCrypt to store a one-way password hash. During login, BCrypt
// verifies the submitted password against the stored hash without decrypting or
// recovering the original password. We should also use HTTPS to protect
// passwords while they are transmitted between the client and server.
// Therefore, authentication establishes a user's identity, authorization
// controls their permissions, password hashing protects stored credentials, and
// HTTPS protects data in transit.