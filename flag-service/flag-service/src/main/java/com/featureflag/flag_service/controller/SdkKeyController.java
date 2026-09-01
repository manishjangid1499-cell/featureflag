package com.featureflag.flag_service.controller;

import com.featureflag.flag_service.dto.CreateSdkKeyRequest;
import com.featureflag.flag_service.dto.SdkKeyCreatedResponse;
import com.featureflag.flag_service.dto.SdkKeyMetadataResponse;
import com.featureflag.flag_service.service.SdkKeyService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/sdk-keys")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
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
    public ResponseEntity<List<SdkKeyMetadataResponse>> list() {
        return ResponseEntity.ok(sdkKeyService.list());
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<SdkKeyMetadataResponse> revoke(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(sdkKeyService.revoke(id));
    }
}
