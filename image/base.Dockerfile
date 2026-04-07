FROM postgres:14-bookworm

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        bash \
        ca-certificates \
        curl \
        wget \
        vim \
        less \
        jq \
        procps \
        iproute2 \
        iputils-ping \
        dnsutils \
        netcat-openbsd \
        net-tools \
        lsof \
        strace \
        tcpdump \
        psmisc \
        unzip \
        tar \
        gzip \
        openssl && \
    rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"
