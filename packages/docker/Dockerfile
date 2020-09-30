ARG REGISTRY
FROM ${REGISTRY}docker:dind
# Install needed tools.
RUN apk update && apk add vim bash bc curl unzip openjdk8
ENV JAVA_HOME /usr/lib/jvm/java-1.8-openjdk
ENV PATH $PATH:/usr/lib/jvm/java-1.8-openjdk/jre/bin:/usr/lib/jvm/java-1.8-openjdk/bin
RUN curl -OL https://dl.bintray.com/groovy/maven/apache-groovy-binary-2.4.14.zip
RUN unzip apache-groovy-binary-2.4.14.zip && ln -s /groovy-2.4.14/bin/groovy /usr/local/bin/groovy
ADD TempDockerfile /root/
# Setup Generator, Helpers and Config property files.
ADD Runner.groovy HelperTools.groovy Generator.groovy config.properties.defaults ./
RUN chmod +x /Runner.groovy
RUN groovy /Runner.groovy --import-grapes
ENTRYPOINT /Runner.groovy
