FROM eclipse-temurin:17-jdk
LABEL authors="remadisson"
WORKDIR /app

COPY . .

RUN ./gradlew build

COPY build/libs/cloudflare-dns-changer-*.jar /app/cloudflare-dns-changer.jar

ENV DC_WEBHOOK="default"
ENV DC_TAG="default"
ENV CF_EMAIL="default"
ENV CF_TOKEN="default"
ENV CF_ZONE="default"
ENV CF_SUBDOMAINS="default"

CMD ["java", "-jar", "cloudflare-dns-changer.jar"]
