FROM kylemanna/openvpn

ADD ./scripts/ /usr/local/bin/

RUN chmod a+x /usr/local/bin/*

# Install JQ
RUN wget https://github.com/stedolan/jq/releases/download/jq-1.6/jq-linux64 && \
    mv jq-linux64 /usr/local/bin/jq && \
    chmod +x /usr/local/bin/jq

# Install Python and necessary libraries:
RUN apk add --update --no-cache python3 && ln -sf python3 /usr/bin/python
RUN python3 -m ensurepip
RUN pip3 install --no-cache --upgrade pip setuptools

RUN pip3 install boto3 botocore

RUN touch /var/log/openvpn-status.log

# Install Curl
RUN apk add curl

CMD /usr/local/bin/configure-and-run-openvpn.sh