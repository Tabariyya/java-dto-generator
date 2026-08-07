FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY java-dto-generator/pom.xml java-dto-generator/
RUN mvn dependency:go-offline -q

COPY java-dto-generator/src java-dto-generator/src
RUN mvn clean package -q
