node("docker"){

    git(
        url: 'https://github.com/jfrog/jfrog-data-generator.git',
        branch: 'fixed-login'
    )

    stage('Build generator') {
        ansiColor('gnome-terminal'){
        sh  """
            bash build.sh build $PAKAGE_TYPE
        """
        }
    }

    stage('Generate data') {
        ansiColor('gnome-terminal'){
            sh  """
                docker run --rm \
                -e "ARTIFACTORY_URL=$ARTIFACTORY_URL" \
                -e "ARTIFACTORY_REPO=$ARTIFACTORY_REPO" \
                -e "ARTIFACTORY_USER=$ARTIFACTORY_USER" \
                -e "ARTIFACTORY_PASSWORD=$ARTIFACTORY_PASSWORD" \
                -e "PACKAGE_NUMBER=$ARTIFACTS_AMOUNT" \
                -e "PACKAGE_ROOT_DIR=$ARTIFACTS_PATH" \
                -e "PACKAGE_SIZE_MIN=$MIN_PACKAGE_SIZE" \
                -e "PACKAGE_SIZE_MAX=$MAX_PACKAGE_SIZE" \
                -e "PACKAGE_NAME_PREFIX=$PACKAGE_NAME" \
                -e "PACKAGE_NAME_EXTENSION=bin" \
                -e "GENERATOR_THREADS=$THREADS_AMOUNT" \
                jfrog/artifactory/generator/$PAKAGE_TYPE:0.1
                """
            }
        }
   }