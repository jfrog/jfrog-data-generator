# PyPi Data Generator

Generates and publishes PyPi packages to Artifactory.

## Getting Started

### Prerequisites

* Docker (17.0+)

### Running

To run this generator, simply:

* Pull the image
    * `docker pull <IMAGE-NAME>:<TAG-NAME>`
*  Run the image, mounting the properties file with your details
    * `docker run --rm -v /my/copy/config.properties:/config.properties <IMAGE-NAME>:<TAG-NAME>`

You can also pass in environment variables instead of a properties file:

* Run the image, passing in environment variables with your details
     * `docker run --rm -e "ARTIFACTORY_URL=http://example.org/artifactory" ... <IMAGE-NAME>:<TAG-NAME>`

You can even mix and match, with environment variables taking precedence

* Run the image, passing in environment variables and mounting a properties file with your details
    * `docker run --rm -e "ARTIFACTORY_URL=http://example.org/artifactory" -v /my/copy/config.properties:/config.properties <IMAGE-NAME>:<TAG-NAME>`


### Available input

Just importing dependencies


#### Properties file template

```
# The Artifactory URL
artifactory.url=
# The name of the repository in Artifactory
artifactory.repo=
# The username to access Artifactory with
artifactory.user=
# The password to access Artifactory with
artifactory.password=
# The number of unique packages to deploy
package.number=100
# The root directory of where the packages should go
package.root.dir=org/jfrog
# The minimum package size (in bytes)
package.size.min=10000
# The maximum package size (in bytes)
package.size.max=10000000
# Package name
package.name=generated
# Any properties (in the form property1=value1;property2=value2) to add to the packages
package.properties=generated=true
# Number of worker threads
generator.threads=8
```

## License

This project is licensed under the Apache 2.0 License