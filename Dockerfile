FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests clean package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/apps-broker-mcp-*.jar /app/mcp-server.jar
COPY apps /app/apps
RUN chmod 644 /app/mcp-server.jar && ls -lah /app && test -f /app/mcp-server.jar
EXPOSE 8080
ENV PORT=8080
ENV MCP_HTTP_SSE_MODE=true
ENV MCP_LOG_TO_FILE=false
ENV MCP_IMPORT_APPS_DIR=/app/apps
ENTRYPOINT ["java","-jar","/app/mcp-server.jar"]


