FROM maven:3.8.1-openjdk-17 AS builder

WORKDIR /app

COPY pom.xml ./

RUN mvn dependency:go-offline

COPY . .

FROM openjdk:17-jdk-slim-buster

WORKDIR /app

COPY --from=builder /app/out/artifacts/gipopofapus_jar/gipopofapus.jar ./gipopofapus.jar

ENTRYPOINT ["java", "-jar", "gipopofapus.jar"]
EXPOSE 8000
CMD tail -f /dev/null
