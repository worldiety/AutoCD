# Documentation AutoCD

## Usage
What is AutoCD and what can it do for me? 
Docker and Kubernetes are everywhere, everybody wants to use it but it is
not always simple to write a fitting docker file that satisfies all constrains.
Even though it would be possible do generate an image by coping and pasting 
all the bits you might need, the chance of an error are not insignificant.
To prevent errors like that and to make docker/kubernetes more accessible 
we created AutoCD.

AutoCD helps you to get your project ready for your k8s cluster. 
The program will automatically generate a fitting docker image and deploy
that to your cluster. Therefor AutoCD detects the project language 
(Java 8+, Go, Vue.js, Node.js are supported) and uses the matching, 
predefined docker image (if there is none found within your root folder),
to deploy the project to your cluster. It can also take down old, not used
projects from the cluster.
All you have to do to use AutoCD is, to include it into your gitlab-ci.yml
file and, if you have one, include a JSON file for configure AutoCD. 
The JSON file allows you to make specific changes to the configuration 
(e.g. port, volumes).

## Installation
If you want to integrate AutoCD to your project, you need to adjust the 
gitlab-ci.yml.
Add the following to the _.script_ option.
```bash
  script:
    - curl https://autocd.cloudiety.de/ -o app.jar
    - java -jar app.jar ${KUBE_URL} ${KUBE_TOKEN} ${KUBE_CA_PEM_FILE} ${BUILDTYPE}
```
This will download and execute the AutoCD tool. All other variables
will be obtained from your global GitLab configuration.
The final configuration should look like this:
```bash
image: fredlahde/dind-java
services:
  - docker:dind

variables:
  DOCKER_TLS_CERTDIR: ""

stages:
  - deploy-prod
  - deploy-dev

.deploy:
  stage: deploy
  before_script:
    - apk add curl
    - apk add git
    - git --no-pager show $(git log --pretty=format:'%h' -n 2 | tail -n 1):autocd.json 2>/dev/null 1>oldautocd.json || true
  environment: default #Needed for CI to autopopulate KUBE_ fields
  tags:
    - docker-build-runner
  script:
    - curl https://autocd.cloudiety.de/ -o app.jar
    - java -jar app.jar ${KUBE_URL} ${KUBE_TOKEN} ${KUBE_CA_PEM_FILE} ${BUILDTYPE}

deploy-prod:
  extends: .deploy
  stage: deploy-prod
  variables:
    BUILDTYPE: prod
  only:
    - master

```
#### JSON configuration file example
In case you want change certain values, set them in a JSON file. See the
table below for parameters to specify. As an example, here is a JSON file 
```bash
{
    "otherImages": [
      {
        "registryImagePath": "redis:latest",
        "containerPort": 6379,
        "servicePort": 6379,
        "publiclyAccessible": "false",
        "serviceName": "redis"
      },
      {
        "registryImagePath": "path/to/registry/service/image",
        "serviceName": "content-service",
        "subdomains": {
          "dev": "yourapp.dev.com",
          "edit": "yourapp.edit.com",
          "stage": "yourapp.com"
        },
        "environmentVariables": {
          "dev": {
            "SPRING_PROFILES_ACTIVE": "dev",
            "REDIS_URL": "redis",
            "SYNC_URL": "http://sync-service"
          },
          "edit": {
            "SPRING_PROFILES_ACTIVE": "stage",
            "REDIS_URL": "redis",
            "SYNC_URL": "http://sync-service",
          },
          "stage": {
            "SPRING_PROFILES_ACTIVE": "prod",
            "REDIS_URL": "redis",
          }
        }
      },
      {
        "registryImagePath": "path/to/registry/service/image",
        "serviceName": "sync-service",
        "subdomains": {
          "dev": "yourapp.dev.com",
          "edit": "yourapp.edit.com",
          "stage": "yourapp.com"
        },
        "environmentVariables": {
          "dev": {
            "SPRING_PROFILES_ACTIVE": "dev",
            "REDIS_URL": "redis"
          },
          "edit": {
            "SPRING_PROFILES_ACTIVE": "stage",
            "REDIS_URL": "redis"
          },
          "stage": {
            "SPRING_PROFILES_ACTIVE": "prod",
            "REDIS_URL": "redis"
          }
        }
      }
    ],
    "containerPort": 3000,
    "environmentVariables": {
      "dev": {
        "ENVIRONMENT": "dev",
        "CONTENT_SERVICE_URL": "https://content-dev.de",
        "NEWSLETTER_SERVICE_URL": "https://newsletter.de",
        "EDITOR_URL": "https://cms-editor.de/js/app.js"
      },
      "edit": {
        "ENVIRONMENT": "edit",
        "NEWSLETTER_SERVICE_URL": "https://newsletter.de"
      },
      "stage": {
        "ENVIRONMENT": "stage",
        "NEWSLETTER_SERVICE_URL": "https://newsletter.de",
      }
    },
    "serviceName" : "website",
    "subdomains": {
      "dev": "yourapp.dev.com",
      "edit": "yourapp.edit.com",
      "stage": "yourapp.com"
    }
  }
```

