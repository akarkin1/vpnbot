FROM kylemanna/openvpn

ARG DANTE_PROXY_USERNAME
ARG DANTE_PROXY_PASSWORD

ADD ./scripts/ /usr/local/bin/
ADD ./conf/danted.conf /etc/

RUN chmod a+x /usr/local/bin/*
# install Dante SOCKS5 proxy
RUN apk add --no-cache dante-server
RUN adduser -H -s /bin/bash $DANTE_PROXY_USERNAME && echo "$DANTE_PROXY_USERNAME:$DANTE_PROXY_PASSWORD" | chpasswd

# Additional components
RUN wget https://github.com/stedolan/jq/releases/download/jq-1.6/jq-linux64 && \
    mv jq-linux64 /usr/local/bin/jq && \
    chmod +x /usr/local/bin/jq
RUN apk add curl
RUN apk add --update --no-cache python3 && ln -sf python3 /usr/bin/python
RUN python3 -m ensurepip
RUN pip3 install --no-cache --upgrade pip setuptools
RUN pip3 install boto3 botocore

RUN touch /var/log/openvpn-status.log

CMD /usr/local/bin/configure-and-run-openvpn.sh