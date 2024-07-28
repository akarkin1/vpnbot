#!/bin/sh

# Parameters: RUN_IN_ECS, OVPN_CN, CLIENTNAME, OVPN_CLIENT_PASSWORD, MAX_CONNECTION_WAIT_TIME_MIN

# Configure the server, if required
if [ -z "$( ls -A '/etc/openvpn' )" ]; then
  # Get Public IP of the ECS task
  if  [ $RUN_IN_ECS ]; then
    PUBLIC_IP=$(curl $ECS_CONTAINER_METADATA_URI | jq '.Networks[0].IPv4Addresses[0]');
  else
    PUBLIC_IP="127.0.0.1";
  fi

  # Generate Server config
  ovpn_genconfig -u udp://$PUBLIC_IP;
  # Generate server certificates
  (echo $OVPN_CN) | ovpn_initpki nopass;
   echo $OVPN_CLIENT_PASSWORD > ./passfile;
  # Generate client certificate
  easyrsa --passin=file:passfile --passout=file:passfile build-client-full $CLIENTNAME;
  # Remove password
  rm -rf ./passfile;
  # Generate client config
  ovpn_getclient $CLIENTNAME > /etc/openvpn/$CLIENTNAME.ovpn;
fi

unset OVPN_CLIENT_PASSWORD;

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
    echo "There is no active connections. The server will be terminated in $((5-minutes_waited_for_connection)) minutes ...";
    minutes_waited_for_connection=$((minutes_waited_for_connection + 1));
  fi

  if [ $minutes_waited_for_connection -gt $MAX_CONNECTION_WAIT_TIME_MIN ]; then
    echo "There has been no connections for the last 5 minutes. Terminating the server due to inactivity";
    exit 0;
  fi

  sleep 60;
done
