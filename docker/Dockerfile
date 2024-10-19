FROM kylemanna/openvpn

ENV DANTE_VER 1.4.2
ENV DANTE_URL https://www.inet.no/dante/files/dante-$DANTE_VER.tar.gz

ARG DANTE_PROXY_USERNAME
ARG DANTE_PROXY_PASSWORD

ADD ./scripts/ /usr/local/bin/
ADD ./conf/danted.conf /etc/

RUN chmod a+x /usr/local/bin/*
# install Dante SOCKS5 proxy
RUN apk add dante-server
RUN adduser -H -D -s /bin/bash $DANTE_PROXY_USERNAME && echo "$DANTE_PROXY_USERNAME:$DANTE_PROXY_PASSWORD" | chpasswd

# Additional components
RUN wget https://github.com/stedolan/jq/releases/download/jq-1.6/jq-linux64 && \
    mv jq-linux64 /usr/local/bin/jq && \
    chmod +x /usr/local/bin/jq
RUN apk add curl

RUN touch /var/log/openvpn-status.log

CMD /usr/local/bin/configure-and-run-openvpn.sh