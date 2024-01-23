#!/bin/bash

java -jar "CloudflareDnsChanger-1.0.0-SNAPSHOT.jar" "$DC_WEBHOOK" "$CF_EMAIL" "$CF_TOKEN" "$CF_ZONE" "$CF_SUBDOMAINS"