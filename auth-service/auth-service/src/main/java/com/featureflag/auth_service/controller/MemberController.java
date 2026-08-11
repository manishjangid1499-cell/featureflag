package com.featureflag.auth_service.controller;

import com.featureflag.auth_service.dto.MemberRequest;
import com.featureflag.auth_service.dto.MemberResponse;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MemberController {

    private final MemberService memberService;

    @Operation(
            summary = "Create a new platform member"
    )
    @PostMapping
    public MemberResponse createMember(
            @Valid @RequestBody MemberRequest request,
            Authentication authentication
    ) {

        User currentUser =
                (User) authentication.getPrincipal();

        return memberService.createMember(
                request,
                currentUser
        );
    }

    @Operation(
            summary = "Get all platform members"
    )
    @GetMapping
    public List<MemberResponse> getAllMembers() {

        return memberService.getAllMembers();
    }

    @Operation(
            summary = "Get member by ID"
    )
    @GetMapping("/{id}")
    public MemberResponse getMember(
            @PathVariable Long id
    ) {

        return memberService.getMember(id);
    }

    @Operation(
            summary = "Change member role"
    )
    @PatchMapping("/{id}/role")
    public MemberResponse updateRole(
            @PathVariable Long id,
            @RequestParam Role role,
            Authentication authentication
    ) {

        User currentUser =
                (User) authentication.getPrincipal();

        return memberService.updateRole(
                id,
                role,
                currentUser
        );
    }

    @Operation(
            summary = "Delete platform member"
    )
    @DeleteMapping("/{id}")
    public String deleteMember(
            @PathVariable Long id,
            Authentication authentication
    ) {

        User currentUser =
                (User) authentication.getPrincipal();

        memberService.deleteMember(
                id,
                currentUser
        );

        return "Member deleted successfully";
    }
}