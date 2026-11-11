FROM node:22-alpine AS web
WORKDIR /web
COPY core/frontend/package.json core/frontend/package-lock.json ./
RUN npm ci
COPY core/frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY core/backend/pom.xml ./pom.xml
RUN mvn -B -ntp dependency:go-offline
COPY core/backend/src ./src
COPY --from=web /web/dist/ ./src/main/resources/static/
RUN mvn -B -ntp -DskipTests package && \
    find target -maxdepth 1 -name 'plataforma-*.jar' -exec cp {} /tmp/app.jar \;

# Dependencias del compilador de proyectos generados, descargadas durante el build.
COPY core/docs/pruebas/fixtures/2026-09-21_compilador_backend_pom.xml /tmp/compilador-pom.xml
RUN mvn -B -ntp -f /tmp/compilador-pom.xml -Dmaven.repo.local=/opt/maven-cache \
    -Dmaven.compiler.proc=none org.apache.maven.plugins:maven-compiler-plugin:3.14.1:compile

FROM maven:3.9.9-eclipse-temurin-21
RUN useradd --system --uid 10001 --create-home app && \
    mkdir -p /app /opt/compilaciones /opt/maven-cache && \
    chown -R app:app /app /opt/compilaciones /opt/maven-cache
COPY --from=backend /tmp/app.jar /app/app.jar
COPY --from=backend --chown=app:app /opt/maven-cache/ /opt/maven-cache/
ENV SPRING_PROFILES_ACTIVE=despliegue \
    UML_COMPILACION_MAVEN_HOME=/usr/share/maven \
    UML_COMPILACION_REPOSITORIO=/opt/maven-cache \
    UML_COMPILACION_DIRECTORIO=/opt/compilaciones \
    JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -XX:ActiveProcessorCount=2 -XX:+ExitOnOutOfMemoryError"
USER app
WORKDIR /app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
