FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN apt-get update && apt-get install -y unzip && rm -rf /var/lib/apt/lists/*
# Use Gradle wrapper if present, else system gradle from image is not available — install quick
RUN if [ -f ./gradlew ]; then chmod +x ./gradlew && ./gradlew installDist --no-daemon; \
    else \
      curl -sL https://services.gradle.org/distributions/gradle-8.12.1-bin.zip -o g.zip && \
      unzip -q g.zip && \
      ./gradle-8.12.1/bin/gradle installDist --no-daemon; \
    fi

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/install/coefficient-radar-backend /app
ENV PORT=8080
EXPOSE 8080
CMD ["/app/bin/coefficient-radar-backend"]
