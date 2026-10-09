FROM amazonlinux:2023

RUN curl -fsSL -o /etc/yum.repos.d/tailscale.repo \
        https://pkgs.tailscale.com/stable/amazon-linux/2023/tailscale.repo && \
    dnf install -y python3 python3-pip tailscale && \
    dnf clean all && \
    rm -rf /var/cache/dnf

COPY requirements.txt /tmp/requirements.txt
RUN pip3 install --no-cache-dir -r /tmp/requirements.txt && \
    rm /tmp/requirements.txt

# Unbuffered output: the agent's log lines reach CloudWatch immediately.
ENV PYTHONUNBUFFERED=1

# Only the agent's modules; node_agent/tests stays out of the image.
COPY node_agent/*.py /opt/node_agent/
WORKDIR /opt

CMD ["python3", "-m", "node_agent"]
