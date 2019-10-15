# Dokumentation für AutoCD

### Paramenter für AutoCD

| Parameter     | Function     | Example  | Type | default |
| ------------- |:-------------| -----:    |-------------:|-------------:|
|       |     |      |      |       |
containerPort      |   defines which port should be exposed   |  8080   | int |8080 |
|       |     |      |      |       |
| servicePort     | defines which port will be exposed to the outside     | 80  | int |80 |
|       |     |      |      |       |
| replicas | number of stable sets of replica Pods running at any given time  | 1  | int |1 |
|       |     |      |      |       |
| publiclyAccessible |allows to connect from the outside. Note: if **false**, you will not receive a subdomain, ingress     | true/false  | Boolean |true |
|       |     |      |      |       |
| terminationGracePeriod |time to shut down the container, if exceeded, container will be terminated forcefully    |60L| long |60L |
|       |     |      |      |       |
| dockerImagePath | Path to the Docker Image     |  _path/to/image/_ | String | |
|       |     |      |      |       |
| registryImagePath |Path to images from the registry| registry.worldiety.net/flahde/redistest  | String | |
|       |     |      |      |       |
| subdomains | maps the environment to a fitting URL | dev -> yourapp.dev.worldiety.de  | String | |
|       |     |      |      |       |
| shouldHost | if true, deployment to cluster, not if false| true/false  | boolean |true |
|       |     |      |      |       |
| volumes | list of volumes to mount     |   | List<Volume> | |
|       |     |      |      |       |
| environmentVariables| define environments variables for a container| |  Map<String, String>| |
|       |     |      |      |       |
| otherImages |list of autoCD objects which will be processed recursively  |   | List<AutoCD> | |
|       |     |      |      |       |
| args | define arguments that will run in the Pod | "HOSTNAME", "KUBERNETES_PORT"  | List<String> | |
|       |     |      |      |       |
| serviceName | name for your service     |   | String |null |
|       |     |      |      |       |
| subdomain | subdomain for your webapp     | yourapp.cloudiety.de  | String |null |
|       |     |      |      |       |



## Example for a final configuration file generated with AutoCD

```bash
{
  "otherImages": [
    {
      "volumes": [
        {
          "volumeMount": "/var/redis",
          "filePermission": "777"
        }
      ],
      "registryImagePath": "redis:latest",
      "args": [
        "--save 15 1",
        "--dir /var/redis"
      ],
      "containerPort": 6379,
      "servicePort": 6379,
      "publiclyAccessible": "false",
      "serviceName": "redistest-redis-dev-service"
    }
  ]
}
```

## Important Notes
* The parameter class 'volume' has parameters of its own:
    * _volumeMount_: name of the Volume 
    * _volumeSize_: size in gigabyte
    * _folderPermission_: permissions within the folder
    * _retainVolume_: boolean value with determines if the volume should be retained after a restart 
    
* If AutoCD finds any file named '_build.sh_' within your project rood folder, AutoCD will use the build.sh file you
 provide. If there is none, AutoCD will use a default build.sh file. However, after executing the build.sh, AutoCD 
 expects a compiled project with fitting files (e.g. yourProject.jar inside of /build/libs if it's a Java project).
 
* If there is any need for static data (e.g. images, fonts) make sure, that those files are located within the **static** 
folder (folder must be named **static**) inside your project root directory. AutoCD will make sure, that the static folder
will be copied onto the pod and available at the working directory.
 