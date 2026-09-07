FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="$JAVA_HOME/bin:$PATH"

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
CMD ["sh", "-c", "exec java -jar /app/app.jar --server.port=${PORT:-8080}"]






