package com.featureflag.auth_service.controller;

import com.featureflag.auth_service.dto.InvitationResponse;
import com.featureflag.auth_service.dto.InviteMemberRequest;
import com.featureflag.auth_service.dto.MemberResponse;
import com.featureflag.auth_service.dto.PageResponse;
import com.featureflag.auth_service.dto.UpdateMemberStatusRequest;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.service.InvitationService;
import com.featureflag.auth_service.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MemberController {

    private final MemberService memberService;
    private final InvitationService invitationService;

    @Operation(summary = "Invite a new member via email")
    @PostMapping("/invite")
    public ResponseEntity<InvitationResponse> inviteMember(
            @Valid @RequestBody InviteMemberRequest request,
            Authentication authentication
    ) {
        User currentUser = (User) authentication.getPrincipal();
        InvitationResponse response = invitationService.inviteMember(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get all member invitations")
    @GetMapping("/invitations")
    public ResponseEntity<PageResponse<InvitationResponse>> getAllInvitations(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(PageResponse.from(
                invitationService.getAllInvitations(
                        PageRequest.of(page, size)
                )
        ));
    }

    @Operation(summary = "Resend a pending invitation")
    @PostMapping("/invitations/{id}/resend")
    public ResponseEntity<InvitationResponse> resendInvitation(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User currentUser = (User) authentication.getPrincipal();
        InvitationResponse response = invitationService.resendInvitation(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Revoke an invitation")
    @PostMapping("/invitations/{id}/revoke")
    public ResponseEntity<String> revokeInvitation(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User currentUser = (User) authentication.getPrincipal();
        String message = invitationService.revokeInvitation(id, currentUser);
        return ResponseEntity.ok(message);
    }

    @Operation(summary = "Get all platform members")
    @GetMapping
    public PageResponse<MemberResponse> getAllMembers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(
                memberService.getAllMembers(PageRequest.of(
                        page,
                        size,
                        Sort.by(Sort.Direction.DESC, "id")
                ))
        );
    }

    @Operation(summary = "Get member by ID")
    @GetMapping("/{id}")
    public MemberResponse getMember(@PathVariable Long id) {
        return memberService.getMember(id);
    }

    @Operation(summary = "Change member role")
    @PatchMapping("/{id}/role")
    public MemberResponse updateRole(
            @PathVariable Long id,
            @RequestParam Role role,
            Authentication authentication
    ) {
        User currentUser = (User) authentication.getPrincipal();
        return memberService.updateRole(id, role, currentUser);
    }

    @Operation(summary = "Enable or disable a platform member")
    @PatchMapping("/{id}/status")
    public MemberResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMemberStatusRequest request,
            Authentication authentication
    ) {
        User currentUser = (User) authentication.getPrincipal();
        return memberService.updateEnabled(
                id,
                request.enabled(),
                currentUser
        );
    }

    @Operation(summary = "Delete platform member")
    @DeleteMapping("/{id}")
    public String deleteMember(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User currentUser = (User) authentication.getPrincipal();
        memberService.deleteMember(id, currentUser);
        return "Member deleted successfully";
    }
}
