FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY java-dto-generator/pom.xml java-dto-generator/
COPY entity-id-generator/pom.xml entity-id-generator/
RUN mvn dependency:go-offline -q

COPY java-dto-generator/src java-dto-generator/src
COPY entity-id-generator/src entity-id-generator/src
RUN mvn clean package -q
