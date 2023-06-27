ARG REGISTRY
FROM ${REGISTRY}openjdk:8
# Install needed tools, including CLI
RUN apt-get update && apt-get install -y curl cmake unzip software-properties-common python3-pip
RUN pip3 install -U setuptools
RUN pip3 install MarkupSafe==2.0.0
RUN pip3 install conan==1.31.0
RUN curl -OL "https://archive.apache.org/dist/groovy/3.0.17/distribution/apache-groovy-binary-3.0.17.zip"
RUN unzip apache-groovy-binary-3.0.17.zip && ln -s /groovy-3.0.17/bin/groovy /usr/local/bin/groovy
# Setup Generator, Helpers and Config property files.
ADD Runner.groovy HelperTools.groovy Generator.groovy config.properties.defaults ./
RUN chmod +x /Runner.groovy
RUN groovy /Runner.groovy --import-grapes
ENTRYPOINT /Runner.groovy