### Paramenter for AutoCD

| Parameter     | Function     | Example  | Type | default |
| ------------- |:-------------| -----:    |-------------:|-------------:|
|containerPort      |   defines which port should be exposed   |  8080   | int |8080 |
| servicePort     | defines which port will be exposed to the outside     | 80  | int |80 |
| replicas | number of stable sets of replica Pods running at any given time  | 1  | int |1 |
| publiclyAccessible |allows to connect from the outside. Note: if **false**, you will not receive a subdomain     | true/false  | Boolean |true |
| terminationGracePeriod |time to shut down the container, if exceeded, container will be terminated forcefully    |60L| long |60L |
| dockerImagePath | Path to the Docker Image(Docker images residing in the root directory will be picked up automatically)     |  _path/to/image/_ | String | |
| registryImagePath |Path to images from the registry(note: if this is set inside the root autocd configuration, autocd will _not_ attempt to build a new image) Usually only set in "otherImages" or for debugging| registry.worldiety.net/flahde/redistest  | String | |
| subdomains | maps the environment to a fitting URL | "dev": "yourapp.dev.worldiety.de"  | String | |
| shouldHost | if true, deployment to cluster, not if false| true/false  | boolean |true |
| volumes | list of volumes to mount     |   | List<Volume> | |
| environmentVariables| define environments variables for a container| |  Map<String, String>| |
| otherImages |list of autoCD objects which will be processed recursively  |   | List<AutoCD> | |
| args | define arguments that will run in the Pod | "HOSTNAME", "KUBERNETES_PORT"  | List<String> | |
| serviceName | name for your service, can be used as for example: redis to access the redis instance in the cluster     |   | String |null |
| subdomain | subdomain for your webapp(If none is specified, one will be generated and announced at the end of the CI build     | yourapp.cloudiety.de  | String |null |



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

## Environment
To run the autoCD.jar, Java 12 is necessary.
Variables, for example KUBE_URL, KUBE_TOKEN, KUBE_CA_PEM_FILE, BUILDTYPE
should be set in your GitLab deployment variables.


## Important Notes
* The parameter class 'volume' has parameters of its own:
    * _volumeMount_: name of the Volume 
    * _volumeSize_: size string from k8s (e.g. 1Gi, 100Mi)
    * _folderPermission_: permissions within the folder
    * _retainVolume_: boolean value with determines if the volume should be retained after a restart 
    
* If AutoCD finds any file named '_build.sh_' within your project rood folder, AutoCD will use the build.sh file you
 provide. If there is none, AutoCD will use a default build.sh file. However, after executing the build.sh, AutoCD 
 expects a compiled project with fitting files (e.g. yourProject.jar inside of /build/libs if it's a Java project).
 
* If there is any need for static data (e.g. images, fonts) make sure, that those files are located within the **static** 
folder (folder must be named **static**) inside your project root directory. AutoCD will make sure, that the static folder
will be copied onto the pod and available at the working directory.
 