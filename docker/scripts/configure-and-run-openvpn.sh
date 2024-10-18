#!/bin/sh
# Parameters: RUN_IN_ECS, OVPN_CN, CLIENTNAME, OVPN_CLIENT_PASSWORD, MAX_CONNECTION_WAIT_TIME_MIN, USER_DATA_DIR, SUBNET_CIDR, AWS_DNS_SERVER_IP

echo "User data dir: $USER_DATA_DIR"
first_run="1"

# Networking configuration to make VPN Server work
echo 1 > /proc/sys/net/ipv4/ip_forward;
iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE;

# Try to restore user data from provided directory
if [[ -n "${USER_DATA_DIR}" && -d "$USER_DATA_DIR" ]]; then
  echo "Found a backup on User Data storage."
  if [[ -n "$( ls -A "$USER_DATA_DIR/" )" ]]; then
    echo "The back up directory is not empty, restoring user data."
    cp -r "$USER_DATA_DIR/"* /etc/openvpn/
    first_run="0"
  else
    echo "User data directory is empty, nothing to restore."
  fi
fi

# Back up data before container termination
backup_userdata() {
  if [[ -n "${USER_DATA_DIR}" ]]; then
    echo "USER_DATA_DIR environment variable has been set, saving files to User Data storage: $USER_DATA_DIR"
    if [[ ! -d "$USER_DATA_DIR" ]]; then
      echo "Directory does not exist, creating the directory."
      mkdir -p "$USER_DATA_DIR/"
    elif [ $( ls -A "$USER_DATA_DIR/" ) ]; then
      echo "Directory is not empty, cleaning up the directory."
    fi
    cp -r /etc/openvpn/* "$USER_DATA_DIR/"
    echo "Backup saved successfully."
  else
    echo "No user data found to back up."
  fi
}

terminate_gracefully() {
  echo "Terminating the process..."
  backup_userdata
  exit
}

# If we catch SIGTERM SIGINT, we need to save user data
trap terminate_gracefully SIGTERM;
trap terminate_gracefully SIGINT;

#Ensure that working directories are created
mkdir -p /etc/openvpn
mkdir -p /var/log

# Configure the server, if required
if [ "$first_run" = "1" ]; then
  # Get Public IP of the ECS task
  if  [ $RUN_IN_ECS ]; then
    PUBLIC_IP=$(curl -s ${ECS_CONTAINER_METADATA_URI_V4} | jq -r '.Networks[0].IPv4Addresses[0]');
  else
    PUBLIC_IP="127.0.0.1";
  fi
  echo "Public IP: $PUBLIC_IP"
  # Generate Server config
  ovpn_genconfig -u udp://$PUBLIC_IP -d -D -s $SUBNET_CIDR -p "redirect-gateway def1 bypass-dhcp" -p "dhcp-option DNS $AWS_DNS_SERVER_IP";
  # Generate server certificates
  (echo $OVPN_CN) | ovpn_initpki nopass;
   echo $OVPN_CLIENT_PASSWORD > ./passfile;
  # Generate client certificate
  easyrsa --passin=file:passfile --passout=file:passfile build-client-full $CLIENTNAME;
  # Remove password
  rm -rf ./passfile;
  # Generate client config
  ovpn_getclient $CLIENTNAME > /etc/openvpn/$CLIENTNAME.ovpn;
  # we need to make a backup, so the lambda would be able to get client config file
  backup_userdata;
fi

unset OVPN_CLIENT_PASSWORD;

if [ ! -e "/var/log/openvpn-status.log" ]; then
  touch /var/log/openvpn-status.log
fi

ovpn_run --daemon --status /var/log/openvpn-status.log 55;

minutes_waited_for_connection=0;

if [ -z $MAX_CONNECTION_WAIT_TIME_MIN ]; then
  MAX_CONNECTION_WAIT_TIME_MIN=5;
fi

# Monitor number of connections and terminate, if there are no connections for $MAX_CONNECTION_WAIT_TIME_MIN minutes.
while [ 1 ]; do
  echo "Heartbeat!";

  # Check number of active users
  ACTIVE_USERS=$(awk 'flag{ if (/ROUTING TABLE/){printf "%s", buf; flag=0; buf=""} else buf = buf $0 ORS}; /Common Name/{flag=1}' /var/log/openvpn-status.log | wc -l);

  if [ $ACTIVE_USERS -gt "0" ]; then
    echo "The server will keep working as long as at least one connection is alive. Number of active connections: $ACTIVE_USERS.";
    minutes_waited_for_connection=0;
  else
    echo "There is no active connections. The server will be terminated in $((MAX_CONNECTION_WAIT_TIME_MIN-minutes_waited_for_connection)) minutes ...";
    minutes_waited_for_connection=$((minutes_waited_for_connection + 1));
  fi

  if [ $minutes_waited_for_connection -gt $MAX_CONNECTION_WAIT_TIME_MIN ]; then
    echo "There has been no connections for the last $MAX_CONNECTION_WAIT_TIME_MIN minutes. Terminating the server due to inactivity";
    terminate_gracefully
    exit 0;
  fi

  sleep 60;
done
