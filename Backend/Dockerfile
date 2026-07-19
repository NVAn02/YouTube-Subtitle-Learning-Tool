FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app

# Install Python3, yt-dlp, and Node.js (JS runtime required by yt-dlp for YouTube).
# bgutil-ytdlp-pot-provider is the yt-dlp plugin side of the PO Token provider — it talks to the
# bgutil-provider sidecar container (see docker-compose.yml) to fetch a valid token so the "web"
# client can serve subtitles directly, without falling back to unreliable alternate player clients.
RUN apt-get update && apt-get install -y python3 python3-pip curl nodejs \
    && pip3 install --break-system-packages yt-dlp bgutil-ytdlp-pot-provider \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
