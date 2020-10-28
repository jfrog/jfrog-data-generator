# HELM Data Generator

Generates and publishes helm packages to Artifactory.

## Getting Started

### Prerequisites

* Docker (17.0+)

### Running

To run this generator, simply:

* Pull the image
    * `docker pull jfrog/artifactory/generator/helm:0.1`
*  Run the image, mounting the properties file with your details
    * `docker run --rm -v /my/copy/config.properties:/config.properties jfrog/artifactory/generator/helm:0.1`

You can also pass in environment variables instead of a properties file:

* Run the image, passing in environment variables with your details
     * `docker run --rm -e "ARTIFACTORY_URL=http://example.org/artifactory" ... jfrog/artifactory/generator/helm:0.1`

You can even mix and match, with environment variables taking precedence

* Run the image, passing in environment variables and mounting a properties file with your details
    * `docker run --rm -e "ARTIFACTORY_URL=http://example.org/artifactory" -v /my/copy/config.properties:/config.properties jfrog/artifactory/generator/helm:0.1`

## License

This project is licensed under the Apache 2.0 License
