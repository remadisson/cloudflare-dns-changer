# cloudflare-dns-changer

[cloudflare](https://www.cloudflare.com/)-dns-changer (cdc) is a tool, written in Kotlin, that can change the target of your DNS entries to your IP-Address.
Comes in quiet handy when you're in Germany and don't have a static IP-Address.
It uses the Cloudflare-API to connect, get and update Information.

It can also provide Notifications via [Discord-Webhook](https://discord.com/developers/docs/resources/webhook#execute-webhook), it'll just need the URL to the channel.
Your IP-Address will be determined by [https://checkip.amazonaws.com](https://checkip.amazonaws.com)

## Installation

You can build the cdc yourself, use one of the releases or run it in docker. Should work on Windows, macOS and Linux.

Requirements:
- at least Java Runtime Environment (JRE) 17
- docker (if you want it to run in docker)

## Usage
Start the tool via this command 

```sh
java  \
    -DDC_WEBHOOK="default" \
    -DDC_TAG="default" \ 
    -DCF_EMAIL="default" \
    -DCF_TOKEN="default" \
    -DCF_ZONE="default" \
    -DCF_SUBDOMAINS="default" \ #
    -jar "cloudflare-dns-changer-{REPLACE-VERSION}.jar"
```

or use environment variables.

Discord Variables
- DC_WEBHOOK - (optional) The URL for an Discord webhook
- DC_TAG - (optional) The UUID of an user that should be tagged when error or warning appears

Cloudflare Variables
- CF_EMAIL - Email of your cloudflare account
- CF_TOKEN - API-Token of your cloudflare account
- CF_ZONE - the ZoneID for the specified domain
- CF_SUBDOMAINS - Should be an comma separated string that holds your current subdomains in full length e.g. sub.example.com

## Building

### Native
1. Make sure you have Java Development Kit (JDK) 17 or higher installed
2. Clone the repo with either git or manually
```sh
git clone https://github.com/remadisson/cloudflare-dns-changer.git
```

3. Execute Gradle:
```sh
./gradlew build
```

### Docker
1. Make sure you have the latest [Docker-Version](https://docs.docker.com/engine/install/) installed
2. Make sure you are not on arm/v7 because occasionally it currently does not want to build with [eclipse-temurin:17-jdk](https://hub.docker.com/layers/library/eclipse-temurin/17-jdk/images/sha256-e8b82974623b18dc9269d227755ffd7dcd927534c7f79242c47083a4ac713d33?context=explore)
3. Build the image
```sh
docker build -t cloudflare-dns-changer:latest .
```
4. Start a container with this image
```sh
docker run -d --name cdc \                                                                                                                                                                                                                                                                                                                                   ✔  3h 29m 48s  
    --restart=always \
    -e DC_WEBHOOK="default" \
    -e CF_EMAIL="default" \
    -e CF_TOKEN="default" \
    -e CF_ZONE="default" \
    -e CF_SUBDOMAINS="default" \
    --cpus 1 \
    --memory 1G \
    cloudflarednschanger:latest

```