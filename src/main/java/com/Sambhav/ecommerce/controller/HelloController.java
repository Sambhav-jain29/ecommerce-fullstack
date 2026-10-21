package com.Sambhav.ecommerce.controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController //tells Spring "This class will handle HTTP requests and return response data."
public class HelloController {
    @GetMapping("/hello")   //We need to map the URL to the java method:tells Spring "This method will handle GET requests to the /hello endpoint."
    public String hello() {
        return "Hello, World!";
    }
}
