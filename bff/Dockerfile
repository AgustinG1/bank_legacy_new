FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
ARG MODULE
COPY . .
RUN mvn -B -pl ${MODULE} -am -DskipTests package && \
    cp ${MODULE}/target/${MODULE}-0.0.1-SNAPSHOT-exec.jar /tmp/app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system banco && useradd --system --gid banco banco
COPY --from=build /tmp/app.jar app.jar
USER banco
ENTRYPOINT ["java", "-jar", "app.jar"]
