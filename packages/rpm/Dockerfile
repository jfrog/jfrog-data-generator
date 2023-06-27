ARG REGISTRY
FROM ${REGISTRY}fedora
#MAINTAINER http://fedoraproject.org/wiki/Cloud
RUN yum -y update && yum clean all
RUN yum update -y && yum install -y tar bc java-1.8.0-openjdk java-devel curl unzip rpm-build rpmdevtools tar gzip && yum clean all
ENV JAVA_HOME /usr/lib/jvm/java-openjdk
RUN curl -OL "https://archive.apache.org/dist/groovy/3.0.17/distribution/apache-groovy-binary-3.0.17.zip"
RUN unzip apache-groovy-binary-3.0.17.zip && ln -s /groovy-3.0.17/bin/groovy /usr/local/bin/groovy
RUN curl -fL https://getcli.jfrog.io | sh && ln -s /jfrog /usr/local/bin/jfrog
ADD rpmbuild.tar.gz /root/
# Actual generator
ADD Runner.groovy /Runner.groovy
# Helpers
ADD HelperTools.groovy /HelperTools.groovy
ADD Generator.groovy /Generator.groovy
ADD config.properties.defaults /config.properties.defaults
RUN chmod +x /Runner.groovy
RUN groovy /Runner.groovy --import-grapes
ENTRYPOINT /Runner.groovy