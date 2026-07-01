FROM eclipse-temurin:22-jre

WORKDIR /app

# Default Spring Boot port (can be overridden with -e SERVER_PORT=...)
EXPOSE 9090

# Override at build time if your jar name/path differs.
# Example: docker build --build-arg JAR_FILE=target/textellent-mcp-server-1.0.0.jar -t textellent-mcp-server .
ARG JAR_FILE=textellent-mcp-server-1.0.0.jar
COPY ${JAR_FILE} textellent-mcp-server.jar

ENTRYPOINT ["java", "-jar", "/app/textellent-mcp-server.jar"]
