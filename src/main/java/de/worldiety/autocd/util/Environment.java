package de.worldiety.autocd.util;


public enum Environment {
    //Populated by the CI if environment is set in .gitlab-ci.yml
    CI_REGISTRY_USER,
    CI_REGISTRY_EMAIL,
    CI_REGISTRY,
    CI_PROJECT_NAME,
    CI_PROJECT_NAMESPACE,
    CI_REGISTRY_PASSWORD,
    //Set for the wdy namespace
    K8S_REGISTRY_USER_TOKEN,
    K8S_REGISTRY_USER_NAME
}
