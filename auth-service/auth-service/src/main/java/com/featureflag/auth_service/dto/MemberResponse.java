package com.featureflag.auth_service.dto;

import com.featureflag.auth_service.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MemberResponse {

    private Long id;

    private String name;

    private String email;

    private Role role;
}