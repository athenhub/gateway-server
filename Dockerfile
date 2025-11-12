FROM eclipse-temurin:21-jdk

ENV HOSTNAME="localhost"
ENV EUREKA_URL="http://localhost:9150/eureka/"

ENTRYPOINT ["java", "-Deureka.instance.hostname=${HOSTNAME}", "-Deureka.client.serviceUrl.defaultZone=${EUREKA_URL}", "-jar", "app.jar"]