#!/bin/bash
# Discord Variables
#DC_WEBHOOK - (optional) The URL for an Discord webhook
#DC_TAG - (optional) The UUID of an user that should be tagged when error or warning appears

# Cloudflare Variables
#CF_EMAIL - Email of your cloudflare account
#CF_TOKEN - API-Token of your cloudflare account
#CF_ZONE - the ZoneID for the specified domain
#CF_SUBDOMAINS - Should be an comma separated string that holds your current subdomains in full length e.g. sub.example.com

#Use this command to start the application with specified arguments.

#Currently only type A records are Supported, and the content will be set to your current IP-Address using https://checkip.amazonaws.com

java  \
    -DDC_WEBHOOK="default" \
    -DDC_TAG="default" \
    -DCF_EMAIL="default" \
    -DCF_TOKEN="default" \
    -DCF_ZONE="default" \
    -DCF_SUBDOMAINS="default" \
    -jar "build/libs/cloudflare-dns-changer-1.0.1.jar"
