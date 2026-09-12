package com.featureflag.flag_service.controller;

import com.featureflag.flag_service.dto.CreateSdkKeyRequest;
import com.featureflag.flag_service.dto.SdkKeyCreatedResponse;
import com.featureflag.flag_service.dto.SdkKeyMetadataResponse;
import com.featureflag.flag_service.dto.PageResponse;
import com.featureflag.flag_service.service.SdkKeyService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sdk-keys")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Validated
public class SdkKeyController {

    private final SdkKeyService sdkKeyService;

    @PostMapping
    public ResponseEntity<SdkKeyCreatedResponse> create(
            @Valid @RequestBody CreateSdkKeyRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                sdkKeyService.create(
                        request,
                        authentication.getName()
                )
        );
    }

    @GetMapping
    public ResponseEntity<PageResponse<SdkKeyMetadataResponse>> list(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page cannot be negative")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size cannot exceed 100")
            int size
    ) {
        return ResponseEntity.ok(PageResponse.from(
                sdkKeyService.list(PageRequest.of(page, size)),
                value -> value
        ));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<SdkKeyMetadataResponse> revoke(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(sdkKeyService.revoke(id));
    }
}
