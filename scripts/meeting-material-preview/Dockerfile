FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends libreoffice-writer libreoffice-calc libreoffice-impress fonts-noto-cjk ffmpeg \
    && rm -rf /var/lib/apt/lists/*
ENV HOME=/tmp
WORKDIR /work
