package com.featureflag.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"eureka.client.enabled=false",
		"spring.cloud.discovery.enabled=false"
})
class ApiGatewayApplicationTests {

	@Autowired
	private RouteDefinitionLocator routeDefinitionLocator;

	@Test
	void contextLoads() {
	}

	@Test
	void flagRouteForwardsRuntimeAndKeyManagementPathsWithoutFilters() {
		var flagRoute = routeDefinitionLocator
				.getRouteDefinitions()
				.filter(route -> "flag-service".equals(route.getId()))
				.single()
				.block();

		assertThat(flagRoute).isNotNull();
		assertThat(flagRoute.getPredicates())
				.flatExtracting(predicate ->
						predicate.getArgs().values()
				)
				.anyMatch(value -> value.contains("/runtime/**"))
				.anyMatch(value -> value.contains("/sdk-keys/**"));
		assertThat(flagRoute.getFilters()).isEmpty();
	}

}
