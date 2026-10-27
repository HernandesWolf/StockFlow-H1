# ---------- Etapa 1: compilar ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copia só o pom primeiro: o Docker reaproveita o cache das dependências
# enquanto o pom.xml não mudar (builds seguintes ficam bem mais rápidos).
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- Etapa 2: imagem final (só o necessário para rodar) ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Não roda como root dentro do container.
RUN useradd --system --uid 1001 stockflow
USER stockflow

COPY --from=build /build/target/stockflow-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
