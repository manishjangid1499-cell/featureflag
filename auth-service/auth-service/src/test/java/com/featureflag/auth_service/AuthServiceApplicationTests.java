package com.featureflag.auth_service;

import com.featureflag.auth_service.config.JwtProperties;
import com.featureflag.auth_service.security.EphemeralJwtTestConfiguration;
import com.featureflag.auth_service.security.JwtService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.text.ParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@Import(EphemeralJwtTestConfiguration.class)
class AuthServiceApplicationTests {

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JwtProperties jwtProperties;

	@Test
	void contextSignsAndVerifiesRsaJwt() throws ParseException {
		String token = jwtService.generateToken("context@example.test", "VIEWER");

		assertTrue(jwtService.isTokenValid(token));
		assertEquals("context@example.test", jwtService.extractEmail(token));
		assertEquals("VIEWER", jwtService.extractRole(token));

		SignedJWT signedJwt = SignedJWT.parse(token);
		assertEquals(JWSAlgorithm.RS256, signedJwt.getHeader().getAlgorithm());
		assertEquals(jwtProperties.getKeyId(), signedJwt.getHeader().getKeyID());
		assertEquals(jwtProperties.getIssuer(), signedJwt.getJWTClaimsSet().getIssuer());
		assertTrue(signedJwt.getJWTClaimsSet().getAudience().contains(jwtProperties.getAudience()));
	}

}
