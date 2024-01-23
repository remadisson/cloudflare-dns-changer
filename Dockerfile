FROM ubuntu:latest
LABEL authors="remadisson"

ENTRYPOINT ["top", "-b"]