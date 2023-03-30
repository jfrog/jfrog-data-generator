ARG REGISTRY
FROM ${REGISTRY}openjdk:8
# Install needed tools, including CLI
RUN apt-get update && apt-get install -y curl unzip
RUN curl "https://bootstrap.pypa.io/get-pip.py" -o "get-pip.py" && python get-pip.py && pip install twine
RUN curl -OL https://archive.apache.org/dist/groovy/2.4.14/distribution/apache-groovy-binary-2.4.14.zip
RUN unzip apache-groovy-binary-2.4.14.zip && ln -s /groovy-2.4.14/bin/groovy /usr/local/bin/groovy
ADD pypibuild.tar.gz /root/
# Actual generator
ADD Runner.groovy /Runner.groovy
# Helpers
ADD HelperTools.groovy /HelperTools.groovy
ADD Generator.groovy /Generator.groovy
ADD config.properties.defaults /config.properties.defaults
RUN chmod +x /Runner.groovy
RUN groovy /Runner.groovy --import-grapes
ENTRYPOINT /Runner.groovy
