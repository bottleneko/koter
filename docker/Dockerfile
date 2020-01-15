ARG BASE_IMAGE

FROM ${BASE_IMAGE}

ARG PUID
ARG PGID

RUN set -ux \
    && groupadd -g "$PGID" -o workspace \
    && useradd -g workspace -m -u "$PUID" -o workspace

USER workspace
