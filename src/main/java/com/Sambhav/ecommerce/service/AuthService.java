
package com.Sambhav.ecommerce.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.Sambhav.ecommerce.dto.LoginRequest;
import com.Sambhav.ecommerce.dto.RegisterRequest;
import com.Sambhav.ecommerce.exception.EmailAlreadyExistsException;
import com.Sambhav.ecommerce.model.User;
import com.Sambhav.ecommerce.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(RegisterRequest request) {

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException("Email is already registered");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("CUSTOMER");

        return userRepository.save(user);
    }

    public User login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword())) {

            throw new BadCredentialsException("Invalid email or password");
        } // We use "Invalid email or password" for both cases to avoid revealing whether
          // an email is registered. This reduces unnecessary account-enumeration
          // information exposed by the login endpoint. means that if an attacker tries to
          // log in with an email that is not registered, they will receive the same error
          // message as if they had entered the wrong password for a registered email.
          // This makes it harder for attackers to determine which emails are registered
          // in the system.

        return user;
    }

}
