# Once jar uretilmis olmali: mvn -pl product-catalog-service package -DskipTests
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/product-catalog-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 9003
ENTRYPOINT ["java", "-jar", "app.jar"]
