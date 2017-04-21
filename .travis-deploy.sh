#!/usr/bin/env sh
set -e

# Define all paths
export DEPLOY_PATH="$HOME/code/deploy/"
export IJ_PATH="$DEPLOY_PATH/Fiji.app"
export IJ_LAUNCHER="$IJ_PATH/Contents/MacOS/ImageJ-macosx"

# Install IJ
mkdir -p $IJ_PATH/
cd $DEPLOY_PATH
wget --no-check-certificate https://downloads.imagej.net/fiji/latest/fiji-macosx.zip
unzip fiji-macosx.zip

# Install artifact
cd $TRAVIS_BUILD_DIR/
mvn clean install -Dimagej.app.directory=$IJ_PATH -Ddelete.other.versions=true
