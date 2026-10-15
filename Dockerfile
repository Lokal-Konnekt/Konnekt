FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive \
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    GRADLE_USER_HOME=/opt/gradle-cache

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    cmake \
    curl \
    ffmpeg \
    g++ \
    git \
    libasound2-dev \
    libfontconfig1 \
    libfreetype6 \
    libgbm1 \
    libgl1-mesa-dev \
    libgtk-3-0 \
    libgtk-3-dev \
    libnss3 \
    libpulse0 \
    libx11-xcb1 \
    libxi6 \
    libxrandr2 \
    libxrender1 \
    libxtst6 \
    make \
    ninja-build \
    openjdk-21-jdk \
    pkg-config \
    unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
COPY . .

RUN chmod +x gradlew \
    && ./gradlew --no-daemon :shared:jvmTest :sharedUi:jvmTest :desktopApp:test

CMD ["./gradlew", "--no-daemon", ":desktopApp:run"]
