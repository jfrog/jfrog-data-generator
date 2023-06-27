ARG REGISTRY
FROM ${REGISTRY}openjdk:8
# Install needed tools, including CLI
RUN apt-get update && apt-get install -y curl unzip python3-pip
RUN curl "https://bootstrap.pypa.io/get-pip.py" -o "get-pip.py" && python3 get-pip.py && pip install twine
RUN curl -OL "https://archive.apache.org/dist/groovy/3.0.17/distribution/apache-groovy-binary-3.0.17.zip"
RUN unzip apache-groovy-binary-3.0.17.zip && ln -s /groovy-3.0.17/bin/groovy /usr/local/bin/groovy
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