package com.shopflow.notification.api;

import com.shopflow.common.web.PageResponse;
import com.shopflow.notification.api.dto.NotificationRequest;
import com.shopflow.notification.api.dto.NotificationResponse;
import com.shopflow.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Admin / internal. From Phase 11 a Kafka consumer replaces the POST endpoint.")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Request a notification (idempotent per eventId + channel); attempts delivery immediately")
    public NotificationResponse request(@Valid @RequestBody NotificationRequest request) {
        return notificationService.request(request.eventId(), request.userId(), request.channel(),
                request.template(), request.data());
    }

    @GetMapping
    @Operation(summary = "List a user's notification history")
    public PageResponse<NotificationResponse> list(@RequestParam UUID userId,
                                                   @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                                   Pageable pageable) {
        Page<NotificationResponse> page = notificationService.listForUser(userId, pageable);
        return PageResponse.of(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Manually re-attempt a failed notification")
    public NotificationResponse retry(@PathVariable UUID id) {
        return notificationService.retry(id);
    }
}
