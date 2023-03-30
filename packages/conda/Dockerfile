ARG REGISTRY
FROM ${REGISTRY}openjdk:8
# Install needed tools, including CLI
RUN apt-get update && apt-get install -y curl unzip wget
RUN curl -OL https://archive.apache.org/dist/groovy/2.4.14/distribution/apache-groovy-binary-2.4.14.zip
RUN unzip apache-groovy-binary-2.4.14.zip && ln -s /groovy-2.4.14/bin/groovy /usr/local/bin/groovy
RUN curl -fL https://getcli.jfrog.io | sh && ln -s /jfrog /usr/local/bin/jfrog
# install miniconda & conda-build
RUN wget https://repo.anaconda.com/miniconda/Miniconda3-latest-MacOSX-x86_64.sh -O miniconda.sh && bash miniconda.sh -b -p $HOME/miniconda
RUN conda install conda-build
# Actual generator
ADD Runner.groovy /Runner.groovy
# Helpers
ADD HelperTools.groovy /HelperTools.groovy
ADD Generator.groovy /Generator.groovy
ADD test_cookbook/ /test_cookbook
ADD config.properties.defaults /config.properties.defaults
RUN chmod +x /Runner.groovy
RUN groovy /Runner.groovy --import-grapes
ENTRYPOINT /Runner.groovy
