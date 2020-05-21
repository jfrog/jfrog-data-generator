ARG REGISTRY
FROM ${REGISTRY}openjdk:8
# Install needed tools, including CLI
RUN apt-get update && apt-get install -y curl cmake unzip software-properties-common python3-pip
RUN pip3 install -U setuptools
RUN pip3 install conan
RUN curl -OL https://dl.bintray.com/groovy/maven/apache-groovy-binary-2.4.14.zip
RUN unzip apache-groovy-binary-2.4.14.zip && ln -s /groovy-2.4.14/bin/groovy /usr/local/bin/groovy
# Setup Generator, Helpers and Config property files.
ADD Runner.groovy HelperTools.groovy Generator.groovy config.properties.defaults ./
RUN chmod +x /Runner.groovy
RUN groovy /Runner.groovy --import-grapes
ENTRYPOINT /Runner.groovy
