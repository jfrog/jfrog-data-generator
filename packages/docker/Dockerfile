ARG REGISTRY
FROM ${REGISTRY}docker:dind
# Install needed tools.
RUN apk update && apk add vim bash bc curl unzip openjdk8
ENV JAVA_HOME /usr/lib/jvm/java-1.8-openjdk
ENV PATH $PATH:/usr/lib/jvm/java-1.8-openjdk/jre/bin:/usr/lib/jvm/java-1.8-openjdk/bin
RUN curl -OL "https://archive.apache.org/dist/groovy/3.0.17/distribution/apache-groovy-binary-3.0.17.zip"
RUN unzip apache-groovy-binary-3.0.17.zip && ln -s /groovy-3.0.17/bin/groovy /usr/local/bin/groovy
ADD TempDockerfile /root/
# Setup Generator, Helpers and Config property files.
ADD Runner.groovy HelperTools.groovy Generator.groovy config.properties.defaults ./
RUN chmod +x /Runner.groovy
RUN groovy /Runner.groovy --import-grapes
ENTRYPOINT /Runner.groovy
