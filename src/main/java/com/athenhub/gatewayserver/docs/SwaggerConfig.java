package com.athenhub.gatewayserver.docs;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

  /** 회원 API 예: /v1/members/** */
  @Bean
  public GroupedOpenApi membersAPI() {
    return GroupedOpenApi.builder()
        .group("member-api")
        .displayName("Member API")
        .pathsToMatch("/v1/members/**")
        .build();
  }

  /** 허브 API 예: /v1/hubs/** */
  @Bean
  public GroupedOpenApi hubsAPI() {
    return GroupedOpenApi.builder()
        .group("hub-api")
        .displayName("Hub API")
        .pathsToMatch("/v1/hubs/**")
        .build();
  }

  /** 업체 API 예: /v1/vendors/** */
  @Bean
  public GroupedOpenApi vendorsAPI() {
    return GroupedOpenApi.builder()
        .group("vendor-api")
        .displayName("Vendor API")
        .pathsToMatch("/v1/vendors/**")
        .build();
  }

  /** 배송 API 예: /v1/shippings/** 또는 /v1/deliveries/** */
  @Bean
  public GroupedOpenApi shippingsAPI() {
    return GroupedOpenApi.builder()
        .group("shipping-api")
        .displayName("Shipping API")
        .pathsToMatch("/v1/shippings/**")
        .build();
  }

  /** 배송 경로 기록 API 예: /v1/shipping-routes/** 또는 /v1/shipping-path-records/** */
  @Bean
  public GroupedOpenApi shippingRouteRecordsAPI() {
    return GroupedOpenApi.builder()
        .group("shipping-route-api")
        .displayName("Shipping Route Record API")
        .pathsToMatch("/v1/shipping-routes/**")
        .build();
  }

  /** 배송 담당자 API 예: /v1/shipping-agents/** */
  @Bean
  public GroupedOpenApi shippingAgentsAPI() {
    return GroupedOpenApi.builder()
        .group("shipping-agent-api")
        .displayName("Shipping Agent API")
        .pathsToMatch("/v1/shipping-agents/**")
        .build();
  }

  /** 상품 API 예: /v1/products/** */
  @Bean
  public GroupedOpenApi productsAPI() {
    return GroupedOpenApi.builder()
        .group("product-api")
        .displayName("Product API")
        .pathsToMatch("/v1/products/**")
        .build();
  }

  /** 주문 API 예: /v1/orders/** */
  @Bean
  public GroupedOpenApi ordersAPI() {
    return GroupedOpenApi.builder()
        .group("order-api")
        .displayName("Order API")
        .pathsToMatch("/v1/orders/**")
        .build();
  }
}
