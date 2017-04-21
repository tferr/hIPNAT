#!/usr/bin/env sh
set -e

# Details of update site. NB: NEUROANAT_UPLOAD_PASS has been encrypted locally
# as per https://docs.travis-ci.com/user/environment-variables/
export UPDATE_SITE="Neuroanatomy"
export URL="http://sites.imagej.net/$UPDATE_SITE/"

# Paths:
export DEPLOY_PATH="$HOME/deploy"
export IJ_PATH="$DEPLOY_PATH/Fiji.app"
export IJ_LAUNCHER="$IJ_PATH/ImageJ-linux64"

# Install IJ
mkdir -p $IJ_PATH/
cd $DEPLOY_PATH
wget --no-check-certificate https://downloads.imagej.net/fiji/latest/fiji-linux64.zip
unzip fiji-linux64.zip

# Install artifact
cd $TRAVIS_BUILD_DIR/
mvn clean install -Dimagej.app.directory=$IJ_PATH -Ddelete.other.versions=true

# Deploy if release version
ARTIFACT=`find $IJ_PATH/plugins/ -type f -print | grep 'hIPNAT_'`
if [[ $ARTIFACT == *"SNAPSHOT"* ]]; then
    echo "SNAPSHOT release: skipping upload to $URL"
else
    echo "Uploading %ARTIFACT"
    echo "Setting $URL credentials"
    $IJ_LAUNCHER --update edit-update-site $UPDATE_SITE $URL "webdav:$UPDATE_SITE:$NEUROANAT_UPLOAD_PASS" .
    echo "Uploading to $URL..."
    $IJ_LAUNCHER --update upload --update-site $UPDATE_SITE --force-shadow plugins/hIPNAT_.jar
fi
