# --- Etapa de build: compila el jar con Maven ---
FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /build

# Copiar solo el pom primero para cachear las dependencias en una capa
# separada -- solo se re-descargan si pom.xml cambia, no en cada build.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Los tests ya corrieron en CI antes de llegar a main (ver .github/workflows/ci.yml);
# no repetirlos aca evita necesitar red/tiempo extra en cada build de imagen.
RUN mvn -B -q -DskipTests package

# --- Etapa final: solo el JRE + el jar, sin Maven ni el codigo fuente ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Correr como usuario sin privilegios, no como root dentro del contenedor.
RUN useradd --system --create-home --shell /usr/sbin/nologin appuser
COPY --from=build /build/target/*.jar app.jar
RUN chown appuser:appuser app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
