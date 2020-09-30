# Conan Data Generator

Generates and publishes Conan packages to Artifactory.

## Getting Started

### Prerequisites

* Docker (17.0+)

### Running

To run this generator, simply:

* Pull the image
    * `docker pull jfrog/artifactory/generator/conan:0.1`
*  Run the image, mounting the properties file with your details
    * `docker run --rm -v /my/copy/config.properties:/config.properties jfrog/artifactory/generator/conan:0.1`

You can also pass in environment variables instead of a properties file:

* Run the image, passing in environment variables with your details
     * `docker run --rm -e "ARTIFACTORY_URL=http://example.org/artifactory" ... jfrog/artifactory/generator/conan:0.1`

You can even mix and match, with environment variables taking precedence

* Run the image, passing in environment variables and mounting a properties file with your details
    * `docker run --rm -e "ARTIFACTORY_URL=http://example.org/artifactory" -v /my/copy/config.properties:/config.properties jfrog/artifactory/generator/conan:0.1`


### Available input



#### Properties file template

```
# The Artifactory URL
artifactory.url=http://localhost:8082/artifactory
# The name of the repository in Artifactory
artifactory.repo=conan
# The username to access Artifactory with
artifactory.user=admin
# The password to access Artifactory with
artifactory.password=password
# The number of unique packages to deploy
package.number=100
# The minimum package size (in bytes)
package.size.min=1000
# The maximum package size (in bytes)
package.size.max=100000
# Package name
package.name=generated
# Package user
package.user=tester
# Package channel
package.channel=genchannel
# Any properties (in the form property1=value1;property2=value2) to add to the packages
package.properties=generated=true
# Number of worker threads
generator.threads=8
```

## License

This project is licensed under the Apache 2.0 License
