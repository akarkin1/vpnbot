#!/bin/sh

# Parameters: RUN_IN_ECS, OVPN_CN, CLIENTNAME, OVPN_CLIENT_PASSWORD, MAX_CONNECTION_WAIT_TIME_MIN, USER_DATA_DIR
PID=$BASHPID
echo "User data dir: $USER_DATA_DIR"

# Try to restore user data from provided directory
if [[ -n "${USER_DATA_DIR}" && -d "$USER_DATA_DIR" ]]; then
  echo "Found a backup on User Data storage."
  if [[ -n "$( ls -A "$USER_DATA_DIR/" )" ]]; then
    echo "The back up directory is not empty, restoring user data."
    cp -r "$USER_DATA_DIR/"* /etc/openvpn/
  else
    echo "User data directory is empty, nothing to recover."
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
  else
    echo "No user data found to back up."
  fi
  exit
}

# If we catch SIGTERM SIGINT, we need to save user data
trap backup_userdata SIGTERM;
trap backup_userdata SIGINT;
echo "PID: $PID"

mkdir -p /etc/openvpn
mkdir -p /var/log

# Configure the server, if required
echo "ls -A '/etc/openvpn': $( ls -A '/etc/openvpn' )"
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
    backup_userdata
    exit 0;
  fi

  sleep 60;
done
