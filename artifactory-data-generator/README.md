# Artifactory Data Generators

This utility generates binaries of different package types and publishes them to Artifactory.

## Getting Started

To get started, simply clone this project, make sure you meet the pre-requisites. It is also recommended to go through the package type specific README.
 
### Pre-requisites

* Docker 17.0+
  * The ability to build images
  * The ability to run images
* *NIX tools

### Building

To build the docker images responsible to generate binaries using default settings, simply run:

```./build.sh build```

This will create data generator docker images that can be used to generate binaries of different packages types.

If you want to build only a specific package type, specify the package type name:

```./build.sh build maven```

You can get a list of all the package types:

```./build.sh list```

You can modify the version, registry and image namespaces by copying the `env.setup.default` to 
`env.setup` and overriding with your desired values before running the build.

### Usage

To run a generator, simply:

* Pull the specific generator docker image (Note: Prerequisite is Docker 17.0+). 
    * `docker pull <IMAGE-NAME>:<TAG-NAME>`
    
*  Run the image, mounting the properties file with your details
    * `docker run --rm -v /my/copy/config.properties:/config.properties <IMAGE-NAME>:<TAG-NAME>`

You can also pass in environment variables instead of a properties file:

* Run the image, passing in environment variables with your details
     * `docker run --rm -e "ARTIFACTORY_URL=http://example.org/artifactory" ... <IMAGE-NAME>:<TAG-NAME>`

You can even mix and match, with environment variables taking precedence

* Run the image, passing in environment variables and mounting a properties file with your details
    * `docker run --rm -e "ARTIFACTORY_URL=http://example.org/artifactory" -v /my/copy/config.properties:/config.properties <IMAGE-NAME>:<TAG-NAME>`

If README.md.template is available for the package generator type, You can see the usage details of the generator.
* Run the image, with environment variable PRINT_HELP   
    * `run -it -e PRINT_HELP=true <IMAGE-NAME>:<TAG-NAME>`


## Adding a new package type

To add a new package type, you will need to:

1. Create a new directory with the name of the package type under `packages`
2. Create a Dockerfile to build the image
3. Create a Runner.groovy script and extend Generator.groovy (see maven example)
    * The files under the `shared` directory will automatically be added to your new package during build time
    * Make sure to add them to your Dockerfile
4. Create a config.properties.defaults file with input you expect (see maven example)
    * If left blank, the property will be considered required
    * The comments in the property file are important as they will be used to build the README.md
5. (Optional) Create a README.md.template
    * This is what the end users will see 
    * Use the placeholder <INPUT-TABLE> for the build process to auto-fill it with a end user friendly version of your config.properties.default
    * Use the placeholder <INPUT-FILE> for the block the end user will copy to provide as the input
    * Use the placeholder <IMAGE-NAME> for the generated image name
    * Use the placeholder <TAG-NAME> for the generated tag name


## License

This project is licensed under the Apache 2.0 License - see the [LICENSE.md](LICENSE.md) file for details
