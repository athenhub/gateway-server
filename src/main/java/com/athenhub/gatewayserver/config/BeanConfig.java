package com.athenhub.gatewayserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson {@link ObjectMapper} 관련 Bean 설정을 담당하는 구성 클래스.
 *
 * <p>이 클래스에서는 스프링 컨테이너에 사용할 {@link ObjectMapper} Bean을 등록하고, Java 8 날짜/시간 API(예: {@link
 * java.time.LocalDate}, {@link java.time.LocalDateTime})를 직렬화/역직렬화할 수 있도록 {@link JavaTimeModule}을
 * 등록한다.
 *
 * <p>이렇게 공용 {@code ObjectMapper}를 Bean으로 등록해 두면, 스프링에서 주입받아 사용할 때 항상 동일한 설정(날짜/시간 처리 포함)을 공유할 수 있다.
 */
@Configuration // @Bean 메서드로 정의돈 객체들을 빈으로 등록하도록 해준다.
public class BeanConfig {
  /**
   * 공용 {@link ObjectMapper} Bean을 생성하여 스프링 컨테이너에 등록한다.
   *
   * <p>설정 내용:
   *
   * <ul>
   *   <li>{@link JavaTimeModule}을 등록하여 {@code java.time} 패키지의 타입들을 JSON으로 직렬화/역직렬화할 수 있도록 지원한다.
   * </ul>
   *
   * @return 애플리케이션 전역에서 사용할 {@link ObjectMapper} 인스턴스
   */
  @Bean // 이 메서드가 반환하는 ObjectMapper를 스프링 빈으로 등록함
  public ObjectMapper getObjectMapper() {
    // 기본 ObjectMapper 인스턴스 생성
    ObjectMapper om = new ObjectMapper();
    // LocalDate, LocalDateTime 등 java.time 관련 타입 직렬화/역직렬화를 위한 모듈 등록
    om.registerModule(new JavaTimeModule());
    return om;
  }
}
