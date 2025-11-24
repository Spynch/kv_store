FROM eclipse-temurin:21-jre
WORKDIR /app

# Копируем fat-jar внутрь контейнера под понятным именем
COPY target/arkasha-1.0-SNAPSHOT-jar-with-dependencies.jar app.jar

EXPOSE 8080

# ЯВНО говорим, какой main запускать
CMD ["java", "-cp", "app.jar", "com.example.kvdb.apihttp.ArkashaApiApplication"]
