ARG REGISTRY
FROM ${REGISTRY}openjdk:8
# Install needed tools, including CLI
RUN apt-get update && apt-get install -y apt-transport-https gnupg2 curl
RUN curl -s https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key add -
RUN echo "deb https://apt.kubernetes.io/ kubernetes-xenial main" | tee -a /etc/apt/sources.list.d/kubernetes.list
RUN curl https://baltocdn.com/helm/signing.asc | apt-key add -
RUN apt-get install apt-transport-https -y
RUN echo "deb https://baltocdn.com/helm/stable/debian/ all main" | tee /etc/apt/sources.list.d/helm-stable-debian.list
RUN apt-get update
RUN apt-get install -y kubectl helm
RUN curl -OL https://dl.bintray.com/groovy/maven/apache-groovy-binary-2.4.14.zip
RUN unzip apache-groovy-binary-2.4.14.zip && ln -s /groovy-2.4.14/bin/groovy /usr/local/bin/groovy
RUN curl -fL https://getcli.jfrog.io | sh && ln -s /jfrog /usr/local/bin/jfrog
# Actual generator
ADD Runner.groovy /Runner.groovy
# Helpers
ADD HelperTools.groovy /HelperTools.groovy
ADD Generator.groovy /Generator.groovy
ADD test-chart/ /test-chart
ADD config.properties.defaults /config.properties.defaults
RUN chmod +x /Runner.groovy
RUN groovy /Runner.groovy --import-grapes