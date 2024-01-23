FROM eclipse-temurin:17-jdk
LABEL authors="remadisson"

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./

RUN ./gradlew --version

COPY . .

RUN ./gradlew build

COPY build/libs/CloudflareDnsChanger-1.0.0-SNAPSHOT.jar /app/

ENV DC_WEBHOOK="your_webhook_link"
ENV CF_EMAIL="your_cloudflare_email"
ENV CF_TOKEN="your_cloudflare_token"
ENV CF_ZONE="your_cloudflare_domain_zone"
ENV CF_SUBDOMAINS="your_cloudflare_subdomains"

COPY entryscript.sh /app/entrypoint.sh

RUN chmod +x /app/entrypoint.sh

ENTRYPOINT ["/app/entrypoint.sh"]
#CMD ["java", "-jar", "build/libs/CloudflareDnsChanger-1.0.0-SNAPSHOT.jar", "${DC_WEBHOOK}", "${CF_EMAIL}", "${CF_TOKEN}", "${CF_ZONE}", "${CF_SUBDOMAINS}"]