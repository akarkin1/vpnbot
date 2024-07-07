## Commands

* /servers - prints list of all available servers
  - Example:
```text
1. Region: eu-north-1 (Stockholm), Server Name: openvpn-server-stockholm, InstanceID: <InstanceId>, State: {Running | Stopped}
2. Region: eu-west-2 (London), Server Name: openvpn-server-london, InstanceID: <InstanceId>, State: {Running | Stopped}
3. Region: us-east-1 (North Virginia), Server Name: openvpn-server-nvirginia, InstanceID: <InstanceId>, State: {Running | Stopped}
```
* /serverInfo <ID>
```text
ID: <ID>
Region: <Region ?>
State: {Running, Stopped}
Public IP: <IP>
```
* /startInstance <InstanceId>
```text
Public IP: ...
State: Running
<attachment - OpenVPN config file>
```
* /startServer <Name>
* /stopInstance <InstanceId>
* /stopServer <Name>