package com.example.nagoyameshi.controller;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.example.nagoyameshi.service.MemberinfoService;
import com.stripe.Stripe;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;

@RestController
public class StripeWebhookController {

    private static final Logger logger = Logger.getLogger(StripeWebhookController.class.getName());

    @Value("${stripe.api.secret}")
    private String stripeApiKey;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    private final MemberinfoService memberinfoService;

    public StripeWebhookController(MemberinfoService memberinfoService) {
        this.memberinfoService = memberinfoService;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    @PostMapping("/stripe/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody String payload, @RequestHeader("Stripe-Signature") String sigHeader) {
        Event event;

        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Webhook error while validating signature.", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook verification failed");
        }

        logger.info("Received Stripe WebHook: " + event.getType());
        logger.info("Received payload: " + payload);
        logger.info("Received Stripe-Signature header: " + sigHeader);
        if ("checkout.session.completed".equals(event.getType())) {
            logger.info("Received Stripe-event: " + event.toJson());
            EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
            if (dataObjectDeserializer.getObject().isPresent()) {
                Session session = (Session) dataObjectDeserializer.getObject().get();
                handleCheckoutSession(session);
            } else {
                logger.warning("Unable to deserialize object.");
            }
        }
        return ResponseEntity.ok("");
    }

    @GetMapping("/stripe/webhook")
    public ResponseEntity<String> handleGetWebhook() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body("This endpoint only supports POST requests.");
    }

    private void handleCheckoutSession(Session session) {
        String customerId = session.getCustomer();
        
        try {
            // 顧客オブジェクトの取得
            Customer customer = Customer.retrieve(customerId);
            String email = customer.getEmail();
            logger.info("Customer email from Customer object: " + email);

            if (email != null) {
                try {
                    memberinfoService.upgradeUserRole(email, "ROLE_PAID");
                    logger.info("User role upgraded to ROLE_PAID for user: " + email);
                } catch (RuntimeException e) {
                    logger.severe("Error upgrading user role: " + e.getMessage());
                }
            } else {
                logger.warning("User email not found in Customer object.");
            }
        } catch (Exception e) {
            logger.severe("Error retrieving Customer object: " + e.getMessage());
        }
    }
}