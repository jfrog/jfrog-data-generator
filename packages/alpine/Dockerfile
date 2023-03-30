ARG REGISTRY
FROM ${REGISTRY}alpine:3.7
# Install needed tools, including CLI
RUN apk add abuild
RUN apk add openjdk8
RUN apk add bash
RUN apk add zip
RUN curl -OL https://archive.apache.org/dist/groovy/2.4.14/distribution/apache-groovy-binary-2.4.14.zip
RUN unzip apache-groovy-binary-2.4.19.zip && ln -s /groovy-2.4.19/bin/groovy /usr/local/bin/groovy
RUN curl -fL https://getcli.jfrog.io | sh && ln -s /jfrog /usr/local/bin/jfrog
# set JAVA_HOME
ENV JAVA_HOME /usr/lib/jvm/java-1.8-openjdk/
RUN export JAVA_HOME
# Actual generator
ADD Runner.groovy /Runner.groovy
# Add Helpers
ADD HelperTools.groovy /HelperTools.groovy
ADD Generator.groovy /Generator.groovy
ADD config.properties.defaults /config.properties.defaults
ADD pack.sh /pack.sh
# Make files executable
RUN chmod +x /Runner.groovy
RUN chmod +x /pack.sh
# Run it
RUN groovy /Runner.groovy --import-grapes
ENTRYPOINT /Runner.groovy
