properties([[$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false],
	parameters([string(defaultValue: 'jslave1-euwest1c-n1std4', description: '', name: 'NODE_LABEL'),
    choice(choices: "generic\ndocker", description: '''Package type''', name: 'PAKAGE_TYPE'),
	string(defaultValue: 'artifactory-test.jfrog.io', description: 'Artifactory URL', name: 'ARTIFACTORY_URL'),
    string(defaultValue: 'repo1', description: 'Artifactory repo name', name: 'ARTIFACTORY_REPO'),
    string(defaultValue: 'admin', description: 'Artifactory user', name: 'ARTIFACTORY_USER'),
    string(defaultValue: 'password', description: 'Artifactory password', name: 'ARTIFACTORY_PASSWORD'),
	string(defaultValue: '100', description: 'Amount of artifacts', name: 'ARTIFACTS_AMOUNT'),
	string(defaultValue: 'path', description: 'Path of artifacts inside the repo', name: 'ARTIFACTS_PATH'),
	string(defaultValue: '1000', description: 'Min package size in bytes', name: 'MIN_PACKAGE_SIZE'),
	string(defaultValue: '1000', description: 'Max package size in bytes', name: 'MAX_PACKAGE_SIZE'),
	string(defaultValue: 'generated', description: 'Pakage name prefix', name: 'PACKAGE_NAME'),
	string(defaultValue: '8', description: 'Parallel threads amount', name: 'THREADS_AMOUNT')]),
	[$class: 'ThrottleJobProperty', categories: [], limitOneJobWithMatchingParams: false, maxConcurrentPerNode: 0, maxConcurrentTotal: 0, paramsToUseForLimit: '', throttleEnabled: false, throttleOption: 'project']
])

node("docker"){

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