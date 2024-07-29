FROM kylemanna/openvpn

ADD ./scripts/ /usr/local/bin/

RUN chmod a+x /usr/local/bin/*

# Install JQ
RUN wget https://github.com/stedolan/jq/releases/download/jq-1.6/jq-linux64 && \
    mv jq-linux64 /usr/local/bin/jq && \
    chmod +x /usr/local/bin/jq

RUN touch /var/log/openvpn-status.log

# Install Curl
RUN apk curl \
    && apk cache clean \

CMD /usr/local/bin/configure-and-run-openvpn.sh