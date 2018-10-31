#!/bin/bash

BUILD_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
PACKAGES_DIR=$BUILD_DIR/packages
COMMAND=$1
PACKAGE=$2

source $BUILD_DIR/env.setup.default
if [ -f $BUILD_DIR/env.setup ]; then
   source $BUILD_DIR/env.setup
fi

display_usage() {
    echo "Usage: build.sh [build|list|help] [{package}]"
    echo "    build - Builds all the package types (default) or a specific package if specified"
    echo "    clean - Cleans all the package types (default) or a specific package if specified"
    echo "    list - Lists all the different package types"
    echo "    help - This help message"
}

list_packages() {
    echo "Package Types: "
    for dir in $PACKAGES_DIR/*/
    do
        dir=${dir%*/}
        echo "    ${dir##*/}"
    done
}

list_packages() {
    echo "Package Types: "
    for dir in $PACKAGES_DIR/*/
    do
        dir=${dir%*/}
        echo "    ${dir##*/}"
    done
}

build_package() {
    PACKAGE_TO_BUILD=$1
    PACKAGE_DIR=$PACKAGES_DIR/$PACKAGE_TO_BUILD
    echo "Building $PACKAGE_TO_BUILD"
    # Copy in shared tools
    \cp -rf $BUILD_DIR/shared/* $PACKAGE_DIR/.
    # Build the image
    cd $PACKAGE_DIR
    IMAGE_NAME=$IMAGE_NAMESPACE_NAME/$PACKAGE_TO_BUILD
    docker build -t "$IMAGE_NAME:$VERSION" --build-arg REGISTRY=$REGISTRY .
    README_TEMPLATE_FILE=$PACKAGE_DIR/README.md.template
    README_FILE=$PACKAGE_DIR/README.md
    # If there is a README template, generate the README
    if [ -f  ]; then
        \cp -rf $README_TEMPLATE_FILE $README_FILE
        sed -i .~ "s|<IMAGE-NAME>|$IMAGE_NAMESPACE_NAME/$PACKAGE_TO_BUILD|" $README_FILE
        sed -i .~ "s|<TAG-NAME>|$VERSION|" $README_FILE
        # Multiline replace
        docker run --rm -e "PRINT_HELP=true" $IMAGE_NAME:$VERSION > tmp.txt~
        cp $README_FILE tmp2.txt~
        sed  '/<INPUT-TABLE>/r tmp.txt~' tmp2.txt~ | sed '/<INPUT-TABLE>/d' > $README_FILE
        cp $README_FILE tmp2.txt~
        sed  "/<INPUT-FILE>/r $PACKAGE_DIR/config.properties.defaults" tmp2.txt~ | sed '/<INPUT-FILE>/d' > $README_FILE
    fi
}

build_packages() {
    # If no package set, do all
    if [ -z "$PACKAGE" ]
    then
        echo "Building all"
        for dir in $PACKAGES_DIR/*/
        do
            dir=${dir%*/}
            build_package ${dir##*/}
        done
    else
        if [ ! -d "$PACKAGES_DIR/$PACKAGE" ]; then
            echo "ERROR: Package $PACKAGE does not exist"
            list_packages
        else
            build_package $PACKAGE
        fi
    fi
}


clean_package() {
    PACKAGE_TO_CLEAN=$1
    PACKAGE_DIR=$PACKAGES_DIR/$PACKAGE_TO_CLEAN
    echo "Cleaning $PACKAGE_TO_CLEAN"
    # Copy in shared tools
    cd $PACKAGE_DIR
    rm -rf README.md
     rm -rf tmp.txt
    for full_path in $BUILD_DIR/shared/*
    do
       file=${full_path##*/}
       rm -rf $PACKAGE_DIR/$file
    done
}

clean_packages() {
    # If no package set, do all
    if [ -z "$PACKAGE" ]
    then
        echo "Cleaning all"
        for dir in $PACKAGES_DIR/*/
        do
            dir=${dir%*/}
            clean_package ${dir##*/}
        done
    else
        if [ ! -d "$PACKAGES_DIR/$PACKAGE" ]; then
            echo "ERROR: Package $PACKAGE does not exist"
            list_packages
        else
            clean_package $PACKAGE
        fi
    fi
}

if [ "$COMMAND" == "build" ]
then
    build_packages
elif [ "$COMMAND" == "list" ]
then
    list_packages
elif [ "$COMMAND" == "clean" ]
then
    clean_packages
else
    display_usage
fi

